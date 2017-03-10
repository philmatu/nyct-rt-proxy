/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy;

import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFullUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ExtensionRegistry;
import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtimeNYCT;
import com.kurtraschke.nyctrtproxy.model.ActivatedTrip;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import com.kurtraschke.nyctrtproxy.services.TripActivator;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

/**
 *
 * @author kurt
 */
public class ProxyProvider {

  private static final org.slf4j.Logger _log = LoggerFactory.getLogger(ProxyProvider.class);

  private static final ExtensionRegistry _extensionRegistry;

  private TripActivator _tripActivator;

  private GtfsRealtimeSink _tripUpdatesSink;

  private String _key;

  private HttpClientConnectionManager _connectionManager;

  private CloseableHttpClient _httpClient;

  private ScheduledExecutorService _scheduledExecutorService;

  private ScheduledFuture _updater;

  private static final Map<Integer, Set<String>> routeBlacklistByFeed = ImmutableMap.of(1, ImmutableSet.of("D", "N", "Q"));

  private static final Set<String> routesUsingAlternateIdFormat = ImmutableSet.of("SI", "L", "N", "Q", "R", "W");

  private static final Set<String> routesNeedingFixup = ImmutableSet.of("SI", "N", "Q", "R", "W");

  private static final Map<Integer, Map<String, String>> realtimeToStaticRouteMapByFeed = ImmutableMap.of(1, ImmutableMap.of("S", "GS"));

