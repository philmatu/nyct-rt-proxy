/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.kurtraschke.nyctrtproxy.model.MatchMetrics;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;
import com.kurtraschke.nyctrtproxy.services.ProxyDataListener;
import com.kurtraschke.nyctrtproxy.services.TripMatcher;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFullUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ExtensionRegistry;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;

import static com.kurtraschke.nyctrtproxy.util.NycRealtimeUtil.*;

/**
 *
 * @author kurt
 */
public class ProxyProvider {

  private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ProxyProvider.class);

  private static final ExtensionRegistry _extensionRegistry;

  private GtfsRealtimeSink _tripUpdatesSink;

  private String _key;

  private HttpClientConnectionManager _connectionManager;

  private CloseableHttpClient _httpClient;

  private ScheduledExecutorService _scheduledExecutorService;

  private ScheduledFuture _updater;

  private ProxyDataListener _listener;

  private TripMatcher _tripMatcher;

  private static final Map<Integer, Set<String>> routeBlacklistByFeed = ImmutableMap.of(1, ImmutableSet.of("D", "N", "Q"));

  private static final Set<String> routesUsingAlternateIdFormat = ImmutableSet.of("SI", "L", "N", "Q", "R", "W", "B", "D");

  private static final Set<String> routesNeedingFixup = ImmutableSet.of("SI", "N", "Q", "R", "W", "B", "D");

  private static final Map<Integer, Map<String, String>> realtimeToStaticRouteMapByFeed = ImmutableMap.of(1, ImmutableMap.of("S", "GS"));

  static {
    _extensionRegistry = ExtensionRegistry.newInstance();
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctFeedHeader);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctTripDescriptor);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctStopTimeUpdate);
  }

  @Inject
  public void setTripUpdatesSink(@TripUpdates GtfsRealtimeSink tripUpdatesSink) {
    _tripUpdatesSink = tripUpdatesSink;
  }

  @Inject
  public void setHttpClientConnectionManager(HttpClientConnectionManager connectionManager) {
    _connectionManager = connectionManager;
  }

  @Inject
  public void setScheduledExecutorService(ScheduledExecutorService service) {
    _scheduledExecutorService = service;
  }

  @Inject
  public void setKey(@Named("NYCT.key") String key) {
    _key = key;
  }

  @Inject(optional = true)
  public void setListener(ProxyDataListener listener) {
    _listener = listener;
  }

  @Inject
  public void setTripMatcher(TripMatcher tm) {
    _tripMatcher = tm;
  }

  @PostConstruct
  public void start() {
    _httpClient = HttpClientBuilder.create().setConnectionManager(_connectionManager).build();
    _updater = _scheduledExecutorService.scheduleWithFixedDelay(this::update, 0, 60, TimeUnit.SECONDS);

  }

  @PreDestroy
  public void stop() {
    _updater.cancel(false);
    _scheduledExecutorService.shutdown();
    _connectionManager.shutdown();
  }

  public void update() {
    _log.info("doing update");

    GtfsRealtimeFullUpdate grfu = new GtfsRealtimeFullUpdate();

    List<TripUpdate> tripUpdates = Lists.newArrayList();

    for (int feedId : Arrays.asList(1, 2, 11, 16, 21)) { // 1, 2, 11, 16, 21
      URI feedUrl;

      try {
        URIBuilder ub = new URIBuilder("http://datamine.mta.info/mta_esi.php");

        ub.addParameter("key", _key);
        ub.addParameter("feed_id", Integer.toString(feedId));

        feedUrl = ub.build();
      } catch (URISyntaxException ex) {
        throw new RuntimeException(ex);
      }

      HttpGet get = new HttpGet(feedUrl);

      try {
        CloseableHttpResponse response = _httpClient.execute(get);
        try (InputStream streamContent = response.getEntity().getContent()) {
          tripUpdates.addAll(processFeed(feedId, FeedMessage.parseFrom(streamContent, _extensionRegistry)));
        }
      } catch (Exception ex) {
        _log.warn("Exception while fetching source feed " + feedUrl, ex);
      }
    }

    for (TripUpdate tu : tripUpdates) {
      FeedEntity.Builder feb = FeedEntity.newBuilder();
      feb.setTripUpdate(tu);
      feb.setId(tu.getTrip().getTripId());
      grfu.addEntity(feb.build());
    }

    _tripUpdatesSink.handleFullUpdate(grfu);
  }

  public List<TripUpdate> processFeed(Integer feedId, FeedMessage fm) {

    final Map<String, String> realtimeToStaticRouteMap = realtimeToStaticRouteMapByFeed
            .getOrDefault(feedId, Collections.emptyMap());

    int nExpiredTus = 0;

    Multimap<String, TripUpdate> tripUpdatesByRoute = ArrayListMultimap.create();
    for (FeedEntity entity : fm.getEntityList()) {
      if (entity.hasTripUpdate()) {
        TripUpdate tu = entity.getTripUpdate();
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

    List<TripUpdate> ret = Lists.newArrayList();

    MatchMetrics feedMetrics = new MatchMetrics();
    feedMetrics.reportLatency(fm.getHeader().getTimestamp());
    long nSkippedCancel = 0;

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

        Collection<TripUpdate> tripUpdates = tripUpdatesByRoute.get(routeId);
        for (TripUpdate tu : tripUpdates) {
          TripUpdate.Builder tub = TripUpdate.newBuilder(tu);
          TripDescriptor.Builder tb = tub.getTripBuilder();

          tb.setRouteId(realtimeToStaticRouteMap
                  .getOrDefault(tb.getRouteId(), tb.getRouteId()));

          NyctTripId rtid = NyctTripId.buildFromString(tb.getTripId());

          if (routesNeedingFixup.contains(tb.getRouteId())) {
            tb.setStartDate(fixedStartDate(tb));

            tub.getStopTimeUpdateBuilderList().forEach(stub -> {
              stub.setStopId(stub.getStopId() + rtid.getDirection());
            });

            tb.setTripId(rtid.toString());
          }

          TripMatchResult result = _tripMatcher.match(tub, rtid, fm.getHeader().getTimestamp());
          routeMetrics.add(result);
          feedMetrics.add(result);

          if (result.getResult() != null) {
            ActivatedTrip at = result.getResult();
            String staticTripId = at.getTrip().getId().getId();
            tb.setTripId(staticTripId);
            removeTimepoints(at, tub);
          } else {
            _log.info("unmatched: {}", tub.getTrip().getTripId());
            tb.setScheduleRelationship(ScheduleRelationship.ADDED);
          }
          ret.add(tub.build());
        }

        if (_listener != null)
          _listener.reportMatchesForRoute(routeId, routeMetrics);
      }
    }

    if (_listener != null)
      _listener.reportMatchesForFeed(feedId.toString(), feedMetrics);

    _log.info("feed={}, skipped cancel={}, expired TUs={}", feedId, nSkippedCancel, nExpiredTus);
    return ret;
  }

  private static boolean expiredTripUpdate(TripUpdate tu, long timestamp) {
    OptionalLong latestTime = tu.getStopTimeUpdateList()
            .stream()
            .map(stu -> stu.hasDeparture() ? stu.getDeparture() : stu.getArrival())
            .filter(TripUpdate.StopTimeEvent::hasTime)
            .mapToLong(TripUpdate.StopTimeEvent::getTime).max();
    return latestTime.isPresent() && latestTime.getAsLong() < timestamp - 300;
  }

  private void removeTimepoints(ActivatedTrip trip, TripUpdate.Builder tripUpdate) {
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
}
