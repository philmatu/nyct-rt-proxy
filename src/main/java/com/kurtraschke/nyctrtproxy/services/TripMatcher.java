package com.kurtraschke.nyctrtproxy.services;

import com.google.common.collect.Multimap;
import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;

public interface TripMatcher {

  TripMatchResult match(GtfsRealtime.TripUpdateOrBuilder tu, NyctTripId rtid, long timestamp);

  void initForFeed(Multimap<String, ActivatedTrip> map);
}
