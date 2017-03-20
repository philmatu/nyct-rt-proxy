package com.kurtraschke.nyctrtproxy.services;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.transit.realtime.GtfsRealtime.*;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;

import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActivatedTripMatcher implements TripMatcher {

  private TripActivator _tripActivator;

  // copied from ProxyProvider
  private static final Set<String> routesUsingAlternateIdFormat = ImmutableSet.of("SI", "L", "N", "Q", "R", "W", "B", "D");

  private Multimap<String, ActivatedTrip> staticTripsForRoute;

  @Inject
  public void setTripActivator(TripActivator tripActivator) {
    _tripActivator = tripActivator;
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
              return routesUsingAlternateIdFormat.contains(routeId) ? atid.looseMatch(rtid) :  atid.strictMatch(rtid);
            }).collect(Collectors.toList());

    Optional<ActivatedTrip> at = candidateMatches.stream().findFirst();
    TripMatchResult result = new TripMatchResult();
    if (at.isPresent()) {
      result.setStatus(routesUsingAlternateIdFormat.contains(routeId) ? TripMatchResult.Status.LOOSE_MATCH : TripMatchResult.Status.STRICT_MATCH);
      result.setResult(at.get());
    }
    else {
      result.setStatus(TripMatchResult.Status.NO_MATCH);
    }
    return result;
  }

  @Override
  public void initForFeed(Multimap<String, ActivatedTrip> map) {
    staticTripsForRoute = map;
  }
}
