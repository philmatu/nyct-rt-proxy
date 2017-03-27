package com.kurtraschke.nyctrtproxy.services;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.MatchMetrics;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.kurtraschke.nyctrtproxy.util.NycRealtimeUtil.earliestTripStart;
import static com.kurtraschke.nyctrtproxy.util.NycRealtimeUtil.fixedStartDate;

public class TripUpdateProcessor {

  private static final Logger _log = LoggerFactory.getLogger(TripUpdateProcessor.class);

  private static final Map<Integer, Set<String>> routeBlacklistByFeed = ImmutableMap.of(1, ImmutableSet.of("D", "N", "Q"));

  private static final Set<String> routesNeedingFixup = ImmutableSet.of("SI", "N", "Q", "R", "W", "B", "D");

  private static final Map<Integer, Map<String, String>> realtimeToStaticRouteMapByFeed = ImmutableMap.of(1, ImmutableMap.of("S", "GS"));

  private int _latencyLimit = -1;

  private ProxyDataListener _listener;

  private TripMatcher _tripMatcher;

  // config
  @Inject(optional = true)
  public void setLatencyLimit(@Named("NYCT.latencyLimit") int limit) {
    _latencyLimit = limit;
  }


  @Inject(optional = true)
  public void setListener(ProxyDataListener listener) {
    _listener = listener;
  }

  @Inject
  public void setTripMatcher(TripMatcher tm) {
    _tripMatcher = tm;
  }

  public List<GtfsRealtime.TripUpdate> processFeed(Integer feedId, GtfsRealtime.FeedMessage fm) {

    MatchMetrics feedMetrics = new MatchMetrics();
    feedMetrics.reportLatency(fm.getHeader().getTimestamp());

    if (_latencyLimit > 0 && feedMetrics.getLatency() > _latencyLimit) {
      _log.info("Feed {} ignored, too high latency = {}", feedId, feedMetrics.getLatency());
      if (_listener != null)
        _listener.reportMatchesForFeed(feedId.toString(), feedMetrics);
      return Collections.emptyList();
    }

    final Map<String, String> realtimeToStaticRouteMap = realtimeToStaticRouteMapByFeed
            .getOrDefault(feedId, Collections.emptyMap());

    int nExpiredTus = 0;

    Multimap<String, GtfsRealtime.TripUpdate> tripUpdatesByRoute = ArrayListMultimap.create();
    for (GtfsRealtime.FeedEntity entity : fm.getEntityList()) {
      if (entity.hasTripUpdate()) {
        GtfsRealtime.TripUpdate tu = entity.getTripUpdate();
        if (expiredTripUpdate(tu, fm.getHeader().getTimestamp())) {
          nExpiredTus++;
        }
        else {
          String routeId = tu.getTrip().getRouteId();
          routeId = realtimeToStaticRouteMap.getOrDefault(routeId, routeId);
          tripUpdatesByRoute.put(routeId, tu);
        }
      }
    }

    List<GtfsRealtime.TripUpdate> ret = Lists.newArrayList();

    for (GtfsRealtimeNYCT.TripReplacementPeriod trp : fm.getHeader()
            .getExtension(GtfsRealtimeNYCT.nyctFeedHeader)
            .getTripReplacementPeriodList()) {
      if (routeBlacklistByFeed.getOrDefault(feedId, Collections.emptySet()).contains(trp.getRouteId()))
        continue;
      GtfsRealtime.TimeRange range = trp.getReplacementPeriod();

      Date start = range.hasStart() ? new Date(range.getStart() * 1000) : earliestTripStart(tripUpdatesByRoute.values());
      Date end = range.hasEnd() ? new Date(range.getEnd() * 1000) : new Date(fm.getHeader().getTimestamp() * 1000);

      Set<String> routeIds = Arrays.stream(trp.getRouteId().split(", ?"))
              .map(routeId -> realtimeToStaticRouteMap.getOrDefault(routeId, routeId))
              .collect(Collectors.toSet());

      _tripMatcher.initForFeed(start, end, routeIds);

      for (String routeId : routeIds) {

        MatchMetrics routeMetrics = new MatchMetrics();

        Multimap<String, TripMatchResult> matchesByTrip = ArrayListMultimap.create();
        Collection<GtfsRealtime.TripUpdate> tripUpdates = tripUpdatesByRoute.get(routeId);
        for (GtfsRealtime.TripUpdate tu : tripUpdates) {
          GtfsRealtime.TripUpdate.Builder tub = GtfsRealtime.TripUpdate.newBuilder(tu);
          GtfsRealtime.TripDescriptor.Builder tb = tub.getTripBuilder();

          tb.setRouteId(realtimeToStaticRouteMap
                  .getOrDefault(tb.getRouteId(), tb.getRouteId()));

          NyctTripId rtid = NyctTripId.buildFromString(tb.getTripId());

          if (routesNeedingFixup.contains(tb.getRouteId()) && rtid != null) {
            tb.setStartDate(fixedStartDate(tb));

            tub.getStopTimeUpdateBuilderList().forEach(stub -> {
              stub.setStopId(stub.getStopId() + rtid.getDirection());
            });

            tb.setTripId(rtid.toString());
          }

          TripMatchResult result = _tripMatcher.match(tub, rtid, fm.getHeader().getTimestamp());
          result.setTripUpdate(tub);
          String tripId = result.hasResult() ? result.getResult().getTrip().getId().getId() : tb.getTripId();
          matchesByTrip.put(tripId, result);
        }

        for (Collection<TripMatchResult> matches : matchesByTrip.asMap().values())
          tryMergeResult(matches);

        for (TripMatchResult result : matchesByTrip.values()) {
          if (!result.getStatus().equals(TripMatchResult.Status.MERGED)) {
            if (_tripMatcher instanceof LazyTripMatcher && result.hasResult() && !((LazyTripMatcher) _tripMatcher).tripMatch(result)) {
              _log.info("no stop match rt={} static={}", result.getTripUpdate().getTrip().getTripId(), result.getResult().getTrip().getId().getId());
              result.setStatus(TripMatchResult.Status.NO_MATCH);
              result.setResult(null);
            }
            GtfsRealtime.TripUpdate.Builder tub = result.getTripUpdate();
            GtfsRealtime.TripDescriptor.Builder tb = tub.getTripBuilder();
            if (result.getResult() != null) {
              ActivatedTrip at = result.getResult();
              String staticTripId = at.getTrip().getId().getId();
              tb.setTripId(staticTripId);
              removeTimepoints(at, tub);
            } else {
              _log.info("unmatched: {} due to {}", tub.getTrip().getTripId(), result.getStatus());
              tb.setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED);
            }
            ret.add(tub.build());
          }

          routeMetrics.add(result);
          feedMetrics.add(result);
        }

        if (_listener != null)
          _listener.reportMatchesForRoute(routeId, routeMetrics);
      }
    }

