package com.kurtraschke.nyctrtproxy.model;

public class TripMatchResult implements Comparable<TripMatchResult> {

  // ordered by goodness to make comparison easier
  public enum Status {
    BAD_TRIP_ID,
    NO_TRIP_WITH_START_DATE,
    NO_MATCH,
    LOOSE_MATCH_ON_OTHER_SERVICE_DATE,
    LOOSE_MATCH_COERCION,
    LOOSE_MATCH,
    STRICT_MATCH
  };

  private Status status;
  private ActivatedTrip result;
  private int delta; // lateness of RT trip relative to static trip

  public TripMatchResult(Status status, ActivatedTrip result, int delta) {
    this.status = status;
    this.result = result;
    this.delta = delta;
  }

  // strict match
  public TripMatchResult(ActivatedTrip result) {
    this(Status.STRICT_MATCH, result, 0);
  }

  // no match
  public TripMatchResult(Status status) {
    this(status, null, 0);
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

  // return negative number, 0, or positive number as this object is worse, equal or better than the other
  @Override
  public int compareTo(TripMatchResult other) {
    if (this.status.equals(Status.LOOSE_MATCH_COERCION) && other.status.equals(Status.LOOSE_MATCH_COERCION))
      return other.delta - delta; // flip because smaller is better
    else
      return status.compareTo(other.status);
  }

  public static TripMatchResult looseMatch(ActivatedTrip at, int delta, boolean onServiceDay) {
    Status status = Status.LOOSE_MATCH;
    if (delta > 0)
      status = Status.LOOSE_MATCH_COERCION;
    if (!onServiceDay)
      status = Status.LOOSE_MATCH_ON_OTHER_SERVICE_DATE;
    return new TripMatchResult(status, at, delta);
  }

}
