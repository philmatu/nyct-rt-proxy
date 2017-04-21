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
package com.kurtraschke.nyctrtproxy.util;

import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Utility functions required both by TripUpdateProcessor and test code.
 *
 * @author Simon Jacobs
 */
public class NycRealtimeUtil {

  private static final Logger _log = LoggerFactory.getLogger(NycRealtimeUtil.class);

  public static String fixedStartDate(GtfsRealtime.TripDescriptorOrBuilder td) {
    return td.getStartDate().substring(0, 10).replace("-", "");
  }

  public static Date earliestTripStart(Collection<GtfsRealtime.TripUpdate> updates) {
    OptionalLong time = updates.stream()
            .mapToLong(tu -> tripUpdateStart(tu))
            .filter(n -> n > 0).min();
    return time.isPresent() ? new Date(time.getAsLong()) : null;
  }

  // take a TripUpdate and return the epoch time in millis that this trip started
  private static long tripUpdateStart(GtfsRealtime.TripUpdate tu) {
    GtfsRealtime.TripDescriptor td = tu.getTrip();
    NyctTripId rtid = NyctTripId.buildFromString(td.getTripId());
    if (rtid == null)
      return -1;
    int minHds = rtid.getOriginDepartureTime();
    String startDate = td.getStartDate().length() > 8 ? fixedStartDate(td) : td.getStartDate();
    ServiceDate sd;
    try {
      sd = ServiceDate.parseString(startDate);
    } catch (ParseException e) {
      _log.error("Error parsing trip update={}, exception={}", tu, e);
      return Long.MAX_VALUE;
    }
    return sd.getAsDate().getTime() + (minHds * 600); // 600 millis in 1/100 minute
  }
}
