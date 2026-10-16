/*
 * Copyright (C) 2026 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.history;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Iterator;

import org.junit.Test;

public class CommandHistoryTest {

  private static final String COMMAND = "fake_command";
  private static final Instant T1 = Instant.ofEpochSecond(33330001);
  private static final Instant T2 = Instant.ofEpochSecond(33330002);
  private static final Instant T3 = Instant.ofEpochSecond(33330003);
  private static final Instant T4 = Instant.ofEpochSecond(33330004);

  @Test
  public void testEmpty() {
    CommandHistory history = new CommandHistory(COMMAND);
    assertThat(history.getCommand()).isEqualTo(COMMAND);
    assertThat(history).isEmpty();
    assertThat(history.getLast()).isNull();
  }

  @Test
  public void testGetLast() {
    CommandHistory history = new CommandHistory(COMMAND);
    CommandEvent first = new CommandEvent(T1, T2, 0);
    CommandEvent second = new CommandEvent(T3, T4, 1);
    history.add(first);
    history.add(second);
    assertThat(history).containsExactly(first, second).inOrder();
    assertThat(history.getLast()).isSameInstanceAs(second);
  }

  @Test
  public void testOutOfOrderEventIgnored() {
    CommandHistory history = new CommandHistory(COMMAND);
    CommandEvent later = new CommandEvent(T3, T4, 0);
    assertThat(history.add(later)).isTrue();
    assertThat(history.add(new CommandEvent(T1, T2, 0))).isFalse();
    assertThat(history).containsExactly(later);
  }

  @Test
  public void testAddDuringIterationUsesSnapshot() {
    CommandHistory history = new CommandHistory(COMMAND);
    CommandEvent first = new CommandEvent(T1, T2, 0);
    history.add(first);

    Iterator<CommandEvent> iterator = history.iterator();
    history.add(new CommandEvent(T3, T4, 0));

    // The iterator should neither throw nor see the new event.
    assertThat(iterator.next()).isSameInstanceAs(first);
    assertThat(iterator.hasNext()).isFalse();
    assertThat(history).hasSize(2);
  }
}
