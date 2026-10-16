/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Simple single line text output logger
 */
public class BriefTextFormatter extends Formatter {

  @Override
  public String format(LogRecord rec) {
    StringWriter output = new StringWriter();
    try (PrintWriter writer = new PrintWriter(output)) {
      writer.format("%1$tY-%1$tm-%1$td %1$tT ", rec.getMillis());
      writer.format(
          "%s [%s] %s%n", rec.getLevel().getName(), unqualifiedClass(rec), formatMessage(rec));
      if (rec.getThrown() != null) {
        rec.getThrown().printStackTrace(writer);
      }
    }
    return output.toString();
  }

  /**
   * Returns the unqualified name of the class that issued the log, falling back to the logger
   * name if the source class could not be determined.
   */
  private static String unqualifiedClass(LogRecord rec) {
    String name = rec.getSourceClassName() != null ? rec.getSourceClassName() : rec.getLoggerName();
    if (name == null) {
      return "unknown";
    }
    return name.substring(name.lastIndexOf(".") + 1);
  }
}
