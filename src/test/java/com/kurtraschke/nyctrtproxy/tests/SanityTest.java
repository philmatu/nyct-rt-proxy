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
import com.kurtraschke.nyctrtproxy.services.TripUpdateProcessor;
import org.junit.Test;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SanityTest extends RtTestRunner {

  private static final Logger _log = LoggerFactory.getLogger(SanityTest.class);

  @Inject
  private TripUpdateProcessor _processor;

  @Inject
  private GtfsRelationalDao _dao;

  @Test
  public void test1_2017_03_13() throws Exception {
    test(1, "1_2017-03-13.pb", 188, 28);
  }

  @Test
  public void test2_2017_03_13() throws Exception {
    test(2, "2_2017-03-13.pb", 28, 0);
  }

  @Test
  public void test11_2017_03_13() throws Exception {
    test(11, "11_2017-03-13.pb", 8, 0);
  }

  // this is specifically for testing trip coercion
  @Test
  public void test11_2017_03_21() throws Exception {
    test(11, "11_2017-03-21.pb", 6, 0);
  }

  @Test
  public void test16_2017_03_13() throws Exception {
    test(16, "16_2017-03-13.pb", 58, 44);
  }

  @Test
  public void test21_2017_03_13() throws Exception {
    test(21, "21_2017-03-13.pb", 29, 25);
  }

  @Test
  public void test21_2017_04_20() throws Exception {
    test(21, "21_2017-04-20.pb", 43, 1);
  }

  // Test overnight service
  @Test
  public void test11_midnight() throws Exception {
    test(11, "11_2017-03-23_00:33.pb", 5, 0);
  }

  // Test 5X -> 5 rewriting
  @Test
  public void test1_peak() throws Exception {
    test(1, "1_peak_sample.pb", 258, 30);
  }

  private void test(int feedId, String protobuf, int nScheduledExpected, int nAddedExpected) throws Exception {
    FeedMessage msg = readFeedMessage(protobuf);
    List<TripUpdate> updates = _processor.processFeed(feedId, msg);

    int nScheduled = 0, nAdded = 0, nRt = 0;

    for (TripUpdate tripUpdate : updates) {
      switch(tripUpdate.getTrip().getScheduleRelationship()) {
        case SCHEDULED:
          checkScheduledTrip(tripUpdate);
          nScheduled++;
          nRt++;
          break;
        case ADDED:
          checkAddedTrip(tripUpdate);
          nAdded++;
          nRt++;
          break;
        default:
          throw new Exception("unexpected schedule relationship");
      }
    }

    _log.info("nScheduled={}, nAdded={}", nScheduled, nAdded);
    // make sure we have improved or stayed the same
    assertTrue(nScheduled >= nScheduledExpected);
    assertTrue(nAdded <= nAddedExpected);

    // if improved:
    if (nScheduled != nScheduledExpected || nAdded != nAddedExpected) {
      _log.info("Better than expected, could update test.");
    }
  }

  private void checkScheduledTrip(TripUpdate tripUpdate) {
    Trip trip = getTrip(tripUpdate);
    assertNotNull(trip);
    assertEquals(tripUpdate.getTrip().getRouteId(), trip.getRoute().getId().getId());

    List<TripUpdate.StopTimeUpdate> stus = tripUpdate.getStopTimeUpdateList();
    assertFalse(stus.isEmpty());

    Set<String> stopIds = _dao.getStopTimesForTrip(trip)
            .stream()
            .map(st -> st.getStop().getId().getId())
            .collect(Collectors.toSet());

    for (TripUpdate.StopTimeUpdate stu : stus) {
      assertTrue(stopIds.contains(stu.getStopId()));
    }

  }

  private void checkAddedTrip(TripUpdate tripUpdate) {
    Trip trip = getTrip(tripUpdate);
    assertNull(trip);
  }


}
