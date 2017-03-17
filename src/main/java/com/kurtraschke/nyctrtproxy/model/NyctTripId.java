/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy.model;

import com.google.common.base.Joiner;

import org.apache.commons.lang3.StringUtils;
import org.onebusaway.gtfs.model.Trip;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author kurt
 */
public class NyctTripId {

  private final String timetable;
  private final int originDepartureTime;
  private final String pathId;
  private String directionId;
  private String routeId;
  private String networkId;

  public String getTimetable() {
    return timetable;
  }

  public int getOriginDepartureTime() {
    return originDepartureTime;
  }

  public String getPathId() {
    return pathId;
  }

  public String getDirection() {
    return directionId;
  }

  public String getRouteId() {
    return routeId;
  }

  public String getNetworkId() {
    return networkId;
  }


  /**
   *
   * 112250_L..S S20170301WKD_115720SI..S W20170301WKD_114000W..N
   *
   *
   * @param tripId
   * @return
   */
  public static NyctTripId buildFromString(String tripId) {
    int originDepartureTime;
    String pathId, routeId, directionId, networkId;

    Pattern pat = Pattern.compile("([A-Z0-9]+_)?(?<originDepartureTime>[0-9]{6})_?(?<route>[A-Z0-9]+)\\.+(?<direction>[NS])(?<network>[A-Z0-9]*)$");

    Matcher matcher = pat.matcher(tripId);

    if (matcher.find()) {
      originDepartureTime = Integer.parseInt(matcher.group("originDepartureTime"), 10);
      pathId = StringUtils.rightPad(matcher.group("route"), 3, '.') + matcher.group("direction");
      routeId = matcher.group("route");
      directionId = matcher.group("direction");
      networkId = matcher.group("network");
      if (networkId.length() == 0)
        networkId = null;
      return new NyctTripId(null, originDepartureTime, pathId, routeId, directionId, networkId);

    } else {
      throw new IllegalArgumentException();
    }

  }

  private NyctTripId(String timetable, int originDepartureTime, String pathId, String routeId, String directionId, String networkId) {
    this.timetable = timetable;
    this.originDepartureTime = originDepartureTime;
    this.pathId = pathId;
    this.routeId = routeId;
    this.directionId = directionId;
    this.networkId = networkId;
  }

  private boolean hasTimetable() {
    return this.timetable != null;
  }

  @Override
  public String toString() {
    Joiner joiner = Joiner.on("_").skipNulls();

    return joiner.join(timetable, originDepartureTime, pathId);
  }

  public boolean strictMatch(NyctTripId other) {
    return getOriginDepartureTime() == other.getOriginDepartureTime()
            && getDirection().equals(other.getDirection())
            && getNetworkId().equals(other.getNetworkId());
  }

  public boolean looseMatch(NyctTripId other) {
    return getOriginDepartureTime() == other.getOriginDepartureTime()
            && getDirection().equals(other.getDirection());
  }

  public boolean looseMatch(Trip trip) {
    return looseMatch(buildFromString(trip.getId().getId()));
  }
}
