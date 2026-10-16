/*
 * Copyright (C) 2016-2026 Jody Sankey
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

public class WifiStatusCheckerTest {

  private static final String TEST_SSID = "ExampleNetwork";
  private static final String WRONG_SSID = "WrongNetwork";

  private static final String CONNECTED_OUTPUT =
    "no:OtherNetwork\n"
    + "no:\n"
    + "yes:ExampleNetwork\n"
    + "no:ExampleNetwork\n"
    + "no:ExampleNetworkExtended\n";

  private static final String DISCONNECTED_OUTPUT =
    "no:OtherNetwork\n"
    + "no:\n"
    + "no:ExampleNetwork\n";

  private static final String SPECIAL_SSID = "Odd:Net\\work";
  private static final String SPECIAL_CONNECTED_OUTPUT =
    "no:OtherNetwork\n"
    + "yes:Odd\\:Net\\\\work\n";

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
  public void testSsidPrefixDoesNotMatch() throws Exception {
    checker = spy(WifiStatusChecker.of("Example"));
    configureChecker(CONNECTED_OUTPUT, 0);
    assertThat(checker.connected()).isFalse();
  }

  @Test
  public void testEscapedSsid() throws Exception {
    checker = spy(WifiStatusChecker.of(SPECIAL_SSID));
    configureChecker(SPECIAL_CONNECTED_OUTPUT, 0);
    assertThat(checker.connected()).isTrue();
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
