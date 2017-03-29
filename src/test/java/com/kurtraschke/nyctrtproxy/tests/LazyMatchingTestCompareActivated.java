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
