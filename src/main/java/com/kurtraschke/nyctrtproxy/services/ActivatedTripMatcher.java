/*
 * Copyright (C) 2015 Kurt Raschke <kurt@kurtraschke.com>
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.transit.realtime.GtfsRealtime.*;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;

import javax.inject.Named;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Original matching code. Looks up activated trips, finds one with either a loose match or strict match depending on
 * whether the feed includes network ID.
 *
 * @author kurt (sjacobs refactored into this class)
 */
public class ActivatedTripMatcher implements TripMatcher {

  private TripActivator _tripActivator;

  // copied from ProxyProvider
  private Set<String> _routesUsingAlternateIdFormat = ImmutableSet.of("SI", "L", "N", "Q", "R", "W", "B", "D");

  private Multimap<String, ActivatedTrip> staticTripsForRoute;

  @Inject
  public void setTripActivator(TripActivator tripActivator) {
    _tripActivator = tripActivator;
  }

  @Inject(optional = true)
  public void setRoutesUsingAlternateIdFormat(@Named("NYCT.routesUsingAlternateIdFormat") String json) {
    Type type = new TypeToken<Set<String>>(){}.getType();
    _routesUsingAlternateIdFormat = new Gson().fromJson(json, type);
  }

  @Override
  public TripMatchResult match(TripUpdateOrBuilder tu, NyctTripId rtid, long timestamp) {
    String routeId = rtid.getRouteId();
    TripDescriptorOrBuilder tb = tu.getTrip();
    Stream<ActivatedTrip> candidateTrips = staticTripsForRoute.get(routeId)
            .stream()
            .filter(at -> at.getServiceDate().getAsString().equals(tb.getStartDate()));

    List<ActivatedTrip> candidateMatches = candidateTrips
            .filter(at -> {
              NyctTripId atid = at.getParsedTripId();
              return _routesUsingAlternateIdFormat.contains(routeId) ? atid.looseMatch(rtid) :  atid.strictMatch(rtid);
            }).collect(Collectors.toList());

    Optional<ActivatedTrip> at = candidateMatches.stream().findFirst();
    if (at.isPresent()) {
      if (_routesUsingAlternateIdFormat.contains(routeId))
        return new TripMatchResult(tu, TripMatchResult.Status.LOOSE_MATCH, at.get(), 0);
      else
        return new TripMatchResult(tu, at.get());
    }
    return new TripMatchResult(tu, TripMatchResult.Status.NO_MATCH);
  }

  @Override
  public void initForFeed(Date start, Date end, Set<String> routeIds) {
    staticTripsForRoute = ArrayListMultimap.create();
    for (ActivatedTrip trip : _tripActivator.getTripsForRangeAndRoutes(start, end, routeIds).collect(Collectors.toList())) {
      staticTripsForRoute.put(trip.getTrip().getRoute().getId().getId(), trip);
    }
  }
}
