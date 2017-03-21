package com.kurtraschke.nyctrtproxy.services;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LazyTripMatcher implements TripMatcher {

  private static final int LATE_TRIP_LIMIT_SEC = 3600; // 1 hour
  private GtfsRelationalDao _dao;
  private CalendarServiceData _csd;
  private boolean _looseMatchDisabled = false;

  private static final Logger _log = LoggerFactory.getLogger(LazyTripMatcher.class);

  @Inject
  public void setGtfsRelationalDao(GtfsRelationalDao dao) {
    _dao = dao;
  }

  @Inject
  public void setCalendarServiceData(CalendarServiceData csd) {
    _csd = csd;
  }

  public void setLooseMatchDisabled(boolean looseMatchDisabled) {
    _looseMatchDisabled = looseMatchDisabled;
  }

  @Override
  public TripMatchResult match(GtfsRealtime.TripUpdateOrBuilder tu, NyctTripId id, long timestamp) {
    // why did we not find a trip:
    TripMatchResult.Status notFoundStatus = TripMatchResult.Status.NO_TRIP_WITH_START_DATE;

    Set<TripMatchResult> candidates = Sets.newHashSet();
    ServiceDate sd = new ServiceDate(new Date(timestamp * 1000));
    Set<AgencyAndId> serviceIds = _csd.getServiceIdsForDate(sd);
    Route r = _dao.getRouteForId(new AgencyAndId("MTA NYCT", tu.getTrip().getRouteId()));
    for (Trip trip : _dao.getTripsForRoute(r)) {
      NyctTripId atid = NyctTripId.buildFromString(trip.getId().getId());
      if (!atid.routeDirMatch(id))
        continue;
      notFoundStatus = TripMatchResult.Status.NO_MATCH; // there IS a trip with start date
      List<StopTime> stopTimes = _dao.getStopTimesForTrip(trip);
      int start = stopTimes.get(0).getDepartureTime(); // in sec into day.
      int end = stopTimes.get(stopTimes.size()-1).getArrivalTime();
      boolean onServiceDay = serviceIds.contains(trip.getServiceId());
      if (atid.strictMatch(id) && onServiceDay) {
        return new TripMatchResult(new ActivatedTrip(sd, trip, start, end, stopTimes));
      }
      // loose match, RT trip could be late relative to static trip
      int delta = (int) (((double) id.getOriginDepartureTime())*0.6 - start);
      if (!_looseMatchDisabled && delta >= 0 && delta < LATE_TRIP_LIMIT_SEC) {
        List<GtfsRealtime.TripUpdate.StopTimeUpdate> stus = tu.getStopTimeUpdateList();
        if (tripMatch(trip, stus, stopTimes)) {
          ActivatedTrip at = new ActivatedTrip(sd, trip, start, end, stopTimes);
          TripMatchResult result = TripMatchResult.looseMatch(at, delta, onServiceDay);
          // disable trips that are coerced AND on a different day
          if (onServiceDay || delta == 0)
            candidates.add(result);
        }
      }
    }
    if (candidates.isEmpty())
      return new TripMatchResult(notFoundStatus);
    else
      return Collections.max(candidates);
  }

  @Override
  public void initForFeed(Multimap<String, ActivatedTrip> map) {
    // do nothing
  }

  private boolean tripMatch(Trip trip, List<StopTimeUpdate> stusWithTimepoints, List<StopTime> stopTimes) {
    List<String> stops = stopTimes.stream().map(s -> s.getStop().getId().getId()).collect(Collectors.toList());
    List<StopTimeUpdate> stus = stusWithTimepoints.stream()
            .filter(stu -> isStopIdValid(stu.getStopId())).collect(Collectors.toList());

    // Find longest sequence of stus that matches stops
    List<List<StopTimeUpdate>> sequences = Lists.newArrayList();

    int gtfsIdx = 0;

    Iterator<StopTimeUpdate> stuIter = stus.iterator();
    while (stuIter.hasNext()) {
      StopTimeUpdate stu = stuIter.next();
      while (gtfsIdx < stops.size() && !stops.get(gtfsIdx).equals(stu.getStopId()))
        gtfsIdx++;

      List<StopTimeUpdate> seq = Lists.newArrayList();

      while (gtfsIdx < stops.size()) {
        if (stu.getStopId().equals(stops.get(gtfsIdx))) {
          seq.add(stu);
          gtfsIdx++;
        } else {
          break;
        }
        if (stuIter.hasNext())
          stu = stuIter.next();
        else
          break;
      }
      sequences.add(seq);
    }

    // there's a match if the last-added sequence ends with the last stop TODO longest? last added?

    if (sequences.isEmpty())
      return false;

    if (sequences.size() > 1) {
      Set<String> extras = Sets.newHashSet();
      for (int i = 0; i < sequences.size() - 1; i++)
        for (StopTimeUpdate stu : sequences.get(i))
          extras.add(stu.getStopId());
      _log.warn("For trip {} extra stops {}", trip, extras);
    }

    // last stop ID is last STU: collections are exhausted at the same time
    return !stuIter.hasNext() && gtfsIdx == stops.size();
  }

  private boolean isStopIdValid(String id) {
    return _dao.getStopForId(new AgencyAndId("MTA NYCT", id)) != null;
  }
}
