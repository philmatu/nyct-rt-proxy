package com.kurtraschke.nyctrtproxy.tests;

import com.google.common.collect.ImmutableMap;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.services.ActivatedTripMatcher;
import org.junit.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

public class SanityTest extends RtTestRunner {

  private static final Logger _log = LoggerFactory.getLogger(SanityTest.class);

  @Test
  public void test1_2017_03_13() throws Exception {
    test(1, "1_2017-03-13.pb", 188, 24, 28);
  }

  @Test
  public void test2_2017_03_13() throws Exception {
    test(2, "2_2017-03-13.pb", 28, 0, 0);
  }

  @Test
  public void test11_2017_03_13() throws Exception {
    test(11, "11_2017-03-13.pb", 8, 0, 0);
  }

  @Test
  public void test16_2017_03_13() throws Exception {
    test(16, "16_2017-03-13.pb", 58, 42, 44);
  }

  @Test
  public void test21_2017_03_13() throws Exception {
    test(21, "21_2017-03-13.pb", 29, 24, 25);
  }

  private void test(int feedId, String protobuf, int nScheduledExpected, int nCancelledExpected, int nAddedExpected) throws Exception {
    FeedMessage msg = readFeedMessage(protobuf);
    List<TripUpdate> updates = _proxyProvider.processFeed(feedId, msg);

    int nScheduled = 0, nCancelled = 0, nAdded = 0, nStatic = 0, nRt = 0;

    for (TripUpdate tripUpdate : updates) {
      switch(tripUpdate.getTrip().getScheduleRelationship()) {
        case SCHEDULED:
          checkScheduledTrip(tripUpdate);
          nScheduled++;
          nRt++;
          if (tripInRange(tripUpdate, msg) && tripOnActiveServiceDay(tripUpdate, msg.getHeader()))
            nStatic++;
          break;
        case CANCELED:
          checkCanceledTrip(tripUpdate);
          nCancelled++;
          nStatic++;
          break;
        case ADDED:
          checkAddedTrip(tripUpdate);
          nAdded++;
          nRt++;
          break;
        default:
          throw new Exception("unexpected schedule relationship");
      }
    }

    _log.info("nScheduled={}, nCancelled={}, nAdded={}", nScheduled, nCancelled, nAdded);
    // make sure we have improved or stayed the same
    assertTrue(nScheduled >= nScheduledExpected);
    assertTrue(nCancelled <= nCancelledExpected);
    assertTrue(nAdded <= nAddedExpected);

    // if improved:
    if (nScheduled != nScheduledExpected || nCancelled != nCancelledExpected || nAdded != nAddedExpected) {
      _log.info("Better than expected, could update test.");
      assertEquals("total num of RT trips changed",  nScheduledExpected + nAddedExpected, nRt);
      //assertEquals("total num of static trips changed", nScheduledExpected + nCancelledExpected, nStatic);
    }
  }

  private void checkScheduledTrip(TripUpdate tripUpdate) {
    Trip trip = getTrip(tripUpdate);
    assertNotNull(trip);
    assertEquals(tripUpdate.getTrip().getRouteId(), trip.getRoute().getId().getId());

    // skip stop test for now.
//    List<TripUpdate.StopTimeUpdate> stus = tripUpdate.getStopTimeUpdateList();
//
//    Set<String> stopIds = _dao.getStopTimesForTrip(trip)
//            .stream()
//            .map(st -> st.getStop().getId().getId())
//            .collect(Collectors.toSet());
//
//    for (TripUpdate.StopTimeUpdate stu : stus) {
//      assertTrue(stopIds.contains(stu.getStopId()));
//    }

  }

  private void checkCanceledTrip(TripUpdate tripUpdate) {
    Trip trip = getTrip(tripUpdate);
    assertNotNull(trip);
    assertEquals(tripUpdate.getTrip().getRouteId(), trip.getRoute().getId().getId());
  }

  private void checkAddedTrip(TripUpdate tripUpdate) {
    Trip trip = getTrip(tripUpdate);
    assertNull(trip);
  }

  private static final Map<String, String> staticToRealtimeRouteMap = ImmutableMap.of("GS", "S");
  private static String mapRoutetoRt(String route) {
    return staticToRealtimeRouteMap.getOrDefault(route, route);
  }

  private boolean tripInRange(GtfsRealtime.TripUpdate tu, GtfsRealtime.FeedMessage msg) {
    long time = msg.getHeader().getTimestamp();

    List<GtfsRealtimeNYCT.TripReplacementPeriod> trps = msg.getHeader().getExtension(GtfsRealtimeNYCT.nyctFeedHeader).getTripReplacementPeriodList();
    String routeId = tu.getTrip().getRouteId();
    GtfsRealtimeNYCT.TripReplacementPeriod trp = trps.stream()
            .filter(t -> Arrays.asList(t.getRouteId().split(",")).contains(mapRoutetoRt(routeId)))
            .findFirst().get();

    TimeRange tr = trp.getReplacementPeriod();
    long trStart = tr.hasStart() ? tr.getStart() : time;

    Trip trip = getTrip(tu);
    List<StopTime> stopTimes = _dao.getStopTimesForTrip(trip);
    ServiceDate sd = new ServiceDate(new Date(time * 1000));
    long date = sd.getAsDate().getTime()/1000;
    long tripstart = date + stopTimes.get(0).getDepartureTime();
    long tripend = date + stopTimes.get(stopTimes.size()-1).getArrivalTime();
    return tripstart < time && tripend > trStart;
  }

  private boolean tripOnActiveServiceDay(GtfsRealtime.TripUpdate tu, GtfsRealtime.FeedHeader header) {
    ServiceDate sd = new ServiceDate(new Date(header.getTimestamp() * 1000));
    Set<AgencyAndId> sids = _csd.getServiceIdsForDate(sd);
    Trip trip = getTrip(tu);
    return sids.contains(trip.getServiceId());
  }

}
