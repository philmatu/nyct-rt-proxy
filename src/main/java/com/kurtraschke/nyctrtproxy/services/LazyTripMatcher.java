package com.kurtraschke.nyctrtproxy.services;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsRelationalDao;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LazyTripMatcher implements TripMatcher {
  private GtfsRelationalDao _dao;
  private CalendarServiceData _csd;

  @Inject
  public void setGtfsRelationalDao(GtfsRelationalDao dao) {
    _dao = dao;
  }

  @Inject
  public void setCalendarServiceData(CalendarServiceData csd) {
    _csd = csd;
  }

  @Override
  public Optional<ActivatedTrip> match(GtfsRealtime.TripUpdateOrBuilder tu, NyctTripId id, long timestamp) {
    Set<ActivatedTrip> candidates = Sets.newHashSet();
    ServiceDate sd = new ServiceDate(new Date(timestamp * 1000));
    Route r = _dao.getRouteForId(new AgencyAndId("MTA NYCT", tu.getTrip().getRouteId()));
    for (Trip trip : _dao.getTripsForRoute(r)) {
      NyctTripId atid = NyctTripId.buildFromString(trip.getId().getId());
      if (!atid.looseMatch(id))
        continue;
      List<StopTime> stopTimes = _dao.getStopTimesForTrip(trip);
      int start = stopTimes.get(0).getDepartureTime(); // in sec into day.
      int end = stopTimes.get(stopTimes.size()-1).getArrivalTime();
      if (atid.strictMatch(id)) {
        candidates.add(new ActivatedTrip(sd, trip, start, end, stopTimes));
      }
      else if (((double) start)/3600d == ((double)id.getOriginDepartureTime()/6000d)) {
        List<GtfsRealtime.TripUpdate.StopTimeUpdate> stus = tu.getStopTimeUpdateList();
        if (tripMatch(stus, stopTimes))
          candidates.add(new ActivatedTrip(sd, trip, start, end, stopTimes));
      }
    }
    Set<AgencyAndId> sids = _csd.getServiceIdsForDate(sd);
    for (ActivatedTrip trip : candidates) {
      if (sids.contains(trip.getTrip().getServiceId())) {
        trip.setSidFlag(true);
        return Optional.of(trip);
      }
    }
    return candidates.stream().findFirst();
  }

  @Override
  public void initForFeed(Multimap<String, ActivatedTrip> map) {
    // do nothing
  }

  private boolean tripMatch(List<GtfsRealtime.TripUpdate.StopTimeUpdate> stusWithTimepoints, List<StopTime> stopTimes) {
    List<String> stops = stopTimes.stream().map(s -> s.getStop().getId().getId()).collect(Collectors.toList());
    List<GtfsRealtime.TripUpdate.StopTimeUpdate> stus = stusWithTimepoints.stream()
            .filter(stu -> isStopIdValid(stu.getStopId())).collect(Collectors.toList());
    int gtfsIdx = 0;
    while(gtfsIdx < stops.size() && !stops.get(gtfsIdx).equals(stus.get(0).getStopId()))
      gtfsIdx++;
    Iterator<GtfsRealtime.TripUpdate.StopTimeUpdate> iter = stus.iterator();
    while (iter.hasNext() && gtfsIdx < stops.size()) {
      GtfsRealtime.TripUpdate.StopTimeUpdate stu = iter.next();
      if (stu.getStopId().equals(stops.get(gtfsIdx)))
        gtfsIdx++;
    }
    // there's a match if we have exhausted both lists at the same time
    return !iter.hasNext() && gtfsIdx == stops.size();
  }

  private boolean isStopIdValid(String id) {
    return _dao.getStopForId(new AgencyAndId("MTA NYCT", id)) != null;
  }
}
