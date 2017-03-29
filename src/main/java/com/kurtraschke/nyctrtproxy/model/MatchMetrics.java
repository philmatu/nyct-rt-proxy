/*
 * Copyright (C) 2017 Cambridge Systematics, Inc.
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

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.google.common.collect.Sets;

import java.util.Date;
import java.util.Set;

/**
 * Aggregate metrics per route or per feed, and handle the creation of Cloudwatch metrics objects.
 *
 * @author Simon Jacobs
 */
public class MatchMetrics {

  private int nMatchedTrips = 0, nAddedTrips = 0;
  private int nUnmatchedNoStartDate = 0, nStrictMatch = 0, nLooseMatchSameDay = 0, nLooseMatchOtherDay = 0,
    nUnmatchedNoStopMatch = 0, nLooseMatchCoercion = 0, nDuplicates = 0, nBadId = 0, nMergedTrips = 0;

  private long latency = -1;

  Set<String> tripIds = Sets.newHashSet();

  /**
   * Add results of a match to currently aggregated metrics.
   *
   * @param result The result to add
   */
  public void add(TripMatchResult result) {
    if (result.hasResult()) {
      String tripId = result.getResult().getTrip().getId().getId();
      if (tripIds.contains(tripId)) {
        nDuplicates++;
      }
      tripIds.add(tripId);
    }
    switch (result.getStatus()) {
      case BAD_TRIP_ID:
        nAddedTrips++;
        nBadId++;
        break;
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
      case LOOSE_MATCH_COERCION:
        nMatchedTrips++;
        nLooseMatchCoercion++;
        break;
      case MERGED:
        nMatchedTrips++;
        nMergedTrips++;
        break;
    }
  }

  /**
   * Set internal latency metric from the timestamp of a feed, relative to current time.
   *
   * @param timestamp timestamp of feed in seconds
   */
  public void reportLatency(long timestamp) {
    latency = (new Date().getTime()/1000) - timestamp;
  }

  public long getLatency() {
    return latency;
  }

  /**
   * Return a set of Cloudwatch metric data based on currently aggregated data.
   *
   * @param dim Dimension for the returned metrics (likely route or feed)
   * @param timestamp Timestamp to use for returned metrics
   * @return Set of Cloudwatch metrics
   */
  public Set<MetricDatum> getReportedMetrics(Dimension dim, Date timestamp) {

    Set<MetricDatum> data = Sets.newHashSet();

    if (latency >= 0) {
      MetricDatum dLatency = new MetricDatum().withMetricName("Latency")
              .withTimestamp(timestamp)
              .withValue((double) latency)
              .withUnit(StandardUnit.Seconds)
              .withDimensions(dim);
      data.add(dLatency);
    }

    if (nMatchedTrips + nAddedTrips > 0)
      data.addAll(getMatchMetrics(dim, timestamp));

    return data;
  }

