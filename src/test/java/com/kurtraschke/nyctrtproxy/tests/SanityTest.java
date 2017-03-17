package com.kurtraschke.nyctrtproxy.tests;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.ExtensionRegistry;
import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.ProxyProvider;
import com.kurtraschke.nyctrtproxy.services.CloudwatchProxyDataListener;
import com.kurtraschke.nyctrtproxy.services.LazyTripMatcher;
import com.kurtraschke.nyctrtproxy.services.TripActivator;
import junit.framework.TestCase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.calendar.CalendarServiceDataFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    // to enable logging
    CloudwatchProxyDataListener listener = new CloudwatchProxyDataListener();
    listener.init();
    _proxyProvider.setListener(listener);
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
      // static trips could change if we have a trip getting matched that has ended according to schedule
    }
  }

  private void checkScheduledTrip(TripUpdate tripUpdate) {
    Trip trip = getTrip(tripUpdate);
    assertNotNull(trip);
    assertEquals(tripUpdate.getTrip().getRouteId(), trip.getRoute().getId().getId());

    List<TripUpdate.StopTimeUpdate> stus = tripUpdate.getStopTimeUpdateList();

    List<String> stopTimes = _dao.getStopTimesForTrip(trip).stream()
              .map(s -> s.getStop().getId().getId()).collect(Collectors.toList());

      String firstStopId = stus.get(0).getStopId();
      int gtfsIdx = 0;
      while(!stopTimes.get(gtfsIdx).equals(firstStopId))
        gtfsIdx++;

      // we should contain stop updates from the current stop to the end.
      for (TripUpdate.StopTimeUpdate stu : stus) {
        assertTrue(stopTimes.get(gtfsIdx).equals(stu.getStopId()));
        gtfsIdx++;
    }

    if (gtfsIdx != stopTimes.size())
      System.out.println("s");
    assertEquals(gtfsIdx, stopTimes.size());

//    List<TripUpdate.StopTimeUpdate> subseq = longestSequence(stus, stopTimes);
//
//    //assertEquals(subseq.get(subseq.size()-1).getStopId(), stopTimes.get(stopTimes.size() - 1));
//
//    if (subseq.size() != stus.size()) {
//      _log.warn("Weird number of STUs. Extras are:");
//      for (TripUpdate.StopTimeUpdate stu : stus) {
//        if (!subseq.contains(stu))
//          _log.warn(stu.toString());
//      }
//    }
  }

  // return the longest sequence of TripUpdates that is continuous with stops
  private List<TripUpdate.StopTimeUpdate> longestSequence(List<TripUpdate.StopTimeUpdate> updates, List<String> stops) {
    Collection<List<TripUpdate.StopTimeUpdate>> possibilities = Sets.newHashSet();
    Iterator<TripUpdate.StopTimeUpdate> iter = updates.iterator();
    int gtfsIdx = 0;
    while (iter.hasNext()) {
      TripUpdate.StopTimeUpdate stu = iter.next();
      while (!stops.get(gtfsIdx).equals(stu.getStopId()))
        gtfsIdx++;

      List<TripUpdate.StopTimeUpdate> stus = Lists.newArrayList();
      while (stu != null && stops.get(gtfsIdx).equals(stu.getStopId())) {
        stus.add(stu);
        stu = null;
        if (iter.hasNext()) {
          stu = iter.next();
          gtfsIdx++;
        }
      }
      possibilities.add(stus);
    }
    return Collections.max(possibilities, (x, y) -> x.size() - y.size());
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
