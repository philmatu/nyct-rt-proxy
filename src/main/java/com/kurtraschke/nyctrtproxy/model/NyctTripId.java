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
package com.kurtraschke.nyctrtproxy.model;

import org.apache.commons.lang3.StringUtils;
import org.onebusaway.gtfs.model.Trip;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GTFS or realtime trip identifier broken into constituent parts; most importantly, route, direction, and origin-departure time.
 *
 * Origin-departure time is encoded as hundredths of a minute after midnight.
 *
 * @author kurt
 */
public class NyctTripId {

  private int originDepartureTime;
  private String pathId;
  private String directionId;
  private String routeId;
  private String networkId;

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
   * Parse a trip ID (from static GTFS or realtime feed) into NyctTripId
   *
   * @param tripId the trip ID
   * @return parsed trip ID
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
      return new NyctTripId(originDepartureTime, pathId, routeId, directionId, networkId);

    } else {
      return null;
    }

  }

  /**
   * Build a NyctTripId from a static GTFS Trip.
   *
   * This is necessary because route W static trip IDs have "N" in the typical 'route' position.
   *
   * @param trip GTFS static trip
   * @return parsed trip ID
   */
  public static NyctTripId buildFromTrip(Trip trip) {
    NyctTripId id = buildFromString(trip.getId().getId());
    id.routeId = trip.getRoute().getId().getId();
    return id;
  }

  private NyctTripId(int originDepartureTime, String pathId, String routeId, String directionId, String networkId) {
    this.originDepartureTime = originDepartureTime;
    this.pathId = pathId;
    this.routeId = routeId;
    this.directionId = directionId;
    this.networkId = networkId;
  }

  @Override
  public String toString() {
    return String.format("%06d_%s", originDepartureTime, pathId);
  }

  /**
   * Check route, direction, and network ID match. Note only Feed 1 has IDs which *may* have network ID.
   *
   * This method throws an exception if {@link #networkId} is null.
   *
   * @param other
   * @return true if match, false otherwise
   */
  public boolean strictMatch(NyctTripId other) {
    return  looseMatch(other)
            && getNetworkId().equals(other.getNetworkId());
  }

  /**
   * Check route, direction, and origin-departure time match.
   *
   * @param other
   * @return true if match, false otherwise
   */
  public boolean looseMatch(NyctTripId other) {
    return routeDirMatch(other)
            && getOriginDepartureTime() == other.getOriginDepartureTime();
  }

  /**
   * Check route and direction match.
   *
   * @param other
   * @return true if match, false otherwise
   */
  public boolean routeDirMatch(NyctTripId other) {
    return getRouteId().equals(other.getRouteId())
            && getDirection().equals(other.getDirection());
  }

  /**
   * Obtain NyctTripId that is relative to a 26-hour schedule on the previous day, otherwise the same as this one.
   *
   * @return new trip ID
   */
  public NyctTripId relativeToPreviousDay() {
    int time = originDepartureTime + (24 * 60 * 100);
    return new NyctTripId(time, pathId, routeId, directionId, networkId);
  }
}
