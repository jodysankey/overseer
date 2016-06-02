package com.jsankey.overseer;

import java.io.BufferedReader;
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
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(wifiStatus.getInputStream())); 
      int exitCode = wifiStatus.waitFor();
      if (exitCode == 0) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.contains(searchString)) {
            return true;
          }
        }
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
  @SuppressWarnings("static-method")
  Process createProcess() throws IOException {
    return new ProcessBuilder("iwconfig").start();
  }
}
