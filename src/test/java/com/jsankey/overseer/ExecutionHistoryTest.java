package com.jsankey.overseer;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.jsankey.overseer.ExecutionHistory.HistoryStatus;

public class ExecutionHistoryTest {
  
  private static final String COMMAND_1 = "fake_command_one";
  private static final String COMMAND_2 = "fake_command_two";
  private static final Instant T1 = Instant.ofEpochSecond(44440001);
  private static final Instant T2 = Instant.ofEpochSecond(44440002);
  private static final Instant T3 = Instant.ofEpochSecond(44440003);
  private static final Instant T4 = Instant.ofEpochSecond(44440004);
  private static final Instant T5 = Instant.ofEpochSecond(44440005);
  
  private static final int SUCCESS_CODE = 0;
  private static final int FAILURE_CODE = 99;
  
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testCommandSuccess() {
    ExecutionHistory history = createTwoCommandHistory();
    history.recordEvent(COMMAND_1, T1, T2, FAILURE_CODE);
    history.recordEvent(COMMAND_1, T2, T3, SUCCESS_CODE);
    history.recordEvent(COMMAND_2, T1, T2, SUCCESS_CODE);
    assertThat(history.getStatus()).isEqualTo(HistoryStatus.ALL_PASSED);
    assertThat(history.getStatusTime()).isEqualTo(Optional.of(T2));
  }
   
  @Test
  public void testCommandNotRun() {
    ExecutionHistory history = createTwoCommandHistory();
    history.recordEvent(COMMAND_2, T4, T5, SUCCESS_CODE);
    assertThat(history.getStatus()).isEqualTo(HistoryStatus.NOT_ALL_RUN);
    assertThat(history.getStatusTime()).isEqualTo(Optional.absent());
  }

  @Test
  public void testCommandFailure() {
    ExecutionHistory history = createTwoCommandHistory();
    history.recordEvent(COMMAND_1, T1, T2, FAILURE_CODE);
    history.recordEvent(COMMAND_2, T3, T4, SUCCESS_CODE);
    assertThat(history.getStatus()).isEqualTo(HistoryStatus.FAILED);
    assertThat(history.getStatusTime()).isEqualTo(Optional.of(T2));
  }

  @Test
  public void testOutOfSequenceCommandDiscarded() {
    ExecutionHistory history = createTwoCommandHistory();
    history.recordEvent(COMMAND_1, T3, T4, SUCCESS_CODE);
    history.recordEvent(COMMAND_2, T3, T4, SUCCESS_CODE);
    history.recordEvent(COMMAND_2, T2, T3, SUCCESS_CODE);
    assertThat(history.getStatus()).isEqualTo(HistoryStatus.ALL_PASSED);
    assertThat(history.getStatusTime()).isEqualTo(Optional.of(T4));
  }
 
  @Test
  public void testDiscardingOldHistoryEntries() {
    ExecutionHistory history = createOneCommandHistory();
    for (int i = 0; i < ExecutionHistory.MAX_HISTORY_SIZE + 1; i++) {
      history.recordEvent(
          COMMAND_1, T1.plus(i, ChronoUnit.SECONDS), T2.plus(i, ChronoUnit.SECONDS), SUCCESS_CODE);
    }
    assertThat(history.getStatus()).isEqualTo(HistoryStatus.ALL_PASSED);
    assertThat(history.getStatusTime())
        .isEqualTo(Optional.of(T2.plus(ExecutionHistory.MAX_HISTORY_SIZE, ChronoUnit.SECONDS)));
  }
  
  @Test
  public void testRestorationFromFile() {
    Path configPath = Paths.get(tempFolder.getRoot().getAbsolutePath(), "test-history.cfg");
    Configuration initialConfig = Configuration.from(new String[]{
        "--status_file", configPath.toString(), "--command", COMMAND_1, "--command", COMMAND_2});
    ExecutionHistory initialHistory = ExecutionHistory.from(initialConfig);
    assertThat(initialHistory.getStatus()).isEqualTo(HistoryStatus.NOT_ALL_RUN);
    initialHistory.recordEvent(COMMAND_1, T1, T2, FAILURE_CODE);
    initialHistory.recordEvent(COMMAND_2, T2, T4, SUCCESS_CODE);
    assertThat(initialHistory.getStatus()).isEqualTo(HistoryStatus.FAILED);

    // Note this time we only have one of the commands, the passing one.
    Configuration restoredConfig = Configuration.from(new String[]{
        "--status_file", configPath.toString(), "--command", COMMAND_2});
    ExecutionHistory restoredHistory = ExecutionHistory.from(restoredConfig);
    assertThat(restoredHistory.getStatus()).isEqualTo(HistoryStatus.ALL_PASSED);
    assertThat(restoredHistory.getStatusTime()).isEqualTo(Optional.of(T4));
  }

  private static ExecutionHistory createOneCommandHistory() {
    return new ExecutionHistory(Optional.<String>absent(), ImmutableList.of(COMMAND_1));
  }

  private static ExecutionHistory createTwoCommandHistory() {
    return new ExecutionHistory(Optional.<String>absent(), ImmutableList.of(COMMAND_1, COMMAND_2));
  }

}
