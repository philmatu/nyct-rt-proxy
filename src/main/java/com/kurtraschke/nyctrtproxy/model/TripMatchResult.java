package com.kurtraschke.nyctrtproxy.model;

public class TripMatchResult {
  public enum Status {
    NO_TRIP_WITH_START_DATE, NO_MATCH, STRICT_MATCH, LOOSE_MATCH, LOOSE_MATCH_ON_OTHER_SERVICE_DATE, LOOSE_MATCH_COERCION
  };

  private Status status;
  private ActivatedTrip result;

  public TripMatchResult(Status status, ActivatedTrip result) {
    this.status = status;
    this.result = result;
  }

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

  public static TripMatchResult newStrictMatch(ActivatedTrip at) {
    return new TripMatchResult(Status.STRICT_MATCH, at);
  }
}
