package com.kurtraschke.nyctrtproxy.services;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Date;

public class CloudwatchProxyDataListener implements ProxyDataListener {
  private static final Logger _log = LoggerFactory.getLogger(CloudwatchProxyDataListener.class);

  @Inject
  @Named("cloudwatch.namespace")
  private String _namespace;

  @Inject
  @Named("cloudwatch.accessKey")
  private String _accessKey;

  @Inject
  @Named("cloudwatch.secretKey")
  private String _secretKey;

  private AmazonCloudWatchAsync _client;

  private AsyncHandler<PutMetricDataRequest, PutMetricDataResult> _handler;

  @PostConstruct
  public void init() {
    //_client = new AmazonCloudWatchAsyncClient(new BasicAWSCredentials(_accessKey, _secretKey));
    BasicAWSCredentials cred = new BasicAWSCredentials(_accessKey, _secretKey);
    _client = AmazonCloudWatchAsyncClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(cred))
            .build();
    _handler = new AsyncHandler<PutMetricDataRequest, PutMetricDataResult>() {
      @Override
      public void onError(Exception e) {
        _log.error("Error sending to cloudwatch: " + e);
      }

      @Override
      public void onSuccess(PutMetricDataRequest request, PutMetricDataResult putMetricDataResult) {
        // do nothing
      }
    };
  }

  @Override
  public void reportMatchesForRoute(String routeId, int nMatchedTrips, int nAddedTrips, int nCancelledTrips) {
    Date timestamp = new Date();
    Dimension dim = new Dimension();
    dim.setName("route");
    dim.setValue(routeId);
    reportMatches(timestamp, dim, nMatchedTrips, nAddedTrips, nCancelledTrips);
    _log.info("time={}, route={}, nMatchedTrips={}, nAddedTrips={}, nCancelledTrips={}", timestamp, routeId, nMatchedTrips, nAddedTrips, nCancelledTrips);

  }
  @Override
  public void reportMatchesForFeed(String feedId, int nMatchedTrips, int nAddedTrips, int nCancelledTrips) {
    Date timestamp = new Date();
    Dimension dim = new Dimension();
    dim.setName("feed");
    dim.setValue(feedId);
    reportMatches(timestamp, dim, nMatchedTrips, nAddedTrips, nCancelledTrips);
    _log.info("time={}, feed={}, nMatchedTrips={}, nAddedTrips={}, nCancelledTrips={}", timestamp, feedId, nMatchedTrips, nAddedTrips, nCancelledTrips);
  }

  private void reportMatches(Date timestamp, Dimension dim, int nMatchedTrips, int nAddedTrips, int nCancelledTrips) {
    double nStatic = nMatchedTrips + nCancelledTrips;
    double nMatchedPct = (double) nMatchedTrips / nStatic;
    double nCancelledPct = (double) nCancelledTrips / nStatic;

    MetricDatum dMatched = metricCount(timestamp, "MatchedTrips", nMatchedTrips, dim);
    MetricDatum dAdded = metricCount(timestamp, "AddedTrips", nAddedTrips, dim);
    MetricDatum dCancelled = metricCount(timestamp, "CancelledTrips", nCancelledTrips, dim);
    MetricDatum dMatchedPct = metricPct(timestamp, "MatchedTripsPct", nMatchedPct, dim);
    MetricDatum dCancelledPct = metricPct(timestamp, "CancelledTripsPct", nCancelledPct, dim);

    PutMetricDataRequest request = new PutMetricDataRequest()
            .withMetricData(dMatched, dAdded, dCancelled, dMatchedPct, dCancelledPct)
            .withNamespace(_namespace);

    _client.putMetricDataAsync(request, _handler);
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
