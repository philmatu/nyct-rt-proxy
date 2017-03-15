package com.kurtraschke.nyctrtproxy.tests;

import com.google.protobuf.ExtensionRegistry;
import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.ProxyProvider;
import com.kurtraschke.nyctrtproxy.services.TripActivator;
import junit.framework.TestCase;
import org.junit.Before;
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
import java.util.Set;
import java.util.stream.Collectors;

public class SanityTest extends TestCase {

  private static final Logger _log = LoggerFactory.getLogger(SanityTest.class);

  private ProxyProvider _proxyProvider;
  private ExtensionRegistry _extensionRegistry;
  private GtfsRelationalDaoImpl _dao;
  private String _agencyId = "MTA NYCT";

  @Before
  public void setUp() {
    _dao = new GtfsRelationalDaoImpl();
    GtfsReader reader = new GtfsReader();
    reader.setEntityStore(_dao);
    try {
      File file = new File(this.getClass().getResource("/google_transit.zip").getFile());
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

    _proxyProvider = new ProxyProvider();
    _proxyProvider.setTripActivator(ta);

    _extensionRegistry = ExtensionRegistry.newInstance();
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctFeedHeader);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctTripDescriptor);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctStopTimeUpdate);
  }

  @Test
  public void testFeed1() throws Exception {
    test(1, "1.pb");
  }

  @Test
  public void testFeed2() throws Exception {
    test(2, "2.pb");
  }

  @Test
  public void testFeed11() throws Exception {
    test(11, "11.pb");
  }

  @Test
  public void testFeed16() throws Exception {
    test(16, "16.pb");
  }

  @Test
  public void testFeed21() throws Exception {
    test(21, "21.pb");
  }

  private void test(int feedId, String protobuf) throws Exception {
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
          throw new Exception("invalid schedule relationship");
      }
    }

    _log.info("nScheduled={}, nCancelled={}, nAdded={}", nScheduled, nCancelled, nAdded);
    assertTrue(nScheduled > 0);
  }

  private void checkScheduledTrip(TripUpdate tripUpdate) {
    Trip trip = getTrip(tripUpdate);
    assertNotNull(trip);
    assertEquals(tripUpdate.getTrip().getRouteId(), trip.getRoute().getId().getId());

    List<TripUpdate.StopTimeUpdate> stus = tripUpdate.getStopTimeUpdateList();

    Set<String> stopIds = _dao.getStopTimesForTrip(trip)
            .stream()
            .map(st -> st.getStop().getId().getId())
            .collect(Collectors.toSet());

    for (TripUpdate.StopTimeUpdate stu : stus) {
      //assertTrue(stopIds.contains(stu.getStopId()));
    }

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
