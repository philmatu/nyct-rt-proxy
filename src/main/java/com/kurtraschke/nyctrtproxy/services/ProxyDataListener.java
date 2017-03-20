package com.kurtraschke.nyctrtproxy.services;

import com.kurtraschke.nyctrtproxy.model.MatchMetrics;

public interface ProxyDataListener {
  void reportMatchesForRoute(String routeId, MatchMetrics metrics);
  void reportMatchesForFeed(String feedId, MatchMetrics metrics);
}