    if (_listener != null)
      _listener.reportMatchesForFeed(feedId.toString(), feedMetrics);

    _log.info("feed={}, expired TUs={}", feedId, nExpiredTus);
    return ret;
  }

  private static boolean expiredTripUpdate(GtfsRealtime.TripUpdate tu, long timestamp) {
    OptionalLong latestTime = tu.getStopTimeUpdateList()
            .stream()
            .map(stu -> stu.hasDeparture() ? stu.getDeparture() : stu.getArrival())
            .filter(GtfsRealtime.TripUpdate.StopTimeEvent::hasTime)
            .mapToLong(GtfsRealtime.TripUpdate.StopTimeEvent::getTime).max();
    return latestTime.isPresent() && latestTime.getAsLong() < timestamp - 300;
  }

  private void removeTimepoints(ActivatedTrip trip, GtfsRealtime.TripUpdate.Builder tripUpdate) {
    Set<String> stopIds = trip.getStopTimes().stream()
            .map(s -> s.getStop().getId().getId()).collect(Collectors.toSet());
    for(int i = 0; i < tripUpdate.getStopTimeUpdateCount(); i++) {
      String id = tripUpdate.getStopTimeUpdate(i).getStopId();
      if (!stopIds.contains(id)) {
        tripUpdate.removeStopTimeUpdate(i);
        i--;
      }
    }
  }


  private void tryMergeResult(Collection<TripMatchResult> col) {
    if (col.size() != 2)
      return;
    Iterator<TripMatchResult> iter = col.iterator();
    mergedResult(iter.next(), iter.next());
  }

  private TripMatchResult mergedResult(TripMatchResult first, TripMatchResult second) {
    NyctTripId firstId = NyctTripId.buildFromString(first.getTripUpdate().getTrip().getTripId());
    NyctTripId secondId = NyctTripId.buildFromString(second.getTripUpdate().getTrip().getTripId());
    if (firstId.getOriginDepartureTime() > secondId.getOriginDepartureTime())
      return mergedResult(second, first);

    String midpt0 = getReliefPoint(first.getTripUpdate(), 1);
    String midpt1 = getReliefPoint(second.getTripUpdate(), 0);
    if (midpt0 != null && midpt0.equals(midpt1)) {
      Iterator<StopTimeUpdate.Builder> stusToAdd = second.getTripUpdate().getStopTimeUpdateBuilderList().iterator();
      GtfsRealtime.TripUpdate.Builder update = first.getTripUpdate();
      StopTimeUpdate.Builder stu1 = stusToAdd.next();
      StopTimeUpdate.Builder stu0 = update.getStopTimeUpdateBuilder(update.getStopTimeUpdateCount() - 1);
      if (stu1.getStopId().equals(stu0.getStopId())) {
        stu0.setDeparture(stu1.getDeparture());
        while (stusToAdd.hasNext()) {
          update.addStopTimeUpdate(stusToAdd.next());
        }
        second.setStatus(TripMatchResult.Status.MERGED);
        return first;
      }
    }

    return null;
  }

  private static String getReliefPoint(GtfsRealtime.TripUpdateOrBuilder update, int pt) {
    String trainId = update.getTrip().getExtension(GtfsRealtimeNYCT.nyctTripDescriptor).getTrainId();
    String[] tokens = trainId.split(" ");
    String relief = tokens[tokens.length - 1];
    String[] points = relief.split("/");
    return points[pt];
  }
}
