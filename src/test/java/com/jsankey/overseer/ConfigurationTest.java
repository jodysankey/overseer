package com.jsankey.overseer;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import joptsimple.OptionException;

public class ConfigurationTest {

  private static final String TEST_SSID = "theNeighborsWifi";
  private static final String TEST_LOG_FILE = "/var/log/test_output_here";
  private static final String TEST_STATUS_FILE = "/home/user/.overseer";
  private static final int TEST_CHECK_INTERVAL = 999;
  private static final int TEST_RUN_INTERVAL = 888;
  private static final String COMMAND_1 = "run something --with flag";
  private static final String COMMAND_2 = "log anotherthing now";

  @Test
  public void testValidInput() {
    Configuration config = Configuration.from(new String[]{
        "--ssid", TEST_SSID,
        "--log_file", TEST_LOG_FILE,
        "--status_file", TEST_STATUS_FILE,
        "--check_interval", String.valueOf(TEST_CHECK_INTERVAL),
        "--run_interval", String.valueOf(TEST_RUN_INTERVAL),
        "--command", COMMAND_1,
        "--command", COMMAND_2});
    assertThat(config.getSsid()).isEqualTo(Optional.of(TEST_SSID));
    assertThat(config.getLogFile()).isEqualTo(Optional.of(TEST_LOG_FILE));
    assertThat(config.getStatusFile()).isEqualTo(Optional.of(TEST_STATUS_FILE));
    assertThat(config.getCheckIntervalSec()).isEqualTo(TEST_CHECK_INTERVAL);
    assertThat(config.getRunIntervalSec()).isEqualTo(TEST_RUN_INTERVAL);
    assertThat(config.getCommands()).isEqualTo(ImmutableList.of(COMMAND_1, COMMAND_2));
  }

  @Test
  public void testMinimalInput() {
     Configuration config = Configuration.from(new String[]{"--command", COMMAND_1});
    assertThat(config.getSsid()).isEqualTo(Optional.<String>absent());
    assertThat(config.getLogFile()).isEqualTo(Optional.<String>absent());
    assertThat(config.getStatusFile()).isEqualTo(Optional.<String>absent());
    assertThat(config.getCheckIntervalSec()).isEqualTo(30/* Default */);
    assertThat(config.getRunIntervalSec()).isEqualTo(300/* Default */);
    assertThat(config.getCommands()).isEqualTo(ImmutableList.of(COMMAND_1));
  }

  @Test
  public void testUnknownOption() {
    try {
      Configuration.from(new String[]{"--fictional", "--command", COMMAND_1});
      fail();
    } catch (OptionException e){
      // Expected
    }
  }

  @Test
  public void testNonIntegerValue() { 
    try {
      Configuration.from(new String[]{"--run_interval", "bob", "--command", COMMAND_1});
      fail();
    } catch (OptionException e){
      // Expected
    }
  }

  @Test
  public void testNoCommands() {
    try {
      Configuration.from(new String[]{"--ssid", TEST_SSID});
      fail();
    } catch (RuntimeException e){
      // Expected
    }
   
  }

  @Test
  public void testHelpOutput() throws IOException {
    OutputStream mockStream = mock(OutputStream.class);
    ArgumentCaptor<byte[]> writtenHelp = ArgumentCaptor.forClass(byte[].class);
    
    Configuration.printHelpOn(mockStream);

    verify(mockStream).write(writtenHelp.capture(), anyInt(), anyInt());
    String actual = new String(writtenHelp.getValue());
    assertThat(actual).contains("--check_interval <Integer>");
    assertThat(actual).contains("Optional file to cache and restore command");
  }
 
}