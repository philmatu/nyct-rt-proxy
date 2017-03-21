package com.kurtraschke.nyctrtproxy.model;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.google.common.collect.Sets;

import java.util.Date;
import java.util.Set;

public class MatchMetrics {

  private int nMatchedTrips = 0, nCancelledTrips = 0, nAddedTrips = 0;
  private int nUnmatchedNoStartDate = 0, nStrictMatch = 0, nLooseMatchSameDay = 0, nLooseMatchOtherDay = 0,
    nUnmatchedNoStopMatch = 0;

  public void add(TripMatchResult result) {
    switch (result.getStatus()) {
      case NO_TRIP_WITH_START_DATE:
        nAddedTrips++;
        nUnmatchedNoStartDate++;
        break;
      case NO_MATCH:
        nAddedTrips++;
        nUnmatchedNoStopMatch++;
        break;
      case STRICT_MATCH:
        nMatchedTrips++;
        nStrictMatch++;
        break;
      case LOOSE_MATCH:
        nMatchedTrips++;
        nLooseMatchSameDay++;
        break;
      case LOOSE_MATCH_ON_OTHER_SERVICE_DATE:
        nMatchedTrips++;
        nLooseMatchOtherDay++;
        break;
    }
  }

  public void addCancelled() {
    nCancelledTrips++;
  }

  public Set<MetricDatum> getReportedMetrics(Dimension dim, Date timestamp) {
    double nStatic = nMatchedTrips + nCancelledTrips;
    double nMatchedPctOfStatic = ((double) nMatchedTrips) / nStatic;
    double nCancelledPctOfStatic = ((double) nCancelledTrips) / nStatic;

    double nRt = nMatchedTrips + nAddedTrips;
    double nMatchedRtPct = ((double) nMatchedTrips) / nRt;
    double nAddedRtPct = ((double) nAddedTrips) / nRt;

    double nUnmatchedWithoutStartDatePct = ((double) nUnmatchedNoStartDate) / nRt;
    double nUnmatchedNoStopMatchPct = ((double) nUnmatchedNoStopMatch) / nRt;
    double nStrictMatchPct = ((double) nStrictMatch) / nRt;
    double nLooseMatchSameDayPct = ((double) nLooseMatchSameDay) / nRt;
    double nLooseMatchOtherDayPct = ((double) nLooseMatchOtherDay) / nRt;

    MetricDatum dMatched = metricCount(timestamp, "MatchedTrips", nMatchedTrips, dim);
    MetricDatum dAdded = metricCount(timestamp, "AddedTrips", nAddedTrips, dim);
    MetricDatum dCancelled = metricCount(timestamp, "CancelledTrips", nCancelledTrips, dim);
    MetricDatum dUnmatchedNoStartDate = metricCount(timestamp, "UnmatchedWithoutStartDate", nUnmatchedNoStartDate, dim);
    MetricDatum dUnmatchedNoStopMatch = metricCount(timestamp, "UnmatchedNoStopMatch", nUnmatchedNoStopMatch, dim);
    MetricDatum dStrictMatch = metricCount(timestamp, "StrictMatches", nStrictMatch, dim);
    MetricDatum dLooseMatchSameDay = metricCount(timestamp, "LooseMatchesSameDay", nLooseMatchSameDay, dim);
    MetricDatum dLooseMatchOtherDay = metricCount(timestamp, "LooseMatchesOtherDay", nLooseMatchOtherDay, dim);

    MetricDatum dMatchedPct = metricPct(timestamp, "MatchedStaticTripsPct", nMatchedPctOfStatic, dim);
    MetricDatum dCancelledPct = metricPct(timestamp, "CancelledStaticTripsPct", nCancelledPctOfStatic, dim);
    MetricDatum dMatchedRtPct = metricPct(timestamp, "MatchedRtTripsPct", nMatchedRtPct, dim);
    MetricDatum dAddedRtPct = metricPct(timestamp, "AddedRtTripsPct", nAddedRtPct, dim);
    MetricDatum dUnmatchedWithoutStartDatePct = metricPct(timestamp, "UnmatchedWithoutStartDatePct", nUnmatchedWithoutStartDatePct, dim);
    MetricDatum dUnmatchedNoStopMatchPct = metricPct(timestamp, "UnmatchedNoStopMatchPct", nUnmatchedNoStopMatchPct, dim);
    MetricDatum dStrictMatchPct = metricPct(timestamp, "StrictMatchPct", nStrictMatchPct, dim);
    MetricDatum dLooseMatchSameDayPct = metricPct(timestamp, "LooseMatchSameDayPct", nLooseMatchSameDayPct, dim);
    MetricDatum dLooseMatchOtherDayPct = metricPct(timestamp, "LooseMatchOtherDayPct", nLooseMatchOtherDayPct, dim);

    return Sets.newHashSet(dMatched, dAdded, dCancelled, dMatchedPct, dCancelledPct, dMatchedRtPct, dAddedRtPct,
            dUnmatchedNoStartDate, dStrictMatch, dLooseMatchSameDay, dLooseMatchOtherDay, dUnmatchedWithoutStartDatePct,
            dStrictMatchPct, dLooseMatchSameDayPct, dLooseMatchOtherDayPct, dUnmatchedNoStopMatch, dUnmatchedNoStopMatchPct);
  }

  public int getMatchedTrips() {
    return nMatchedTrips;
  }

  public int getAddedTrips() {
    return nAddedTrips;
  }

  public int getCancelledTrips() {
    return nCancelledTrips;
  }

  private static MetricDatum metricCount(Date timestamp, String name, int value, Dimension dim) {
    return new MetricDatum().withMetricName(name)
            .withTimestamp(timestamp)
            .withValue((double) value)
            .withUnit(StandardUnit.Count)
            .withDimensions(dim);
  }

  private static MetricDatum metricPct(Date timestamp, String name, double value, Dimension dim) {
    return new MetricDatum().withMetricName(name)
            .withTimestamp(timestamp)
            .withValue(value * 100.0)
            .withUnit(StandardUnit.Percent)
            .withDimensions(dim);
  }
}
