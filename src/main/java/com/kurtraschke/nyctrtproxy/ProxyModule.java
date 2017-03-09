/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy;

import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeExporterModule;
import org.onebusaway.guice.jsr250.JSR250Module;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.kurtraschke.nyctrtproxy.services.CalendarServiceDataProvider;
import com.kurtraschke.nyctrtproxy.services.GtfsRelationalDaoProvider;

import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 *
 * @author kurt
 */
public class ProxyModule extends AbstractModule {

  public static void addModuleAndDependencies(Set<Module> modules) {
    GtfsRealtimeExporterModule.addModuleAndDependencies(modules);
    JSR250Module.addModuleAndDependencies(modules);
    modules.add(new ProxyModule());
  }

  @Override
  protected void configure() {
    bind(HttpClientConnectionManager.class)
            .toInstance(new PoolingHttpClientConnectionManager());

    bind(ScheduledExecutorService.class)
            .toInstance(Executors.newSingleThreadScheduledExecutor());

    bind(CalendarServiceData.class)
            .toProvider(CalendarServiceDataProvider.class)
            .in(Scopes.SINGLETON);

    bind(GtfsRelationalDao.class)
            .toProvider(GtfsRelationalDaoProvider.class)
            .in(Scopes.SINGLETON);

  }

  /**
   * Implement hashCode() and equals() such that two instances of the module
   * will be equal.
   */
  @Override
  public int hashCode() {
    return this.getClass().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    return this.getClass().equals(o.getClass());
  }
}
