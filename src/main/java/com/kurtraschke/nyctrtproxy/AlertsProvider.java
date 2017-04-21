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
package com.kurtraschke.nyctrtproxy;

import com.google.inject.Inject;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFullUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.Alerts;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;
import org.onebusaway.nyc.gtfsrt.util.GtfsRealtimeLibrary;
import org.onebusaway.nyc.siri.support.SiriXmlSerializer;
import org.onebusaway.nyc.transit_data_manager.util.NycSiriUtil;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri.Siri;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AlertsProvider {

  private Logger _log = LoggerFactory.getLogger(AlertsProvider.class);

  private HttpClientConnectionManager _connectionManager;

  private CloseableHttpClient _httpClient;

  private ScheduledExecutorService _scheduledExecutorService;

  private ScheduledFuture _updater;

  private GtfsRealtimeSink _alertsSink;

  private String _serviceAlertsUrl = null;

  private int _refreshRate = 60;

  private SiriXmlSerializer _siriXmlSerializer;

  @Inject
  public void setHttpClientConnectionManager(HttpClientConnectionManager connectionManager) {
    _connectionManager = connectionManager;
  }

  @Inject
  public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
    _scheduledExecutorService = scheduledExecutorService;
  }

  @Inject
  public void setAlertsSink(@Alerts GtfsRealtimeSink alertsSink) {
    _alertsSink = alertsSink;
  }

  @Inject
  public void setSiriXmlSerializer(SiriXmlSerializer siriXmlSerializer) {
    _siriXmlSerializer = siriXmlSerializer;
  }

  @Inject(optional = true)
  public void setRefreshRate(@Named("NYCT.serviceAlertsRefreshRate") int refreshRate) {
    _refreshRate = refreshRate;
  }

  @Inject(optional = true)
  public void setServiceAlertsUrl(@Named("NYCT.serviceAlertsUrl") String url) {
    _serviceAlertsUrl = url;
  }

  @PostConstruct
  public void start() {
    if (_serviceAlertsUrl != null) {
      _httpClient = HttpClientBuilder.create().setConnectionManager(_connectionManager).build();
      _updater = _scheduledExecutorService.scheduleWithFixedDelay(this::update, 0, _refreshRate, TimeUnit.SECONDS);
    }
  }

  @PreDestroy
  public void stop() {
    if (_updater != null) {
      _updater.cancel(false);
    }
    _scheduledExecutorService.shutdown();
    _connectionManager.shutdown();
  }

  public void update() {
    HttpGet get = new HttpGet(_serviceAlertsUrl);

    Siri siri = null;
    try (CloseableHttpResponse response = _httpClient.execute(get)) {
      String xml = EntityUtils.toString(response.getEntity());
      siri = _siriXmlSerializer.fromXml(xml);
    } catch(Exception ex) {
      _log.error("Error getting service alerts URL: " + ex.getMessage());
    }
    if (siri == null) {
      _log.error("Unable to process siri");
      return;
    }

    List<ServiceAlertBean> serviceAlerts = NycSiriUtil.getSiriAsServiceAlertBeans(siri);

    GtfsRealtimeFullUpdate gtfu = new GtfsRealtimeFullUpdate();
    for (ServiceAlertBean serviceAlert : serviceAlerts) {
      FeedEntity.Builder fe = FeedEntity.newBuilder();
      Alert.Builder alert = GtfsRealtimeLibrary.makeAlert(serviceAlert);
      fe.setAlert(alert);
      fe.setId(serviceAlert.getId());
      gtfu.addEntity(fe.build());
    }

    _log.info("Updating alerts feed with {} service alerts", serviceAlerts.size());

    _alertsSink.handleFullUpdate(gtfu);
  }
}
