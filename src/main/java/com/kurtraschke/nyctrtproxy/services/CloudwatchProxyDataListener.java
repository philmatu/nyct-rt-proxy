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
import com.google.inject.Inject;
import com.kurtraschke.nyctrtproxy.model.MatchMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Named;
import java.util.Date;
import java.util.Set;

/**
 * Obtain metrics per-route and per-feed, and report to Cloudwatch.
 *
 * If cloudwatch credentials are not included in the configuration, this class will simply log.
 *
 * @author Simon Jacobs
 */
public class CloudwatchProxyDataListener implements ProxyDataListener {
  private static final Logger _log = LoggerFactory.getLogger(CloudwatchProxyDataListener.class);

  @Inject(optional = true)
  @Named("cloudwatch.namespace")
  private String _namespace;

  @Inject(optional = true)
  @Named("cloudwatch.accessKey")
  private String _accessKey;

  @Inject(optional = true)
  @Named("cloudwatch.secretKey")
  private String _secretKey;

  private boolean _disabled = false;

  private AmazonCloudWatchAsync _client;

  private AsyncHandler<PutMetricDataRequest, PutMetricDataResult> _handler;

  @PostConstruct
  public void init() {
    if (_secretKey == null || _accessKey == null || _namespace == null) {
      _log.info("No AWS credentials supplied, disabling cloudwatch");
      _disabled = true;
      return;
    }
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
  public void reportMatchesForRoute(String routeId, MatchMetrics metrics) {
    Date timestamp = new Date();
    Dimension dim = new Dimension();
    dim.setName("route");
    dim.setValue(routeId);
    reportMatches(timestamp, dim, metrics);
    _log.info("time={}, route={}, nMatchedTrips={}, nAddedTrips={}, nDuplicates={}, nMergedTrips={}", timestamp, routeId, metrics.getMatchedTrips(), metrics.getAddedTrips(), metrics.getDuplicates(), metrics.getMergedTrips());
  }

  @Override
  public void reportMatchesForFeed(String feedId, MatchMetrics metrics) {
    Date timestamp = new Date();
    Dimension dim = new Dimension();
    dim.setName("feed");
    dim.setValue(feedId);
    reportMatches(timestamp, dim, metrics);
    _log.info("time={}, feed={}, nMatchedTrips={}, nAddedTrips={}, nDuplicates={}, nMergedTrips={}", timestamp, feedId, metrics.getMatchedTrips(), metrics.getAddedTrips(), metrics.getDuplicates(), metrics.getMergedTrips());
  }

  private void reportMatches(Date timestamp, Dimension dim, MatchMetrics metrics) {
    if (_disabled)
      return;

    Set<MetricDatum> data = metrics.getReportedMetrics(dim, timestamp);
    PutMetricDataRequest request = new PutMetricDataRequest()
            .withMetricData(data)
            .withNamespace(_namespace);

    _client.putMetricDataAsync(request, _handler);
  }

}
