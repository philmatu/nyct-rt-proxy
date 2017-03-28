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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