  static {
    _extensionRegistry = ExtensionRegistry.newInstance();
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctFeedHeader);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctTripDescriptor);
    _extensionRegistry.add(GtfsRealtimeNYCT.nyctStopTimeUpdate);
  }

  @Inject
  public void setTripActivator(TripActivator tripActivator) {
    _tripActivator = tripActivator;
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
    GtfsRealtimeFullUpdate grfu = new GtfsRealtimeFullUpdate();
    Stream.of(1, 2, 11, 16/*, 21*/)
            .flatMap(feedId -> {
              URI feedUrl;

              try {
                URIBuilder ub = new URIBuilder("http://datamine.mta.info/mta_esi.php");

                ub.addParameter("key", _key);
                ub.addParameter("feed_id", feedId.toString());

                feedUrl = ub.build();
              } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
              }

              HttpGet get = new HttpGet(feedUrl);

              try {
                CloseableHttpResponse response = _httpClient.execute(get);
                try (InputStream streamContent = response.getEntity().getContent()) {
                  return processFeed(feedId, FeedMessage.parseFrom(streamContent, _extensionRegistry));
                }
              } catch (Exception ex) {
                _log.warn("Exception while fetching source feed " + feedUrl, ex);
                return Stream.<TripUpdate>empty();
              }
            })
            .map(tu -> {
              FeedEntity.Builder feb = FeedEntity.newBuilder();
              feb.setTripUpdate(tu);
              feb.setId(tu.getTrip().getTripId());
              return feb.build();
            })
            .forEach(grfu::addEntity);

    _tripUpdatesSink.handleFullUpdate(grfu);
  }

  private static NyctTripId parseTripId(String routeId, String tripId) {
    if (!routesUsingAlternateIdFormat.contains(routeId)) {
      return NyctTripId.buildFromString(tripId);
    } else {
      return NyctTripId.buildFromAlternateString(tripId);
    }
  }

  private Stream<TripUpdate> processFeed(Integer feedId, FeedMessage fm) {
    Map<String, List<GtfsRealtime.TripUpdate>> tripUpdatesByRoute = fm
            .getEntityList()
            .stream()
            .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
            .map(GtfsRealtime.FeedEntity::getTripUpdate)
            .collect(Collectors.groupingBy(tu -> {
              String routeId = tu.getTrip().getRouteId();
              return realtimeToStaticRouteMapByFeed
                      .getOrDefault(feedId, Collections.emptyMap())
                      .getOrDefault(routeId, routeId);
            }));

    return fm.getHeader()
            .getExtension(GtfsRealtimeNYCT.nyctFeedHeader)
            .getTripReplacementPeriodList()
            .stream()
            .filter(trp -> {
              return !routeBlacklistByFeed.getOrDefault(feedId, Collections.EMPTY_SET).contains(trp.getRouteId());
            })
            .flatMap(trp -> {
              Stream.Builder<GtfsRealtime.TripUpdate> tripUpdatesStreamBuilder = Stream.builder();

              GtfsRealtime.TimeRange range = trp.getReplacementPeriod();

              Date start = range.hasStart() ? new Date(range.getStart() * 1000) : new Date();
              Date end = range.hasEnd() ? new Date(range.getEnd() * 1000) : new Date();

              Set<String> routeIds = Arrays.asList(trp.getRouteId().split(", ?")).stream()
                      .map(routeId -> {
                        return realtimeToStaticRouteMapByFeed
                                .getOrDefault(feedId, Collections.emptyMap())
                                .getOrDefault(routeId, routeId);
                      }).collect(Collectors.toSet());

              Set<ActivatedTrip> staticTripsForRoute = _tripActivator.getTripsForRangeAndRoutes(start, end, routeIds)
                      .collect(Collectors.toSet());

              Set<String> matchedTripIds = new HashSet<>();

              routeIds
                      .stream()
                      .flatMap(routeId -> {
                        return tripUpdatesByRoute.getOrDefault(routeId, Collections.emptyList()).stream();
                      })
                      .forEach(tu -> {
                        TripUpdate.Builder tub = TripUpdate.newBuilder(tu);
                        TripDescriptor.Builder tb = tub.getTripBuilder();

                        tb.setRouteId(realtimeToStaticRouteMapByFeed
                                .getOrDefault(feedId, Collections.emptyMap())
                                .getOrDefault(tb.getRouteId(), tb.getRouteId()));

                        Optional<ActivatedTrip> matchedStaticTrip;

                        NyctTripId rtid = parseTripId(tb.getRouteId(), tb.getTripId());

                        if (routesNeedingFixup.contains(tb.getRouteId())) {
                          tb.setStartDate(tb.getStartDate().substring(0, 10).replace("-", ""));

                          tub.getStopTimeUpdateBuilderList().forEach(stub -> {
                            stub.setStopId(stub.getStopId() + rtid.getDirection());
                          });

                          tb.setTripId(rtid.toString());
                        }

                        Stream<ActivatedTrip> candidateTrips = staticTripsForRoute
                                .stream()
                                .filter(at -> at.getServiceDate().getAsString().equals(tb.getStartDate()))
                                .filter(at -> at.getTrip().getRoute().getId().getId().equals(tb.getRouteId()));

                        if (routesUsingAlternateIdFormat.contains(tb.getRouteId())) {
                          matchedStaticTrip = candidateTrips.filter(at -> {
                            NyctTripId stid = at.getParsedTripId();

                            return stid.getOriginDepartureTime() == rtid.getOriginDepartureTime()
                                    && stid.getDirection().equals(rtid.getDirection());
                          })
                                  .findFirst();
                        } else {
                          matchedStaticTrip = candidateTrips.filter(at -> {
                            NyctTripId stid = at.getParsedTripId();

                            return stid.getOriginDepartureTime() == rtid.getOriginDepartureTime()
                                    && stid.getPathId().equals(rtid.getPathId());
                          })
                                  .findFirst();
                        }

                        if (matchedStaticTrip.isPresent()) {
                          String staticTripId = matchedStaticTrip.get().getTrip().getId().getId();
                          matchedTripIds.add(staticTripId);
                          tb.setTripId(staticTripId);
                        } else {
                          tb.setScheduleRelationship(ScheduleRelationship.ADDED);
                        }

                        tripUpdatesStreamBuilder.accept(tub.build());
                      });

              staticTripsForRoute.stream()
                      .filter(at -> !matchedTripIds.contains(at.getTrip().getId().getId()))
                      .map(at -> {
                        TripUpdate.Builder tub = TripUpdate.newBuilder();
                        TripDescriptor.Builder tdb = tub.getTripBuilder();
                        tdb.setTripId(at.getTrip().getId().getId());
                        tdb.setRouteId(at.getTrip().getRoute().getId().getId());
                        tdb.setStartDate(at.getServiceDate().getAsString());
                        tdb.setScheduleRelationship(ScheduleRelationship.CANCELED);

                        return tub.build();
                      })
                      .forEach(tripUpdatesStreamBuilder::accept);

              return tripUpdatesStreamBuilder.build();
            }
            );
  }
}
