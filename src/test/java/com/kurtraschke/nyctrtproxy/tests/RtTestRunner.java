package com.kurtraschke.nyctrtproxy.tests;

import com.google.protobuf.ExtensionRegistry;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.services.CloudwatchProxyDataListener;
import com.kurtraschke.nyctrtproxy.services.LazyTripMatcher;
import com.kurtraschke.nyctrtproxy.services.TripActivator;
import com.kurtraschke.nyctrtproxy.services.TripUpdateProcessor;
import junit.framework.TestCase;
import org.junit.BeforeClass;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.calendar.CalendarServiceDataFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public abstract class RtTestRunner {

  protected static TripUpdateProcessor _processor;
  protected static CalendarServiceData _csd;
  protected static ExtensionRegistry _extensionRegistry;
  protected static GtfsRelationalDaoImpl _dao;
  protected static String _agencyId = "MTA NYCT";
  protected static LazyTripMatcher _tm;
  protected static TripActivator _ta;

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
    _csd = csdf.createData();

    _ta = new TripActivator();
    _ta.setCalendarServiceData(_csd);
    _ta.setGtfsRelationalDao(_dao);
    _ta.start();

    _tm = new LazyTripMatcher();
    _tm.setGtfsRelationalDao(_dao);
    _tm.setCalendarServiceData(_csd);

    _processor = new TripUpdateProcessor();
    _processor.setTripMatcher(_tm);
    CloudwatchProxyDataListener listener = new CloudwatchProxyDataListener();
    listener.init();
    _processor.setListener(listener);

    _extensionRegistry = ExtensionRegistry.newInstance();
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctFeedHeader);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctTripDescriptor);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctStopTimeUpdate);
  }

  public GtfsRealtime.FeedMessage readFeedMessage(String file) throws IOException {
    InputStream stream = this.getClass().getResourceAsStream("/" + file);
    GtfsRealtime.FeedMessage msg = GtfsRealtime.FeedMessage.parseFrom(stream, _extensionRegistry);
    return msg;
  }

  public Trip getTrip(GtfsRealtime.TripUpdate tu) {
    String tid = tu.getTrip().getTripId();
    return _dao.getTripForId(new AgencyAndId(_agencyId, tid));
  }
}
