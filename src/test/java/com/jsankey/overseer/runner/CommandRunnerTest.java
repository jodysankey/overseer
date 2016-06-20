/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.runner;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.jsankey.overseer.history.CommandEvent;
import com.jsankey.overseer.runner.CommandRunner;

public class CommandRunnerTest {

  private static final String TEST_COMMAND = "false ignored argument";
  private static final String INFINITE_COMMAND = "tail -F /dev/null";

  private static final Instant T1 = Instant.ofEpochSecond(55550001);
  private static final Instant T2 = Instant.ofEpochSecond(55550002);

  private CommandRunner testObject;
  private Clock mockClock;

  @Before
  public void setUp() {
    mockClock = mock(Clock.class);
    testObject = CommandRunner.forCommand(TEST_COMMAND, mockClock);
  }

  @Test
  public void testImmutableProperties() {
    assertThat(testObject.getCommand()).isEqualTo(TEST_COMMAND);
    assertThat(testObject.getClock()).isSameAs(mockClock);
  }

  @Test
  public void testNotYetRun() {
    assertThat(testObject.isRunning()).isFalse();
    try {
      testObject.getLastExecution();
      fail();
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void testExecution() throws Exception {
    when(mockClock.instant()).thenReturn(T1, T2);
    testObject.start();

    // Allow time for false to complete
    TimeUnit.MILLISECONDS.sleep(500);
    assertThat(testObject.isRunning()).isFalse();

    CommandEvent lastExecution = testObject.getLastExecution();
    assertThat(lastExecution.getExitCode()).isEqualTo(1);
    assertThat(lastExecution.getStart()).isEqualTo(T1);
    assertThat(lastExecution.getEnd()).isEqualTo(T2);
  }

  @Test
  public void testTermination() throws Exception {
    when(mockClock.instant()).thenReturn(T1, T2);
    testObject = CommandRunner.forCommand(INFINITE_COMMAND, mockClock);
    testObject.start();

    TimeUnit.MILLISECONDS.sleep(250);
    assertThat(testObject.isRunning()).isTrue();
    testObject.terminate();
    TimeUnit.MILLISECONDS.sleep(250);
    assertThat(testObject.isRunning()).isFalse();

    CommandEvent lastExecution = testObject.getLastExecution();
    assertThat(lastExecution.getExitCode()).isEqualTo(CommandEvent.ENFORCED_TERMINATION);
    assertThat(lastExecution.getStart()).isEqualTo(T1);
  }
}
