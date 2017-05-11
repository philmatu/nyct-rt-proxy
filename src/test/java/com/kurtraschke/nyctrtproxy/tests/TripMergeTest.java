/*
 * Copyright (C) 2017 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.kurtraschke.nyctrtproxy.tests;

import com.google.inject.Inject;
import com.google.transit.realtime.GtfsRealtime.*;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.services.TripUpdateProcessor;
import org.junit.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsRelationalDao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TripMergeTest extends RtTestRunner {

  @Inject
  private TripUpdateProcessor _processor;

  @Inject
  private GtfsRelationalDao _dao;

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

  private List<StopTime> getStopTimesForTripUpdate(TripUpdate tu) {
    String tripId = tu.getTrip().getTripId();
    Trip trip = _dao.getTripForId(new AgencyAndId("MTA NYCT", tripId));
    if (trip == null)
      return Collections.emptyList();
    return _dao.getStopTimesForTrip(trip);
  }

  private List<TripUpdate> getByRouteDirectionAndTime(List<TripUpdate> updates, String routeId, String direction, int odtime) {
    return updates.stream()
            .filter(tu -> {
              NyctTripId id = NyctTripId.buildFromTripDescriptor(tu.getTrip());
              return id.getRouteId().equals(routeId) && id.getDirection().equals(direction) && id.getOriginDepartureTime() == odtime;
            }).collect(Collectors.toList());
  }
}
