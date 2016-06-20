/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.history;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jsankey.overseer.Configuration;

/**
 * Defines a history of the previous command executions in terms of the command, return code,
 * time of execution, and duration. This class is threadsafe.
 *
 * @author Jody
 */
public class ExecutionHistory implements Serializable, Iterable<CommandHistory> {

  private static final long serialVersionUID = -91035340473517723L;

  /** Logger for the current class. */
  private static final Logger LOG = Logger.getLogger(ExecutionHistory.class.getCanonicalName());

  public enum HistoryStatus {
    /** All commands have successfully completed on their last execution */
    ALL_PASSED,
    /** Some commands have not yet executed (but not have failed on their last execution) */
    NOT_ALL_RUN,
    /** One or more commands failed on their last execution */
    FAILED
  }

  /** Path in which we attempt to store and recover the execution history. */
  private Optional<String> filePath;
  /** Map of the {@link ExecutionEvent} lists for each command */
  private ImmutableMap<String, CommandHistory> historyMap;
  /** Cached calculated overall status */
  private HistoryStatus status;
  /** Cached calculated time of oldest last-start */
  private Optional<Instant> oldestStart;
  /** Cached calculated time of most recent failure */
  private Optional<Instant> newestFailure;

  /**
   * Constructs a new instance from the supplied status file and commands, attempting to initialize
   * from the status file references if possible.
   *
   * @param filePath the path in which a config file should be stored
   * @param commands an {@link ImmutableList} of the commands whose history will be tracked
   */
  @VisibleForTesting
  public ExecutionHistory(Optional<String> filePath, ImmutableList<String> commands) {
    this.filePath = filePath;

    // Construct a valid clean history map.
    ImmutableMap.Builder<String, CommandHistory> historyMapBuilder = ImmutableMap.builder();
    for (String command : commands) {
      historyMapBuilder.put(command, new CommandHistory(command));
    }
    historyMap = historyMapBuilder.build();

    // If possible read another object from the file, and merge in its results.
    if (filePath.isPresent()) {
      try {
        ExecutionHistory recovered = readFromFile();
        for (String command : commands) {
          if (recovered.historyMap.containsKey(command)) {
            historyMap.get(command).addAll(recovered.historyMap.get(command));
          } else {
            LOG.info(String.format(
                "Could not find command to restore from previous execution history: ", command));
          }
        }
      } catch (FileNotFoundException e) {
        LOG.info(String.format(
            "Status file %s did not exist to recover execution history", filePath.get()));
      } catch (ClassNotFoundException | IOException e) {
        LOG.log(Level.WARNING, "Error reading execution history", e);
      }
    }

    recalculateSummaryState();
  }

  /**
   * Constructs a new {@link ExecutionHistory} from the supplied configuration, attempting to
   * initialize from the status file this references if possible.
   *
   * @param config a {@link Configuration} used for initialization
   * @return a new {@link ExecutionHistory} instance
   */
  public static ExecutionHistory from(Configuration config) {
    return new ExecutionHistory(config.getStatusFile(), config.getCommands());
  }

  /**
   * Records the successful or unsuccessful completion of a command in the command history,
   * updating the history file if one was specified.
   *
   * @param command the command that was run
   * @param start the {@link Instant} at which execution started
   * @param end the [approximate] {@link Instant} at which execution ended.
   * @param exitCode the exit code returned upon completion.
   */
  public synchronized void recordEvent(String command, Instant start, Instant end, int exitCode) {
    recordEvent(command, new CommandEvent(start, end, exitCode));
  }

  /**
   * Records the successful or unsuccessful completion of a command in the command history,
   * updating the history file if one was specified.
   *
   * @param command the command that was run
   * @param event a {@link CommandEvent} describing the execution times and result
   */
  public synchronized void recordEvent(String command, CommandEvent event) {
    CommandHistory history = historyMap.get(command);
    Preconditions.checkNotNull(history, "Asked to record event for unknown command " + command);
    history.add(event);
    recalculateSummaryState();

    // If possible, save our new state to disk.
    if (filePath.isPresent()) {
      try {
        writeToFile();
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Error writing execution history", e);
      }
    }
  }

  /**
   * Returns an immutable copy of the execution history for the specified command.
   */
  public synchronized ImmutableList<CommandEvent> getCommandHistory(String command) {
    CommandHistory history = historyMap.get(command);
    Preconditions.checkNotNull(history, "Asked to return history for unknown command " + command);
    return ImmutableList.copyOf(history);
  }

  /**
   * Returns the overall status of command execution. See {@link HistoryStatus} for more details.
   */
  public HistoryStatus getStatus() {
    return status;
  }

  /**
   * Returns the oldest {@link Instant} at which the most recent execution of any command started.
   */
  public Optional<Instant> getOldestStart() {
    return oldestStart;
  }

  /**
   * Returns the most recent {@link Instant} of a failed execution, if any commands failed on their
   * most recent execution, or absent otherwise.
   */
  public Optional<Instant> getNewestFailure() {
    return newestFailure;
  }

  /**
   * Recalculates the {@code status} and {@code statusTime} fields.
   */
  private synchronized void recalculateSummaryState() {
    Instant newestFailure = Instant.MIN;
    Instant oldestStart = Instant.MAX;
    status = HistoryStatus.ALL_PASSED;

    for (CommandHistory commandHistory : historyMap.values()) {
      if (commandHistory.isEmpty()) {
        if (status == HistoryStatus.ALL_PASSED) {
          status = HistoryStatus.NOT_ALL_RUN;
        }
      } else {
        CommandEvent lastEvent = commandHistory.getLast();
        if (lastEvent.getStart().isBefore(oldestStart)) {
          oldestStart = lastEvent.getStart();
        }
        if (!lastEvent.isSuccessful()) {
          status = HistoryStatus.FAILED;
          if (lastEvent.getEnd().isAfter(newestFailure)) {
            newestFailure = lastEvent.getEnd();
          }
        }
      }
    }

    this.oldestStart = status != HistoryStatus.NOT_ALL_RUN && oldestStart.isBefore(Instant.MAX)
        ? Optional.of(oldestStart) : Optional.<Instant>absent();
    this.newestFailure = newestFailure.isAfter(Instant.MIN)
        ? Optional.of(newestFailure) : Optional.<Instant>absent();
  }

  /**
   * Write the current object out to the history filePath.
   *
   * <p>Preconditions: {@code filePath} has been specified.
   */
  private synchronized void writeToFile() throws IOException {
    Preconditions.checkArgument(filePath.isPresent());
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath.get()))) {
      oos.writeObject(this);
    }
  }

  /**
   * Reads a new object from the history filePath in the current object. Note that the returned
   * object will not have its transient fields populated so should not be made available externally.
   *
   * <p>Preconditions: {@code filePath} has been specified.
   *
   * @return the reconstructed {@link ExecutionHistory}
   * @throws IOException 
   * @throws FileNotFoundException 
   * @throws ClassNotFoundException 
   */
  private synchronized ExecutionHistory readFromFile()
      throws FileNotFoundException, IOException, ClassNotFoundException {
    Preconditions.checkArgument(filePath.isPresent());
    ExecutionHistory reconstructed;
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath.get()))) {
      reconstructed = (ExecutionHistory)ois.readObject();
    }
    return reconstructed;
  }

  @Override
  public Iterator<CommandHistory> iterator() {
    return historyMap.values().iterator();
  }
}
