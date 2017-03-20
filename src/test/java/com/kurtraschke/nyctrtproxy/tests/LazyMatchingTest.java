package com.kurtraschke.nyctrtproxy.tests;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;
import com.kurtraschke.nyctrtproxy.services.ActivatedTripMatcher;
import com.kurtraschke.nyctrtproxy.services.LazyTripMatcher;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.kurtraschke.nyctrtproxy.util.NycRealtimeUtil.*;
import static org.junit.Assert.*;

public class LazyMatchingTest extends RtTestRunner {

  private static final String routeId = "SI";
  private static final String filename = "11_2017-03-13.pb";

  @Test
  public void test() throws IOException {
    LazyTripMatcher ltm = new LazyTripMatcher();
    ltm.setGtfsRelationalDao(_dao);
    ltm.setCalendarServiceData(_csd);
    ltm.setLooseMatchDisabled(false);

    ActivatedTripMatcher atm = new ActivatedTripMatcher();
    atm.setTripActivator(_ta);

    FeedMessage msg = readFeedMessage(filename);

    for (GtfsRealtimeNYCT.TripReplacementPeriod trp : msg.getHeader()
            .getExtension(GtfsRealtimeNYCT.nyctFeedHeader)
            .getTripReplacementPeriodList()) {
      if (routeId.equals(trp.getRouteId())) {

        List<TripUpdate> updates = msg.getEntityList().stream()
                .filter(FeedEntity::hasTripUpdate)
                .map(FeedEntity::getTripUpdate)
                .filter(tu -> tu.getTrip().getRouteId().equals(routeId))
                .collect(Collectors.toList());

        TimeRange range = trp.getReplacementPeriod();
        Date start = range.hasStart() ? new Date(range.getStart() * 1000) : earliestTripStart(updates);
        Date end = range.hasEnd() ? new Date(range.getEnd() * 1000) : new Date(msg.getHeader().getTimestamp() * 1000);

        Multimap<String, ActivatedTrip> staticTripsForRoute = ArrayListMultimap.create();
        for (ActivatedTrip trip : _ta.getTripsForRangeAndRoute(start, end, routeId).collect(Collectors.toList())) {
          staticTripsForRoute.put(trip.getTrip().getRoute().getId().getId(), trip);
        }

        atm.initForFeed(staticTripsForRoute);

        long timestamp = msg.getHeader().getTimestamp();

        for (TripUpdate tu : updates) {
          TripUpdate.Builder tub = TripUpdate.newBuilder(tu);

          // SI is a route needing fixup (see ProxyProvider)
          TripDescriptor.Builder tb = tub.getTripBuilder();
          NyctTripId rtid = NyctTripId.buildFromString(tb.getTripId());
          tb.setStartDate(fixedStartDate(tb));
          tub.getStopTimeUpdateBuilderList().forEach(stub -> {
            stub.setStopId(stub.getStopId() + rtid.getDirection());
          });
          tb.setTripId(rtid.toString());

          TripMatchResult lazyTrip = ltm.match(tub, rtid, timestamp);
          TripMatchResult activatedTrip = atm.match(tub, rtid, timestamp);

          if (activatedTrip.hasResult()) {
            String atid = activatedTrip.getResult().getTrip().getId().getId();
            assertTrue("activated trip is present: " + atid, lazyTrip.hasResult());
            String ltid = lazyTrip.getResult().getTrip().getId().getId();
            assertEquals(atid, ltid);
          }
        }
      }
    }
  }
}
