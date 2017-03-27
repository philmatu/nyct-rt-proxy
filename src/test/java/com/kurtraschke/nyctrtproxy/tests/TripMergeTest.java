package com.kurtraschke.nyctrtproxy.tests;

import com.google.transit.realtime.GtfsRealtime.*;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import org.junit.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TripMergeTest extends RtTestRunner {

  @Test
  public void testMerging() throws Exception {
    String protobuf = "21_2017-03-13.pb";
    int feedId = 21;

    FeedMessage msg = readFeedMessage(protobuf);
    List<TripUpdate> updates = _processor.processFeed(feedId, msg);

    // we expect these to get merged:
    // 098650_D..S (D01, D03) 099050_D..S (lots of stops)
    assertTrue(getByRouteDirectionAndTime(updates, "D", "S", 99050).isEmpty());

    List<TripUpdate> tus = getByRouteDirectionAndTime(updates, "D", "S", 98650);
    assertEquals(1, tus.size());

    TripUpdate update = tus.get(0);

    List<String> stopTimes = update.getStopTimeUpdateList().stream().map(stu -> stu.getStopId()).collect(Collectors.toList());
    List<String> gtfsStops = getStopTimesForTripUpdate(update).stream().map(s -> s.getStop().getId().getId()).collect(Collectors.toList());

    assertEquals(stopTimes, gtfsStops);

    List<TripUpdate.StopTimeUpdate> sortedStus = new ArrayList<>(update.getStopTimeUpdateList());
    Collections.sort(sortedStus, (s, t) -> (int) (s.getDeparture().getTime() - t.getDeparture().getTime()));
    List<String> sortedStopTimes = sortedStus.stream().map(stu -> stu.getStopId()).collect(Collectors.toList());
    assertEquals(stopTimes, sortedStopTimes);
  }

  private static List<StopTime> getStopTimesForTripUpdate(TripUpdate tu) {
    String tripId = tu.getTrip().getTripId();
    Trip trip = _dao.getTripForId(new AgencyAndId("MTA NYCT", tripId));
    if (trip == null)
      return Collections.emptyList();
    return _dao.getStopTimesForTrip(trip);
  }

  private static List<TripUpdate> getByRouteDirectionAndTime(List<TripUpdate> updates, String routeId, String direction, int odtime) {
    return updates.stream()
            .filter(tu -> {
              NyctTripId id = NyctTripId.buildFromString(tu.getTrip().getTripId());
              return id.getRouteId().equals(routeId) && id.getDirection().equals(direction) && id.getOriginDepartureTime() == odtime;
            }).collect(Collectors.toList());
  }
}
