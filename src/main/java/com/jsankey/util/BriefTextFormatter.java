/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Simple single line text output logger
 */
public class BriefTextFormatter extends Formatter {

  @Override
  public String format(LogRecord rec) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (PrintStream printStream = new PrintStream(outputStream)) {
      printStream.format("%1$tY-%1$tm-%1$td %1$tT ", rec.getMillis());
      printStream.format(
          "%s [%s] %s%n", rec.getLevel().getName(), unqualifiedClass(rec), rec.getMessage());
      if (rec.getThrown() != null) {
        rec.getThrown().printStackTrace(printStream);
      }
    }
    return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
  }

  private static String unqualifiedClass(LogRecord rec) {
    return rec.getSourceClassName().substring(rec.getSourceClassName().lastIndexOf(".") + 1);
  }
}
