package com.kurtraschke.nyctrtproxy.tests;

import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TripCoercionMatchingTest extends LazyMatchingTest {
  public TripCoercionMatchingTest() {
    super("SI", "11_2017-03-21.pb");
  }

  // This test is from hand-matching GTFS to RT.
  //  S20170321WKD_060100SI..N (10:01:00)  --> R20161106WKD_060100_SI.N03R
  //  S20170321WKD_060600SI..S (10:06:00)  --> R20161106WKD_060600_SI.S03R
  //  S20170321WKD_063100SI..N (10:31:00)  --> R20161106WKD_063100_SI.N03R
  //  S20170321WKD_063800SI..S (10:38:00)  ::> (coerced match) R20161106WKD_063600_SI.S03R
  //  S20170321WKD_066100SI..N (11:01:00)  --> R20161106WKD_066100_SI.N03R
  //  S20170321WKD_066600SI..S (11:06:00)  --> R20161106WKD_066600_SI.S03R

  @Override
  public void checkMatchResult(long timestamp, NyctTripId rtid, GtfsRealtime.TripUpdateOrBuilder tripUpdate, TripMatchResult result) {
    switch(rtid.getOriginDepartureTime()) {
      case 60100:
        assertLooseMatch(result, "R20161106WKD_060100_SI.N03R");
        break;
      case 60600:
        assertLooseMatch(result, "R20161106WKD_060600_SI.S03R");
        break;
      case 63100:
        assertLooseMatch(result, "R20161106WKD_063100_SI.N03R");
        break;
      case 63800:
        assertCoercedMatch(result, "R20161106WKD_063600_SI.S03R");
      case 66100:
        assertLooseMatch(result, "R20161106WKD_066100_SI.N03R");
        break;
      case 66600:
        assertLooseMatch(result, "R20161106WKD_066600_SI.S03R");
        break;
      default:
        throw new RuntimeException("unexpected trip " + rtid.toString());
    }
  }

  private static void assertLooseMatch(TripMatchResult result, String expected) {
    String matchedTripId = result.getResult().getTrip().getId().getId();
    assertEquals(expected, matchedTripId);
    assertEquals(result.getStatus(), TripMatchResult.Status.LOOSE_MATCH);
  }

  private static void assertCoercedMatch(TripMatchResult result, String expected) {
    String matchedTripId = result.getResult().getTrip().getId().getId();
    assertEquals(expected, matchedTripId);
    assertEquals(result.getStatus(), TripMatchResult.Status.LOOSE_MATCH_COERCION);
  }
}
