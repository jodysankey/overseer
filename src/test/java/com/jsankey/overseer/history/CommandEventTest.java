/*
 * Copyright (C) 2026 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.history;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import java.time.Instant;

import org.junit.Test;

public class CommandEventTest {

  private static final Instant T1 = Instant.ofEpochMilli(1000000L);
  private static final Instant T2 = Instant.ofEpochMilli(1002500L);

  @Test
  public void testProperties() {
    CommandEvent event = new CommandEvent(T1, T2, 3);
    assertThat(event.getStart()).isEqualTo(T1);
    assertThat(event.getEnd()).isEqualTo(T2);
    assertThat(event.getExitCode()).isEqualTo(3);
    assertThat(event.getDurationMillis()).isEqualTo(2500L);
  }

  @Test
  public void testSuccess() {
    assertThat(new CommandEvent(T1, T2, 0).isSuccessful()).isTrue();
    assertThat(new CommandEvent(T1, T2, 1).isSuccessful()).isFalse();
    assertThat(new CommandEvent(T1, T2, CommandEvent.COULD_NOT_START).isSuccessful()).isFalse();
    assertThat(new CommandEvent(T1, T2, CommandEvent.ENFORCED_TERMINATION).isSuccessful())
        .isFalse();
  }

  @Test
  public void testEqualStartAndEndAccepted() {
    CommandEvent event = new CommandEvent(T1, T1, 0);
    assertThat(event.getDurationMillis()).isEqualTo(0L);
  }

  @Test
  public void testEndBeforeStartRejected() {
    try {
      new CommandEvent(T2, T1, 0);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }
}
