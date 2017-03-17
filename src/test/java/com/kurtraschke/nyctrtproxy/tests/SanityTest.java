package com.kurtraschke.nyctrtproxy.tests;

import com.google.protobuf.ExtensionRegistry;
import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.ProxyProvider;
import com.kurtraschke.nyctrtproxy.services.ActivatedTripMatcher;
import com.kurtraschke.nyctrtproxy.services.LazyTripMatcher;
import com.kurtraschke.nyctrtproxy.services.TripActivator;
import com.kurtraschke.nyctrtproxy.services.TripMatcher;
import junit.framework.TestCase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.calendar.CalendarServiceDataFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

public class SanityTest {

  private static final Logger _log = LoggerFactory.getLogger(SanityTest.class);

  private static ProxyProvider _proxyProvider;
  private static ExtensionRegistry _extensionRegistry;
  private static GtfsRelationalDaoImpl _dao;
  private static String _agencyId = "MTA NYCT";

  @BeforeClass
  public static void beforeClass() {
    _dao = new GtfsRelationalDaoImpl();
    GtfsReader reader = new GtfsReader();
    reader.setEntityStore(_dao);
    try {
      File file = new File(TestCase.class.getResource("/google_transit.zip").getFile());
      reader.setInputLocation(file);
      reader.run();
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException("Failure while reading GTFS", e);
    }

    CalendarServiceDataFactory csdf = new CalendarServiceDataFactoryImpl(_dao);
    CalendarServiceData csd = csdf.createData();

    TripActivator ta = new TripActivator();
    ta.setCalendarServiceData(csd);
    ta.setGtfsRelationalDao(_dao);
    ta.start();

    LazyTripMatcher tm = new LazyTripMatcher();
    tm.setGtfsRelationalDao(_dao);
    tm.setCalendarServiceData(csd);

    _proxyProvider = new ProxyProvider();
    _proxyProvider.setTripActivator(ta);
    _proxyProvider.setTripMatcher(tm);

    _extensionRegistry = ExtensionRegistry.newInstance();
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctFeedHeader);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctTripDescriptor);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctStopTimeUpdate);
  }

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
    InputStream stream = this.getClass().getResourceAsStream("/" + protobuf);
    FeedMessage msg = FeedMessage.parseFrom(stream, _extensionRegistry);
    List<TripUpdate> updates = _proxyProvider.processFeed(feedId, msg);

    int nScheduled = 0, nCancelled = 0, nAdded = 0;

    for (TripUpdate tripUpdate : updates) {
      switch(tripUpdate.getTrip().getScheduleRelationship()) {
        case SCHEDULED:
          checkScheduledTrip(tripUpdate);
          nScheduled++;
          break;
        case CANCELED:
          checkCanceledTrip(tripUpdate);
          nCancelled++;
          break;
        case ADDED:
          checkAddedTrip(tripUpdate);
          nAdded++;
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
      assertEquals("total num of RT trips changed", nScheduled + nAdded, nScheduledExpected + nAddedExpected);
      assertEquals("total num of static trips changed", nScheduled + nCancelled, nScheduledExpected + nCancelledExpected);
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

  private Trip getTrip(TripUpdate tu) {
    String tid = tu.getTrip().getTripId();
    return _dao.getTripForId(new AgencyAndId(_agencyId, tid));
  }

}
