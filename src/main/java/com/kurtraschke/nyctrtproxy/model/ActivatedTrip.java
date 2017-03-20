/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy.model;

import com.google.transit.realtime.GtfsRealtimeNYCT.*;
import com.google.transit.realtime.GtfsRealtime.*;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.util.List;

/**
 *
 * @author kurt
 */
public class ActivatedTrip {

  private final ServiceDate sd;
  private final Trip theTrip;
  private final NyctTripId parsedTripId;
  private long start;
  private long end;
  private List<StopTime> stopTimes;

  public ActivatedTrip(ServiceDate sd, Trip theTrip, long start, long end, List<StopTime> stopTimes) {
    this.sd = sd;
    this.theTrip = theTrip;
    this.parsedTripId = NyctTripId.buildFromString(theTrip.getId().getId());
    this.start = sd.getAsDate().getTime()/1000 + start;
    this.end = sd.getAsDate().getTime()/1000 + end;
    this.stopTimes = stopTimes;
  }

  public ServiceDate getServiceDate() {
    return sd;
  }

  public Trip getTrip() {
    return theTrip;
  }

  public NyctTripId getParsedTripId() {
    return parsedTripId;
  }

  public long getEnd() {
    return end;
  }

  public long getStart() {
    return start;
  }

  public List<StopTime> getStopTimes() {
    return stopTimes;
  }

  public boolean activeFor(TripReplacementPeriod trp, long timestamp) {
    TimeRange tr = trp.getReplacementPeriod();
    long trStart = tr.hasStart() ? tr.getStart() : timestamp;
    long trEnd = tr.hasEnd() ? tr.getEnd() : timestamp;
    return getStart() < timestamp && getEnd() > trStart; // only consider trips that have *started* as active.
  }

  @Override
  public String toString() {
    return "ActivatedTrip{" + "sd=" + sd + ", theTrip=" + theTrip + '}';
  }

}
