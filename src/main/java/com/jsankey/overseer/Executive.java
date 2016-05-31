package com.jsankey.overseer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jsankey.overseer.ExecutionHistory.HistoryStatus;

/**
 * Master class that handles running each command at an appropriate time and
 * based on a configuration, ecording the outcomes of these executions, and calculating
 * an overall state for the system.
 * 
 * <p>The class creates its own thread to do the work, and may safely be called
 * from other threads.
 * 
 * @author Jody
 */
public class Executive {

  private static final int SMALL_TIMEOUT_MILLIS = 250;
  private static final int MEDIUM_TIMEOUT_MILLIS = 2000;
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  /** Logger for the current class. */
  private static final Logger LOG = Logger.getLogger(Executive.class.getCanonicalName());

  private final Configuration config;
  private final ImmutableList<CommandRunner> commands;
  private final ExecutionHistory history;
  private final Clock clock;
  
  private @Nullable Thread runnerThread;
  private @Nullable CommandRunner activeCommand;
  private Instant nextStart;
  
  public enum Status {
    /** A command is currently being executed */
    RUNNING,
    /** The most recent run was successful, and we're waiting until it's time to run again */
    IDLE,
    /** The most recent run was successful, but we're not on the required wifi SSID to run again */
    BLOCKED_ON_WIFI,
    /** The most recent run included one or more failures */
    FAILURE,
    /** The executive has been terminated, no more runs will be performed */
    TERMINATED
  }

  /**
   * Constructs a new {@link Executive} from the supplied configuration.
   * 
   * @param config a {@link Configuration} used for initialization
   */
  private Executive(Configuration config) {
    this.config = config;
    this.history = ExecutionHistory.from(config);
    this.clock = Clock.systemUTC();
    
    ImmutableList.Builder<CommandRunner> runnerBuilder = ImmutableList.builder();
    for (String command : config.getCommands()) {
      runnerBuilder.add(CommandRunner.forCommand(command));
    }
    this.commands = runnerBuilder.build();
    
    this.nextStart = clock.instant();
  }

  /**
   * Constructs a new {@link Executive} from the supplied configuration.
   * 
   * @param config a {@link Configuration} used for initialization
   */
  public static Executive from(Configuration config) {
    return new Executive(config);
  }

  /**
   * Returns the {@link Configuration} this object was constructed for.
   */
  public Configuration getConfig() {
    return config;
  }

  /**
   * Returns the {@link ExecutionHistory} recording previous runs.
   */
  public ExecutionHistory getHistory() {
    return history;
  }
  
  /**
   * Returns the overall status of execution.
   */
  public synchronized Status getStatus() {
    if (runnerThread == null) {
      return Status.TERMINATED;
    } else if (activeCommand != null) {
      return Status.RUNNING;
    } else if (history.getStatus() == HistoryStatus.FAILED) {
      return Status.FAILURE;
    } else {
      return Status.IDLE;
      //TODO: Blocked on Wifi
    }
  }
  
  /**
   * Returns the command that is currently running, if any.
   */
  public synchronized Optional<String> getActiveCommand() {
    if (activeCommand == null) {
      return Optional.absent();
    } else {
      return Optional.of(activeCommand.toString());
    }
  }

  /**
   * Start a new execution of the commands immediately, unless one is already in progress or
   * unless WiFi state currently prevents execution. 
   */
  public synchronized void runNow() {
    LOG.info("Scheduling next start as current time by request");
    nextStart = this.clock.instant();
  }

  /**
   * Begins execution for the first time.
   */
  public synchronized void start() {
    Preconditions.checkState(runnerThread == null, "Cannot start running executive");
    runnerThread = new Thread(this.new ExecutiveRunner());
    runnerThread.start();
  }
 
  /**
   * Terminates any currently executing command immediately, killing the thread, releasing
   * resources, and preventing any further interaction with the object.
   * @throws InterruptedException 
   */
  public void terminate() throws InterruptedException {
    Preconditions.checkState(runnerThread != null, "Cannot terminate already terminated executive");
    // Note deliberately don't synchronize. We only communicate with the other
    // thread via an atomically set reference, and don't want to deadlock.
    runnerThread.interrupt();
    while (runnerThread != null) {
      Thread.sleep(SMALL_TIMEOUT_MILLIS);
    }
  }
  
  /**
   * Inner class to handle the actual execution on a dedicated thread.
   */
  private class ExecutiveRunner implements Runnable {

    @Override
    public void run() {
      try {
        setNextStart();
        while (!Thread.currentThread().isInterrupted()) {
          waitUntilNextStart();
          runCommandSet();
          setNextStart();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      //Communicate back to the parent that we stopped by clearing its reference.
      runnerThread = null;
    }
     
    /**
     * Waits until at least next start time.
     */
    private void waitUntilNextStart() throws InterruptedException {
      // Note: The nextStart time may move forwards by a manual start request, hence do frequent
      // checks rather than one big long sleep to the correct time.
      while (clock.instant().isBefore(nextStart)) {
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
        Thread.sleep(MEDIUM_TIMEOUT_MILLIS);
      }
      synchronized (Executive.this) {
        LOG.info(String.format("Current time after next start of %s", TIME_FMT.format(nextStart)));
      }
    }
     
    /**
     * Sets an appropriate next start time given the current command history.
     */
    private void setNextStart() {
      synchronized(Executive.this) {
        Optional<Instant> oldestStart = history.getOldestStart();
        if (oldestStart.isPresent()) {
          nextStart = oldestStart.get().plus(config.getRunIntervalSec(), ChronoUnit.SECONDS);
          LOG.info(String.format("Scheduling next start for %s", TIME_FMT.format(nextStart)));
        } else {
          // By the time we're picking a time, the history will normally have a last execution,
          // but just in case of e.g. wifi failure we fallback to current time.
          nextStart = clock.instant();
          LOG.warning("Scheduling next start as current time - no previous completion found");
        }
      }
    }

    /**
     * Runs all commands in order, waiting until execution is complete. And gating on the
     * availability of wifi before starting each.
     */
    private void runCommandSet() throws InterruptedException {
      try {
        for (CommandRunner command : commands) {
          // TODO(jody): Wifi check
          activeCommand = command;
          runCommand(command);
        }
      } finally {
        activeCommand = null;
      }
    }

    /**
     * Runs a single command, waiting until execution is complete, and terminating the process
     * upon interruption.
     */
    private void runCommand(CommandRunner command) throws InterruptedException {
      command.start();
      try {
        while (command.isRunning()) {
          if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
          }
          Thread.sleep(SMALL_TIMEOUT_MILLIS);
        }
        history.recordEvent(command.getCommand(), command.getLastExecution());
      } catch (InterruptedException e) {
        command.terminate();
        history.recordEvent(command.getCommand(), command.getLastExecution());
        throw e;
      }
    }
  }
}
