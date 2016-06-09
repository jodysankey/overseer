package com.jsankey.overseer;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Initiates, terminates, and monitors execution of a given command.
 * 
 * <p>This class is thread safe.
 * 
 * @author Jody
 */
public class CommandRunner {
  
  /** Logger for the current class. */
  private static final Logger LOG = Logger.getLogger(CommandRunner.class.getCanonicalName());
  /** The bit bucket as a {@link File} for convenience of redirects. */
  private static final File DEVNULL = new File("/dev/null");

  // Immutable state
  private final Clock clock;
  private final String command;
  private final ProcessBuilder builder;

  // Current execution
  @Nullable private Process process;
  @Nullable private Instant startTime;

  // Previous execution
  @Nullable private CommandEvent lastEvent; 

  private CommandRunner(String command, Clock clock) {
    this.command = command;
    // TODO(jody): We accept a complete command string in the command line arguments but
    // need to tokenize for starting the process. For now very crudely split at every space,
    // could do something better than this when we need it.
    String[] commandArray = command.split("\\s");
    this.clock = clock;

    ProcessBuilder builder = new ProcessBuilder(commandArray)
        .redirectInput(DEVNULL)
        .redirectOutput(DEVNULL)
        .redirectError(DEVNULL);

    this.builder = builder;
    this.process = null;
    this.startTime = null;
    this.lastEvent = null;
  }

  /**
   * Constructs a new {@link CommandRunner} using the system clock.
   * 
   * @param command the command to be run
   * @return a new {@link CommandRunner} instance
   */
  public static CommandRunner forCommand(String command) {
    return new CommandRunner(command, Clock.systemUTC());
  }

  /**
   * Constructs a new {@link CommandRunner} with the supplied clock.
   * 
   * @param command the command to be run
   * @param clock the clock to use for reading time
   * @return a new {@link CommandRunner} instance
   */
  @VisibleForTesting
  static CommandRunner forCommand(String command, Clock clock) {
    return new CommandRunner(command, clock);
  }

  /**
   * Returns the {@link Clock} this instance uses to read time.
   */
  public Clock getClock() {
    return clock;
  }

  /**
   * Returns the command string this instance will run.
   */
  public String getCommand() {
    return command;
  }
  
  /**
   * Begins execution of the command then returns immediately.
   * 
   * <p>Preconditions: The command is not already executing.
   */
  public synchronized void start() {
    Preconditions.checkState(process == null, "Command is already running");  
    startTime = clock.instant();
    try {
      LOG.info("Starting command: " + command);
      process = builder.start();
    } catch (IOException e) {
      LOG.warning("Command failed initialization: " + command);
      lastEvent = new CommandEvent(startTime, clock.instant(), CommandEvent.COULD_NOT_START);
      process = null;
    }
  }
  
  /**
   * Terminates execution of the command if it is still running.
   */
  public synchronized void terminate() {
    if (process !=null) {
      process.destroy();
      LOG.warning("Terminating command: " + command);
      lastEvent = new CommandEvent(startTime, clock.instant(), CommandEvent.ENFORCED_TERMINATION);
      process = null;
    }
  }
  
  /**
   * Returns true iff the command is currently executing.
   * 
   * <p>Note that polling this method periodically is used to measure when execution of a command
   * completes without using a dedicated checker thread. Calling with higher frequency increases the
   * accuracy of completion times.
   */
  public synchronized boolean isRunning() {
    if (process != null) {
      try {
        int exitCode = process.exitValue();
        // If we didn't throw an exception the command must now be finished
        lastEvent = new CommandEvent(startTime, clock.instant(), exitCode);
        process = null;
        if (lastEvent.isSuccessful()) {
          LOG.info(String.format(
              "Detected completion of command: %s (%.2f sec)",
              command, lastEvent.getDurationMillis()/1000f));
        } else {
          LOG.warning(String.format(
              "Detected failure of command with exit code %d: %s (%.2f sec)",
              lastEvent.getExitCode(), command, lastEvent.getDurationMillis()/1000f));
        }
      } catch (IllegalThreadStateException e) {
        // Normal that if the process is not done yet we receive this Exception.
        return true;
      }
    }
    return false;
  }
  
  /**
   * Returns a {@link CommandEvent} representing the most recent completed execution.
   * 
   * <p>Preconditions:</p>
   * <ul>The command is not currently executing</ul>
   * <ul>One or more executions have been performed</ul>
   */
  public synchronized CommandEvent getLastExecution() {
    Preconditions.checkState(process == null, "Cannot get last execution while command is running");  
    Preconditions.checkState(lastEvent != null, "Cannot get last execution before completion");
    return lastEvent;
  }

}
