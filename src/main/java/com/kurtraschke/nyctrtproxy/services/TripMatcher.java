package com.kurtraschke.nyctrtproxy.services;

import com.google.common.collect.Multimap;
import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;

import java.util.Optional;

public interface TripMatcher {

  Optional<ActivatedTrip> match(GtfsRealtime.TripUpdateOrBuilder tu, NyctTripId rtid);

  void initForFeed(Multimap<String, ActivatedTrip> map);
}
