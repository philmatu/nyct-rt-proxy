package com.kurtraschke.nyctrtproxy.util;

import com.google.transit.realtime.GtfsRealtime;
import com.kurtraschke.nyctrtproxy.model.NyctTripId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.OptionalLong;
import java.util.Set;

public class NycRealtimeUtil {

  private static final Logger _log = LoggerFactory.getLogger(NycRealtimeUtil.class);

  public static String fixedStartDate(GtfsRealtime.TripDescriptorOrBuilder td) {
    return td.getStartDate().substring(0, 10).replace("-", "");
  }

  public static Date earliestTripStart(Set<String> routesNeedingFixup, Collection<GtfsRealtime.TripUpdate> updates) {
    OptionalLong time = updates.stream()
            .mapToLong(tu -> tripUpdateStart(routesNeedingFixup, tu))
            .filter(n -> n > 0).min();
    return time.isPresent() ? new Date(time.getAsLong()) : null;
  }

  // take a TripUpdate and return the epoch time in millis that this trip started
  private static long tripUpdateStart(Set<String> routesNeedingFixup, GtfsRealtime.TripUpdate tu) {
    GtfsRealtime.TripDescriptor td = tu.getTrip();
    NyctTripId rtid = NyctTripId.buildFromString(td.getTripId());
    if (rtid == null)
      return -1;
    int minHds = rtid.getOriginDepartureTime();
    String startDate = routesNeedingFixup.contains(td.getRouteId()) ? fixedStartDate(td) : td.getStartDate();
    ServiceDate sd;
    try {
      sd = ServiceDate.parseString(startDate);
    } catch (ParseException e) {
      _log.error("Error parsing trip update={}, exception={}", tu, e);
      return Long.MAX_VALUE;
    }
    return sd.getAsDate().getTime() + (minHds * 600); // 600 millis in 1/100 minute
  }
}
