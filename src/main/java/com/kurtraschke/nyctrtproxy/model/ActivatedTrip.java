/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy.model;

import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

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

  public ActivatedTrip(ServiceDate sd, Trip theTrip, long start, long end) {
    this.sd = sd;
    this.theTrip = theTrip;
    this.parsedTripId = NyctTripId.buildFromString(theTrip.getId().getId());
    this.start = sd.getAsDate().getTime()/1000 + start;
    this.end = sd.getAsDate().getTime()/1000 + end;
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

  @Override
  public String toString() {
    return "ActivatedTrip{" + "sd=" + sd + ", theTrip=" + theTrip + '}';
  }

}
