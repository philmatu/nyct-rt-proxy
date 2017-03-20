package com.kurtraschke.nyctrtproxy.model;

public class TripMatchResult {
  public enum Status {
    NO_TRIP_WITH_START_DATE, NO_MATCH, STRICT_MATCH, LOOSE_MATCH, LOOSE_MATCH_ON_OTHER_SERVICE_DATE
  };

  private Status status;
  private ActivatedTrip result;

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public ActivatedTrip getResult() {
    return result;
  }

  public void setResult(ActivatedTrip result) {
    this.result = result;
  }

  public boolean hasResult() {
    return result != null;
  }
}