  private Set<MetricDatum> getMatchMetrics(Dimension dim, Date timestamp) {
    //double nCancelledPctOfStatic = ((double) nCancelledTrips) / nStatic;

    double nRt = nMatchedTrips + nAddedTrips;
    double nMatchedRtPct = ((double) nMatchedTrips) / nRt;
    double nAddedRtPct = ((double) nAddedTrips) / nRt;

    double nUnmatchedWithoutStartDatePct = ((double) nUnmatchedNoStartDate) / nRt;
    double nUnmatchedNoStopMatchPct = ((double) nUnmatchedNoStopMatch) / nRt;
    double nStrictMatchPct = ((double) nStrictMatch) / nRt;
    double nLooseMatchSameDayPct = ((double) nLooseMatchSameDay) / nRt;
    double nLooseMatchOtherDayPct = ((double) nLooseMatchOtherDay) / nRt;
    double nLooseMatchCoercionPct = ((double) nLooseMatchCoercion) / nRt;
    double nBadIdPct = ((double) nBadId) / nRt;
    double nMergedPct = ((double) nMergedTrips) / nRt;

    MetricDatum dMatched = metricCount(timestamp, "MatchedTrips", nMatchedTrips, dim);
    MetricDatum dAdded = metricCount(timestamp, "AddedTrips", nAddedTrips, dim);
    //MetricDatum dCancelled = metricCount(timestamp, "CancelledTrips", nCancelledTrips, dim);
    // can only send 20 metrics; these are taken out in favor of dMerged
    //MetricDatum dUnmatchedNoStartDate = metricCount(timestamp, "UnmatchedWithoutStartDate", nUnmatchedNoStartDate, dim);
    //MetricDatum dUnmatchedNoStopMatch = metricCount(timestamp, "UnmatchedNoStopMatch", nUnmatchedNoStopMatch, dim);
    MetricDatum dStrictMatch = metricCount(timestamp, "StrictMatches", nStrictMatch, dim);
    MetricDatum dLooseMatchSameDay = metricCount(timestamp, "LooseMatchesSameDay", nLooseMatchSameDay, dim);
    MetricDatum dLooseMatchOtherDay = metricCount(timestamp, "LooseMatchesOtherDay", nLooseMatchOtherDay, dim);
    MetricDatum dLooseMatchCoercion = metricCount(timestamp, "LooseMatchCoercion", nLooseMatchCoercion, dim);
    MetricDatum dDuplicateTrips = metricCount(timestamp, "DuplicateTripMatches", nDuplicates, dim);
    MetricDatum dBadId = metricCount(timestamp, "UnmatchedBadId", nBadId, dim);
    MetricDatum dMerged = metricCount(timestamp, "MergedTrips", nMergedTrips, dim);


    //MetricDatum dMatchedPct = metricPct(timestamp, "MatchedStaticTripsPct", nMatchedPctOfStatic, dim);
    //MetricDatum dCancelledPct = metricPct(timestamp, "CancelledStaticTripsPct", nCancelledPctOfStatic, dim);
    MetricDatum dMatchedRtPct = metricPct(timestamp, "MatchedRtTripsPct", nMatchedRtPct, dim);
    MetricDatum dAddedRtPct = metricPct(timestamp, "AddedRtTripsPct", nAddedRtPct, dim);
    MetricDatum dUnmatchedWithoutStartDatePct = metricPct(timestamp, "UnmatchedWithoutStartDatePct", nUnmatchedWithoutStartDatePct, dim);
    MetricDatum dUnmatchedNoStopMatchPct = metricPct(timestamp, "UnmatchedNoStopMatchPct", nUnmatchedNoStopMatchPct, dim);
    MetricDatum dStrictMatchPct = metricPct(timestamp, "StrictMatchPct", nStrictMatchPct, dim);
    MetricDatum dLooseMatchSameDayPct = metricPct(timestamp, "LooseMatchSameDayPct", nLooseMatchSameDayPct, dim);
    MetricDatum dLooseMatchOtherDayPct = metricPct(timestamp, "LooseMatchOtherDayPct", nLooseMatchOtherDayPct, dim);
    MetricDatum dLooseMatchCoercionPct = metricPct(timestamp, "LooseMatchCoercionPct", nLooseMatchCoercionPct, dim);
    MetricDatum dBadIdPct = metricPct(timestamp, "UnmatchedBadIdPct", nBadIdPct, dim);
    MetricDatum dMergedPct = metricPct(timestamp, "MergedTripsPct", nMergedPct, dim);

    return Sets.newHashSet(dMatched, dAdded, dMatchedRtPct, dAddedRtPct,
            dStrictMatch, dLooseMatchSameDay, dLooseMatchOtherDay, dUnmatchedWithoutStartDatePct,
            dStrictMatchPct, dLooseMatchSameDayPct, dLooseMatchOtherDayPct, dUnmatchedNoStopMatchPct,
            dLooseMatchCoercion, dLooseMatchCoercionPct, dDuplicateTrips, dBadId, dBadIdPct, dMerged, dMergedPct);
  }

  public int getMatchedTrips() {
    return nMatchedTrips;
  }

  public int getAddedTrips() {
    return nAddedTrips;
  }

  public int getDuplicates() {
    return nDuplicates;
  }

  public int getMergedTrips() {
    return nMergedTrips;
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
