/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kurtraschke.nyctrtproxy;

import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeExporter;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeFileWriter;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeServlet;
import org.onebusaway.guice.jsr250.LifecycleService;

import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.inject.name.Names;

import org.nnsoft.guice.rocoto.Rocoto;
import org.nnsoft.guice.rocoto.configuration.ConfigurationModule;
import org.nnsoft.guice.rocoto.converters.FileConverter;
import org.nnsoft.guice.rocoto.converters.PropertiesConverter;
import org.nnsoft.guice.rocoto.converters.URLConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 *
 * @author kurt
 */
public class Main {

  private static final Logger _log = LoggerFactory.getLogger(Main.class);

  @Inject
  @SuppressWarnings("unused")
  private ProxyProvider _provider;

  @Inject
  private LifecycleService _lifecycleService;

  @Inject
  @TripUpdates
  GtfsRealtimeExporter _tripUpdatesExporter;

  private static final String ARG_CONFIG_FILE = "config";

  private Injector _injector;

  public static void main(String[] args) throws IOException {
    Main m = new Main();

    ArgumentParser parser = ArgumentParsers.newArgumentParser("nyct-rt-proxy");
    parser.description("Produces a GTFS-realtime feed from the Washington State Ferries API");
    parser.addArgument("--" + ARG_CONFIG_FILE).type(File.class).help("configuration file path");
    Namespace parsedArgs;

    try {
      parsedArgs = parser.parseArgs(args);
      File configFile = parsedArgs.get(ARG_CONFIG_FILE);
      m.run(configFile);
    } catch (CreationException | ConfigurationException | ProvisionException e) {
      _log.error("Error in startup:", e);
      System.exit(-1);
    } catch (ArgumentParserException ex) {
      parser.handleError(ex);
    }

  }

  public void run(File configFile) {
    Set<Module> modules = new HashSet<>();
    ProxyModule.addModuleAndDependencies(modules);
    _injector = Guice.createInjector(
            new URLConverter(),
            new FileConverter(),
            new PropertiesConverter(),
            new ConfigurationModule() {
      @Override
      protected void bindConfigurations() {
        bindSystemProperties();

        if (configFile != null) {
          bindProperties(configFile);
        }
      }
    },
            Rocoto.expandVariables(modules));

    _injector.getMembersInjector(Main.class).injectMembers(this);

    configureExporter(getConfigurationValue(URL.class, "tripUpdates.url"),
            getConfigurationValue(File.class, "tripUpdates.path"),
            _tripUpdatesExporter);

    _lifecycleService.start();
  }

  private <T> T getConfigurationValue(Class<T> type, String configurationKey) {
    try {
      return _injector.getInstance(Key.get(type, Names.named(configurationKey)));
    } catch (ConfigurationException e) {
      return null;
    }
  }

  private void configureExporter(URL feedUrl, File feedPath, GtfsRealtimeExporter exporter) {
    if (feedUrl != null) {
      GtfsRealtimeServlet servlet = _injector.getInstance(GtfsRealtimeServlet.class);
      servlet.setUrl(feedUrl);
      servlet.setSource(exporter);
    }

    if (feedPath != null) {
      GtfsRealtimeFileWriter writer = _injector.getInstance(GtfsRealtimeFileWriter.class);
      writer.setPath(feedPath);
      writer.setSource(exporter);
    }
  }

}
