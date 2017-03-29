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

import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.util.List;

/**
 * Trip active on a service day.
 *
 * @author kurt
 */
public class ActivatedTrip {

  private final ServiceDate sd;
  private final Trip theTrip;
  private final NyctTripId parsedTripId;
  private List<StopTime> stopTimes;

  public ActivatedTrip(ServiceDate sd, Trip theTrip, List<StopTime> stopTimes) {
    this.sd = sd;
    this.theTrip = theTrip;
    this.parsedTripId = NyctTripId.buildFromString(theTrip.getId().getId());
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

  public List<StopTime> getStopTimes() {
    return stopTimes;
  }

  @Override
  public String toString() {
    return "ActivatedTrip{" + "sd=" + sd + ", theTrip=" + theTrip + '}';
  }

}
