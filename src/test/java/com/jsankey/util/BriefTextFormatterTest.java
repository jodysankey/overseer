/*
 * Copyright (C) 2026 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Test;

public class BriefTextFormatterTest {

  private static final String TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} ";

  private final BriefTextFormatter formatter = new BriefTextFormatter();

  @Test
  public void testSimpleMessage() {
    LogRecord rec = createRecord(Level.INFO, "Hello there");
    rec.setSourceClassName("com.example.SomeClass");
    assertThat(formatter.format(rec))
        .matches(TIMESTAMP_REGEX + "INFO \\[SomeClass\\] Hello there" + System.lineSeparator());
  }

  @Test
  public void testParametersSubstituted() {
    LogRecord rec = createRecord(Level.WARNING, "Value {0} and {1}");
    rec.setSourceClassName("com.example.SomeClass");
    rec.setParameters(new Object[] {"one", "two"});
    assertThat(formatter.format(rec)).contains("WARNING [SomeClass] Value one and two");
  }

  @Test
  public void testExceptionAppended() {
    LogRecord rec = createRecord(Level.SEVERE, "Failed");
    rec.setSourceClassName("com.example.SomeClass");
    rec.setThrown(new IllegalStateException("broken thing"));
    String output = formatter.format(rec);
    assertThat(output).contains("SEVERE [SomeClass] Failed" + System.lineSeparator());
    assertThat(output).contains("java.lang.IllegalStateException: broken thing");
    assertThat(output).contains("BriefTextFormatterTest.testExceptionAppended");
  }

  @Test
  public void testNullSourceClassUsesLoggerName() {
    LogRecord rec = createRecord(Level.INFO, "Message");
    rec.setSourceClassName(null);
    rec.setLoggerName("com.example.LoggerName");
    assertThat(formatter.format(rec)).contains("INFO [LoggerName] Message");
  }

  @Test
  public void testNullSourceClassAndLoggerName() {
    LogRecord rec = createRecord(Level.INFO, "Message");
    rec.setSourceClassName(null);
    rec.setLoggerName(null);
    assertThat(formatter.format(rec)).contains("INFO [unknown] Message");
  }

  private static LogRecord createRecord(Level level, String message) {
    LogRecord rec = new LogRecord(level, message);
    rec.setMillis(1234567890123L);
    return rec;
  }
}
