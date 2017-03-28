/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.kurtraschke.nyctrtproxy.services.TripUpdateProcessor;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFullUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;

import com.google.protobuf.ExtensionRegistry;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtimeNYCT;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;

/**
 *
 * @author kurt
 */
public class ProxyProvider {

  private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ProxyProvider.class);

  private static final ExtensionRegistry _extensionRegistry;

  private GtfsRealtimeSink _tripUpdatesSink;

  private String _key;

  private HttpClientConnectionManager _connectionManager;

  private CloseableHttpClient _httpClient;

  private ScheduledExecutorService _scheduledExecutorService;

  private ScheduledFuture _updater;

  private TripUpdateProcessor _processor;

  private int _nTries = 5;

  private List<Integer> _feedIds = Arrays.asList(1, 2, 11, 16, 21);

  static {
    _extensionRegistry = ExtensionRegistry.newInstance();
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctFeedHeader);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctTripDescriptor);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctStopTimeUpdate);
  }

  @Inject
  public void setTripUpdatesSink(@TripUpdates GtfsRealtimeSink tripUpdatesSink) {
    _tripUpdatesSink = tripUpdatesSink;
  }

  @Inject
  public void setHttpClientConnectionManager(HttpClientConnectionManager connectionManager) {
    _connectionManager = connectionManager;
  }

  @Inject
  public void setScheduledExecutorService(ScheduledExecutorService service) {
    _scheduledExecutorService = service;
  }

  @Inject
  public void setKey(@Named("NYCT.key") String key) {
    _key = key;
  }

  @Inject(optional = true)
  public void setNTries(@Named("NYCT.nTries") int nTries) {
    _nTries = nTries;
  }

  @Inject(optional = true)
  public void setFeedIds(@Named("NYCT.feedIds") String json) {
    Type type = new TypeToken<List<Integer>>(){}.getType();
    _feedIds = new Gson().fromJson(json, type);
  }

  @Inject
  public void setTripUpdateProcessor(TripUpdateProcessor processor) {
    _processor = processor;
  }

  @PostConstruct
  public void start() {
    _httpClient = HttpClientBuilder.create().setConnectionManager(_connectionManager).build();
    _updater = _scheduledExecutorService.scheduleWithFixedDelay(this::update, 0, 60, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void stop() {
    _updater.cancel(false);
    _scheduledExecutorService.shutdown();
    _connectionManager.shutdown();
  }

  public void update() {
    _log.info("doing update");

    GtfsRealtimeFullUpdate grfu = new GtfsRealtimeFullUpdate();

    List<TripUpdate> tripUpdates = Lists.newArrayList();

    // For each feed ID, read in GTFS-RT, process trip updates, push to output.
    for (int feedId : _feedIds) {
      URI feedUrl;

      try {
        URIBuilder ub = new URIBuilder("http://datamine.mta.info/mta_esi.php");

        ub.addParameter("key", _key);
        ub.addParameter("feed_id", Integer.toString(feedId));

        feedUrl = ub.build();
      } catch (URISyntaxException ex) {
        throw new RuntimeException(ex);
      }

      HttpGet get = new HttpGet(feedUrl);

      FeedMessage message = null;
      for (int tries = 0; tries < _nTries; tries++) {
        try {
          CloseableHttpResponse response = _httpClient.execute(get);
          try (InputStream streamContent = response.getEntity().getContent()) {
           message = FeedMessage.parseFrom(streamContent, _extensionRegistry);
           break;
          }
        } catch (IOException e) {
          _log.error("Error parsing protocol buffer for feed={}. try={}, retry={}. Error={}",
                  feedId, tries, tries < _nTries, e.getMessage());
        }
      }

      if (message != null) {
        try {
          tripUpdates.addAll(_processor.processFeed(feedId, message));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

    }

    for (TripUpdate tu : tripUpdates) {
      FeedEntity.Builder feb = FeedEntity.newBuilder();
      feb.setTripUpdate(tu);
      feb.setId(tu.getTrip().getTripId());
      grfu.addEntity(feb.build());
    }

    _tripUpdatesSink.handleFullUpdate(grfu);
  }


}
