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
package com.kurtraschke.nyctrtproxy.services;

import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;

import java.util.Date;
import java.util.Set;

/**
 * Match a TripUpdate to a static GTFS trip.
 *
 * Implementations may require an initialization step; if so they can override {@link #initForFeed}.
 *
 * @author Simon Jacobs
 */
public interface TripMatcher {

  /**
   * Match a TripUpdate to a static GTFS trip, if possible.
   *
   * @param tu TripUpdate (or TripUpdate.Builder) to be matched
   * @param rtid parsed ID of TripUpdate
   * @param timestamp time of feed in seconds
   * @return results of match
   */
  TripMatchResult match(GtfsRealtime.TripUpdateOrBuilder tu, NyctTripId rtid, long timestamp);

  /**
   * Optional initialization step matcher may require. (In practice, only ActivatedTripMatcher uses this.)
   *
   * @param start Matched static trips should have a start time after or equal to this value.
   * @param end Matched static trips should have an end time before or equal to this value.
   * @param routeIds set of routes which will need to be matched during subsequent calls to {@link #match}.
   */
  void initForFeed(Date start, Date end, Set<String> routeIds);
}
