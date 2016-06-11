package com.jsankey.overseer;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import com.jsankey.util.BriefTextFormatter;

import joptsimple.OptionException;

/**
 * Main class for executing the application. Most of the real work is delegated out to
 * {@link Configuration} for parsing command line options and {@link Executive} for scheduling
 * and performing the command execution.
 */
public class OverseerApplication {
  private static final int SUCCESS_EXIT_CODE = 0;
  private static final int PARSE_ERROR_EXIT_CODE = 2;
  private static final int INTERRUPTED_EXIT_CODE = 3;
  private static final int LONG_TIMEOUT_MILLIS = 15 * 60 * 1000;

  public static void main(String[] args) throws IOException {
    Configuration config = parseConfiguration(args);
    configureLogs(config);
    Executive exec = Executive.from(config);
    exec.begin();
    if (config.getSocket().isPresent()) {
      SocketService.from(config.getSocket().get());
    }
    // The executive is running on its own thread. We can just sleep on this one.
    waitIndefinitely();
  }

  /**
   * Returns a configuration from the supplied configuration options, outputting the 
   * help or version information when appropriate, and exiting gracefully on failure.
   */
  private static Configuration parseConfiguration(String[] args) throws IOException {
    Configuration config = null;
    try {
      config = Configuration.from(args);
      if (config.isHelpRequested()) {
        Configuration.printHelpOn(System.out);
        System.exit(SUCCESS_EXIT_CODE);
      } else if (config.isVersionRequested()) {
        Configuration.printVersionOn(System.out);
        System.exit(SUCCESS_EXIT_CODE);
      }
    } catch (OptionException e) {
      System.out.println(String.format("%nError reading command line options:"));
      System.out.println(String.format("  %s%n", e.toString()));
      Configuration.printHelpOn(System.out);
      System.exit(PARSE_ERROR_EXIT_CODE);
	}
    return config;
  }

  /**
   * Configures the parent logger for application classes to write to file if specified by
   * command line flag, or console if not.
   */
  private static void configureLogs(Configuration config) {
    Logger appLogger = Logger.getLogger(OverseerApplication.class.getPackage().getName());

    Handler handler = null;
    if (config.getLogFile().isPresent()) {
      try {
        handler = new FileHandler(config.getLogFile().get(), true /* append mode */);
      } catch (SecurityException | IOException e) {
        appLogger.warning("Could not log to the specified file, using console instead");
      }
    }
    if (handler == null) {
      handler = new ConsoleHandler();
    }
    handler.setFormatter(new BriefTextFormatter());
    appLogger.setUseParentHandlers(false);
    appLogger.addHandler(handler);
  }

  /**
   * Waits forever without consuming much CPU and exits gracefully if an interrupt occurs.
   */
  private static void waitIndefinitely() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(LONG_TIMEOUT_MILLIS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    System.exit(INTERRUPTED_EXIT_CODE);
  }
}
