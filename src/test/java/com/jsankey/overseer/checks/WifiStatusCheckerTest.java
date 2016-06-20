/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.checks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.jsankey.overseer.checks.WifiStatusChecker;

public class WifiStatusCheckerTest {

  private static final String TEST_SSID = "ExampleNetwork";
  private static final String WRONG_SSID = "WrongNetwork";

  private static final String CONNECTED_OUTPUT =
    "enp2s0    no wireless extensions.\n"
    + "wlp1s0    IEEE 802.11abgn  ESSID:\"ExampleNetwork\"  \n"
    + "          Mode:Managed  Frequency:2.422 GHz  Access Point: AA:AA:AA:AA:AA:63   \n"
    + "          Bit Rate=5.5 Mb/s   Tx-Power=15 dBm   \n"
    + "          Retry short limit:7   RTS thr:off   Fragment thr:off\n"
    + "          Power Management:off\n"
    + "          Link Quality=48/70  Signal level=-62 dBm  \n"
    + "          Rx invalid nwid:0  Rx invalid crypt:0  Rx invalid frag:0\n"
    + "          Tx excessive retries:0  Invalid misc:45   Missed beacon:0\n"
    + " \n"
    + "lo        no wireless extensions.\n"
    + " \n";

  private static final String DISCONNECTED_OUTPUT =
    "enp2s0    no wireless extensions.\n"
    + " \n"
    + "wlp1s0    IEEE 802.11abgn  ESSID:off/any  \n"
    + "          Mode:Managed  Access Point: Not-Associated   Tx-Power=15 dBm   \n"
    + "          Retry short limit:7   RTS thr:off   Fragment thr:off\n"
    + "          Power Management:off\n"
    + "            \n"
    + "lo        no wireless extensions.\n";

  private WifiStatusChecker checker;

  @Before
  public void setUp() {
    checker = spy(WifiStatusChecker.of(TEST_SSID));
  }

  @Test
  public void testGetSsid() {
    assertThat(checker.getTargetSsid()).isEqualTo(TEST_SSID);
  }

  @Test
  public void testCorrectWifi() throws Exception {
    configureChecker(CONNECTED_OUTPUT, 0);
    assertThat(checker.connected()).isTrue();
  }

  @Test
  public void testIncorrectWifi() throws Exception {
    checker = spy(WifiStatusChecker.of(WRONG_SSID));
    configureChecker(DISCONNECTED_OUTPUT, 0);
    assertThat(checker.connected()).isFalse();
  }

  @Test
  public void testNoWifi() throws Exception {
    configureChecker(DISCONNECTED_OUTPUT, 0);
    assertThat(checker.connected()).isFalse();
  }

  @Test
  public void testCommandFailure() throws Exception {
    configureChecker(CONNECTED_OUTPUT, 1);
    assertThat(checker.connected()).isFalse();
  }

  private void configureChecker(String output, int returnCode)
      throws InterruptedException, IOException {
    Process mockProcess = mock(Process.class);
    when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes()));
    when(mockProcess.waitFor()).thenReturn(returnCode);
    when(checker.createProcess()).thenReturn(mockProcess);
  }
}
