/*
 * Copyright (C) 2016-2026 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.checks;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;

/**
 * Utility class to determine whether the host is currently connected to a particular wifi network.
 *
 * <p>The implementation depends on availability of the *nix iwconfig command so is not portable.
 */
public class WifiStatusChecker {

  /** Logger for the current class. */
  private static final Logger LOG = Logger.getLogger(WifiStatusChecker.class.getCanonicalName());
  /** The bit bucket as a {@link File} for convenience of redirects. */
  private static final File DEVNULL = new File("/dev/null");

  private final String targetSsid;
  private final String searchString;

  /**
   * Constructs a new instance to check for the specified SSID.
   */
  private WifiStatusChecker(String targetSsid) {
    this.targetSsid = targetSsid;
    this.searchString = String.format("SSID:\"%s\"", targetSsid);
  }

  /**
   * Constructs a new instance to check for the specified SSID.
   */
  public static WifiStatusChecker of(String targetSsid) {
    return new WifiStatusChecker(targetSsid);
  }

  /**
   * Returns true iff the host is currently connected to the target wifi.
   */
  public boolean connected() {
    try {
      Process wifiStatus = createProcess();
      // Read all output before waiting for the process to exit, otherwise a full pipe buffer
      // could block the process forever.
      boolean found = false;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(wifiStatus.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.contains(searchString)) {
            found = true;
          }
        }
      }
      int exitCode = wifiStatus.waitFor();
      if (exitCode == 0) {
        return found;
      } else {
        LOG.warning(String.format("Get wifi status failed with error code %d", exitCode));
      }
    } catch (IOException e) {
      LOG.warning(String.format("IOException while reading wifi status: %s", e.toString()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /**
   * Returns the SSID this {@link WifiStatusChecker} is searching for.
   */
  public String getTargetSsid() {
    return targetSsid;
  }

  /**
   * Returns a new process whose output stream returns information about the connected wifi.
   * This is designed to be mocked for testing.
   */
  @VisibleForTesting
  Process createProcess() throws IOException {
    // Only stdout is used, so avoid creating (and leaking) pipes for stdin and stderr.
    return new ProcessBuilder("iwconfig")
        .redirectInput(DEVNULL)
        .redirectError(DEVNULL)
        .start();
  }
}
