package com.jsankey.overseer;

import java.io.IOException;

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

    Executive exec = Executive.from(config);
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
}
