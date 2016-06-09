package com.jsankey.overseer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
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

  private static final int COMMAND_COMPLETION_CHECK_MILLIS = 250;
  private static final int MANUAL_REQUEST_CHECK_MILLIS = 2000;
  private static final int WIFI_STATUS_CHECK_MILLIS = 60000;

  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  /** Logger for the current class. */
  private static final Logger LOG = Logger.getLogger(Executive.class.getCanonicalName());

  private final int runIntervalSec;
  private final ImmutableList<CommandRunner> commands;
  private final ExecutionHistory history;
  private final Clock clock;

  private @Nullable Thread runnerThread;
  private @Nullable CommandRunner activeCommand;
  private @Nullable WifiStatusChecker wifiStatus;
  private Instant automaticRunTime;
  private Instant manualRunTime;
  private boolean blockedOnWifi;

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
    this.history = ExecutionHistory.from(config);
    this.runIntervalSec = config.getRunIntervalSec();
    this.clock = Clock.systemUTC();
    this.wifiStatus =
        config.getSsid().isPresent() ? WifiStatusChecker.of(config.getSsid().get()) : null;
    this.blockedOnWifi = false;

    ImmutableList.Builder<CommandRunner> runnerBuilder = ImmutableList.builder();
    for (String command : config.getCommands()) {
      runnerBuilder.add(CommandRunner.forCommand(command));
    }
    this.commands = runnerBuilder.build();

    scheduleAutomaticRun();
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
    } else if (blockedOnWifi) {
      return Status.BLOCKED_ON_WIFI;
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
    LOG.info("Scheduling manual start at current time");
    manualRunTime = this.clock.instant();
  }

  /**
   * Begins execution for the first time.
   */
  public synchronized void begin() {
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
      Thread.sleep(COMMAND_COMPLETION_CHECK_MILLIS);
    }
  }

  /**
   * Sets an appropriate next automatic run time given the current command history, clearing any
   * previous manual run request.
   */
  private synchronized void scheduleAutomaticRun() {
    Optional<Instant> oldestStart = history.getOldestStart();
    if (oldestStart.isPresent()) {
      automaticRunTime = oldestStart.get().plus(runIntervalSec, ChronoUnit.SECONDS);
      LOG.info(String.format("Scheduling next start for %s", TIME_FMT.format(automaticRunTime)));
    } else {
      // The history will normally have a last execution, but in case it doesn't (e.g. first run)
      // we fallback to current time.
      automaticRunTime = clock.instant();
      LOG.warning("Scheduling next start as current time - no previous completion found");
    }
    manualRunTime = Instant.MAX;
  }

  /**
   * Inner class to handle the actual execution on a dedicated thread.
   */
  private class ExecutiveRunner implements Runnable {

    @Override
    public void run() {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          waitUntilNextRun();
          runCommandSet();
          scheduleAutomaticRun();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        LOG.log(Level.SEVERE, "Fatal exception", e);
      }
      //Communicate back to the parent that we stopped by clearing its reference.
      runnerThread = null;
    }

    /**
     * Runs all commands in order, waiting until execution is complete and gating
     * on the availability of wifi before starting each.
     */
    private void runCommandSet() throws InterruptedException {
      try {
        for (CommandRunner command : commands) {
          waitUntilWifi();
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
      if (!Thread.currentThread().isInterrupted()) {
        try {
          activeCommand = command;
          command.start();
          while (command.isRunning() && !Thread.currentThread().isInterrupted()) {
            Thread.sleep(COMMAND_COMPLETION_CHECK_MILLIS);
          }
        } finally {
          command.terminate();
          history.recordEvent(command.getCommand(), command.getLastExecution());
          activeCommand = null;
        }
      }
    }

    /**
     * Waits until the next scheduled or manually requested run time.
     */
    private void waitUntilNextRun() throws InterruptedException {
      while (!Thread.currentThread().isInterrupted()) {
        Instant now = clock.instant();
        if (now.isAfter(manualRunTime)) {
          LOG.info(String.format(
              "Current time after manual start of %s", TIME_FMT.format(manualRunTime)));
          break;
        } else if (now.isAfter(automaticRunTime)) {
          LOG.info(String.format(
              "Current time after automatic start of %s", TIME_FMT.format(automaticRunTime)));
          break;
        }
        Thread.sleep(MANUAL_REQUEST_CHECK_MILLIS);
      }
    }

    /**
     * Waits until the specified wifi SSID is connected, or returns immediately if
     * no wifi SSID was specified.
     */
    private void waitUntilWifi() throws InterruptedException {
      // Try the initial run without any logging or fallback.
      if (wifiStatus == null || wifiStatus.connected()) {
        return;
      }

      // After the initial, only check after a time delay or a manual run request;
      // the wifi checker is quite expensive.
      try {
        LOG.info(String.format("Waiting for wifi SSID: %s", wifiStatus.getTargetSsid()));
        Instant nextCheckTime = clock.instant().plus(WIFI_STATUS_CHECK_MILLIS, ChronoUnit.MILLIS);
        Instant cachedManualRunTime = manualRunTime;
        blockedOnWifi = true;
        while (!Thread.currentThread().isInterrupted()) {
          if (clock.instant().isAfter(nextCheckTime) || manualRunTime != cachedManualRunTime) {
            if (wifiStatus.connected()) {
              LOG.info(String.format("Now connected to wifi SSID: %s", wifiStatus.getTargetSsid()));
              return;
            } else {
              nextCheckTime = clock.instant().plus(WIFI_STATUS_CHECK_MILLIS, ChronoUnit.MILLIS);
              cachedManualRunTime = manualRunTime;
            }
          }
        }
      } finally {
        blockedOnWifi = false;
      }
    }
  }
}
