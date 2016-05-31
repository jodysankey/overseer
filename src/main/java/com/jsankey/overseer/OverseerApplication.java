package com.jsankey.overseer;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.jsankey.util.BriefTextFormatter;

/**
 * Main class for executing the application. Most of the real work is delegated out to
 * {@link Configuration} for parsing command line options and {@link Executive} for scheduling
 * and performing the command execution.
 */
public class OverseerApplication {
  private static final int PARSE_ERROR_EXIT_CODE = 2;
  private static final int INTERRUPTED_EXIT_CODE = 3;

  public static void main(String[] args) throws IOException {
    Configuration config;
    try {
      config = Configuration.from(args);
    } catch (RuntimeException e) {
      // TODO(jody): Doesn't add functionality but should really use a non-error situation
      // for help and version information.
      Configuration.printVersionOn(System.out);
      System.out.print(String.format("%nError reading command line options%n%s%n", e.toString()));
      Configuration.printHelpOn(System.out);
      System.exit(PARSE_ERROR_EXIT_CODE);
      // Explicit return helps static analysis determine state of config
      return;
	}

    configureLogs(config);
    Executive exec = Executive.from(config);
    exec.start();
    try {
      // The executive is running on its own thread. We can just sleep on this one
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(60000);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    System.exit(INTERRUPTED_EXIT_CODE);
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
        handler = new FileHandler(config.getLogFile().get());
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

  private static void dumpLogConfiguration() {
    // TODO(jody): This was helpful for debug but should be deleted soon.
    LogManager manager = LogManager.getLogManager();
    Enumeration<String> loggerEnum = manager.getLoggerNames();
    while (loggerEnum.hasMoreElements()) {
      String loggerName = loggerEnum.nextElement();
      Logger logger = manager.getLogger(loggerName);
      System.out.println("LOG: " + loggerName);
      for (Handler handler : logger.getHandlers()) {
        System.out.println("  handler: " + handler.toString());
      }
    }
  }
}
