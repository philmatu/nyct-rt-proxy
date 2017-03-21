package com.kurtraschke.nyctrtproxy.tests;

import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LazyMatchingTestCompareActivated extends LazyMatchingTest {
  public LazyMatchingTestCompareActivated() {
    super("SI", "11_2017-03-13.pb");
  }

  @Override
  public void checkMatchResult(long timestamp, NyctTripId rtid, GtfsRealtime.TripUpdateOrBuilder tripUpdate, TripMatchResult lazyTrip) {
    TripMatchResult activatedTrip = atm.match(tripUpdate, rtid, timestamp);

    if (activatedTrip.hasResult()) {
      String atid = activatedTrip.getResult().getTrip().getId().getId();
      assertTrue("activated trip is present: " + atid, lazyTrip.hasResult());
      String ltid = lazyTrip.getResult().getTrip().getId().getId();
      assertEquals(atid, ltid);
    }
  }
}
