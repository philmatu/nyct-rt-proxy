package com.kurtraschke.nyctrtproxy.services;

import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.model.TripMatchResult;

import java.util.Date;
import java.util.Set;

public interface TripMatcher {

  TripMatchResult match(GtfsRealtime.TripUpdateOrBuilder tu, NyctTripId rtid, long timestamp);

  void initForFeed(Date start, Date end, Set<String> routeIds);
}
