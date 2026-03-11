package com.orasa.backend.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import com.orasa.backend.config.TimeConfig;

public class DateTimeUtils {

  public static final DateTimeFormatter DATE_TIME_FORMATTER = 
      DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

  private DateTimeUtils() {
    // Utility class
  }

  public static String formatDateTime(OffsetDateTime dateTime) {
    if (dateTime == null) return "(not set)";
    return dateTime.atZoneSameInstant(TimeConfig.PH_ZONE).format(DATE_TIME_FORMATTER);
  }
}
