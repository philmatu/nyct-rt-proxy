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

  public ActivatedTrip(ServiceDate sd, Trip theTrip) {
    this.sd = sd;
    this.theTrip = theTrip;
    this.parsedTripId = NyctTripId.buildFromString(theTrip.getId().getId());
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

  @Override
  public String toString() {
    return "ActivatedTrip{" + "sd=" + sd + ", theTrip=" + theTrip + '}';
  }

}
