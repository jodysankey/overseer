/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.util;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Simple single line text output logger
 */
public class BriefTextFormatter extends Formatter {

  @Override
  public String format(LogRecord rec) {
    String className =
        rec.getSourceClassName().substring(rec.getSourceClassName().lastIndexOf(".") + 1);
    String throwable = (rec.getThrown() == null) ? "" : String.format("%s%n", rec.toString());
    return String.format("%1$tY-%1$tm-%1$td %1$tT %2$s [%3$s] %4$s%n%s",
        rec.getMillis(), rec.getLevel().getName(), className, rec.getMessage(), throwable);
  }
}
