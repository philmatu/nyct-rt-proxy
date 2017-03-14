package com.kurtraschke.nyctrtproxy.services;

public interface ProxyDataListener {
  void reportMatchesForRoute(String routeId, int nMatchedTrips, int nAddedTrips, int nCancelledTrips);
  void reportMatchesForFeed(String feedId, int nMatchedTrips, int nAddedTrips, int nCancelledTrips);
}
