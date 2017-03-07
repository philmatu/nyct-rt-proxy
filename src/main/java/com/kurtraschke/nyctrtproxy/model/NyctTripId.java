/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy.model;

import com.google.common.base.Joiner;

import org.apache.commons.lang3.StringUtils;

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
    return String.valueOf(pathId.charAt(3));
  }

  /**
   *
   *
   *
   *
   * @param tripId
   * @return
   */
  public static NyctTripId buildFromString(String tripId) {
    ArrayDeque<String> tripIdParts = new ArrayDeque<>(Arrays.asList(tripId.split("_")));

    String timetable = null;
    int originDepartureTime;
    String pathId;

    switch (tripIdParts.size()) {
      case 3:
        timetable = tripIdParts.removeFirst();
      case 2:
        originDepartureTime = Integer.parseInt(tripIdParts.removeFirst(), 10);
        pathId = tripIdParts.removeFirst();
        break;
      default:
        throw new IllegalArgumentException();
    }

    return new NyctTripId(timetable, originDepartureTime, pathId);
  }

  /**
   *
   * 112250_L..S S20170301WKD_115720SI..S W20170301WKD_114000W..N
   *
   *
   * @param tripId
   * @return
   */
  public static NyctTripId buildFromAlternateString(String tripId) {
    int originDepartureTime;
    String pathId;

    Pattern pat = Pattern.compile("(?<originDepartureTime>[0-9]{6})_?(?<route>[A-Z0-9]+)\\.\\.(?<direction>[NS])$");

    Matcher matcher = pat.matcher(tripId);

    if (matcher.find()) {
      originDepartureTime = Integer.parseInt(matcher.group("originDepartureTime"), 10);
      pathId = StringUtils.rightPad(matcher.group("route"), 3, '.') + matcher.group("direction");
      return new NyctTripId(null, originDepartureTime, pathId);

    } else {
      throw new IllegalArgumentException();
    }

  }

  private NyctTripId(String timetable, int originDepartureTime, String pathId) {
    this.timetable = timetable;
    this.originDepartureTime = originDepartureTime;
    this.pathId = pathId;
  }

  private boolean hasTimetable() {
    return this.timetable != null;
  }

  @Override
  public String toString() {
    Joiner joiner = Joiner.on("_").skipNulls(); 
    
    return joiner.join(timetable, originDepartureTime, pathId);
  }

}
