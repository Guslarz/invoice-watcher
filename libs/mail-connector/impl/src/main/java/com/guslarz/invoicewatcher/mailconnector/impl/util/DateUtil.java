package com.guslarz.invoicewatcher.mailconnector.impl.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class DateUtil {

  public static Date toDate(ZonedDateTime dateTime) {
    return Date.from(dateTime.toInstant());
  }

  public static ZonedDateTime toZonedDateTime(Date date) {
    return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC"));
  }
}
