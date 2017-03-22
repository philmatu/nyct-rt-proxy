package com.kurtraschke.nyctrtproxy.tests;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.kurtraschke.nyctrtproxy.util.NycRealtimeUtil.*;

public abstract class LazyMatchingTest extends RtTestRunner {

  // copy from ProxyProvider
  private static final Set<String> routesNeedingFixup = ImmutableSet.of("SI", "N", "Q", "R", "W", "B", "D");


  private String routeId;
  private String filename;

  protected LazyTripMatcher ltm;
  protected ActivatedTripMatcher atm;

  public LazyMatchingTest(String routeId, String filename) {
    this.routeId = routeId;
    this.filename = filename;
  }

  public abstract void checkMatchResult(long timestamp, NyctTripId rtid, TripUpdateOrBuilder tripUpdate, TripMatchResult result);

  @Test
  public void test() throws IOException {
    ltm = new LazyTripMatcher();
    ltm.setGtfsRelationalDao(_dao);
    ltm.setCalendarServiceData(_csd);
    ltm.setLooseMatchDisabled(false);

    atm = new ActivatedTripMatcher();
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

        atm.initForFeed(start, end, Collections.singleton(routeId));

        long timestamp = msg.getHeader().getTimestamp();

        for (TripUpdate tu : updates) {
          TripUpdate.Builder tub = TripUpdate.newBuilder(tu);

          TripDescriptor.Builder tb = tub.getTripBuilder();
          NyctTripId rtid = NyctTripId.buildFromString(tb.getTripId());
          if (routesNeedingFixup.contains(routeId)) {
            tb.setStartDate(fixedStartDate(tb));
            tub.getStopTimeUpdateBuilderList().forEach(stub -> {
              stub.setStopId(stub.getStopId() + rtid.getDirection());
            });
            tb.setTripId(rtid.toString());
          }

          TripMatchResult result = ltm.match(tub, rtid, timestamp);
          checkMatchResult(timestamp, rtid, tub, result);
        }
      }
    }
  }
}
