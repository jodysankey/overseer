/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.jsankey.overseer.Executive;
import com.jsankey.overseer.history.ExecutionHistory;

public class SocketConnectionTest {

  private static final InetAddress TEST_ADDRESS = InetAddress.getLoopbackAddress();
  private static final int TEST_PORT = 9999;
  private static final String TEST_COMMAND = "test command one";
  private static final Instant TEST_START_TIME = Instant.ofEpochMilli(12345678L);
  private static final Instant TEST_END_TIME = Instant.ofEpochMilli(23456789L);
  private static final int TEST_EXIT_CODE = 0;
  private static final Executive.Status TEST_EXEC_STATUS = Executive.Status.BLOCKED_ON_WIFI;
  private static final Executive.Status TEST_EXEC_STATUS_2 = Executive.Status.IDLE;

  private static final int EXECUTION_TIME_MILLIS = 100;

  private Socket mockSocket;
  private Executive mockExecutive;
  private ExecutionHistory testHistory;
  private ByteArrayOutputStream outputStream;
  private SocketConnection testObject;
  private Thread runnerThread;

  private enum RunMode {
    REQUEST_CLOSE,
    EXPECT_SELF_CLOSE,
    LEAVE_RUNNING
  }

  @Before
  public void setUp() throws Exception {
    outputStream = new ByteArrayOutputStream();
    mockSocket = mock(Socket.class);
    when(mockSocket.getOutputStream()).thenReturn(outputStream);
    when(mockSocket.getInetAddress()).thenReturn(TEST_ADDRESS);
    when(mockSocket.getPort()).thenReturn(TEST_PORT);

    testHistory = new ExecutionHistory(Optional.<String>absent(), ImmutableList.of(TEST_COMMAND));
    testHistory.recordEvent(TEST_COMMAND, TEST_START_TIME, TEST_END_TIME, TEST_EXIT_CODE);

    mockExecutive = mock(Executive.class);
    when(mockExecutive.getHistory()).thenReturn(testHistory);
    when(mockExecutive.getStatus()).thenReturn(TEST_EXEC_STATUS);
  }

  @After
  public void verifyNoFurtherInteractions() {
    verify(mockExecutive).registerListener(testObject);
    verify(mockExecutive).unregisterListener(testObject);
    Mockito.verifyNoMoreInteractions(mockExecutive);
  }

  @Test
  public void testHelp() throws Exception {
    setTestInput("help\n");
    startTestObject(RunMode.REQUEST_CLOSE);
    assertThat(outputStream.toString())
        .contains("{\"RUN\":\"Begins a new execution of the commands immediately\"}");
  }

  @Test(timeout=1000)
  public void testClose() throws Exception {
    setTestInput("close\n");
    startTestObject(RunMode.EXPECT_SELF_CLOSE);
    verify(mockSocket).close();
  }

  @Test
  public void testStatus() throws Exception {
    setTestInput("status\n");
    startTestObject(RunMode.REQUEST_CLOSE);
    verify(mockExecutive).getStatus();
    verify(mockExecutive).getHistory();
    assertThat(outputStream.toString()).isEqualTo(
        "{\"status\":\"BLOCKED_ON_WIFI\",\"last_start_ms\":\"12345678\"}\n");
  }

  @Test
  public void testHistory() throws Exception {
    setTestInput("history\n");
    startTestObject(RunMode.REQUEST_CLOSE);
    verify(mockExecutive).getHistory();
    assertThat(outputStream.toString())
        .isEqualTo("[{\"command\":\"test command one\",\"executions\":"
            + "[{\"start_ms\":12345678,\"end_ms\":23456789,\"exit_code\":0}]}]\n");
  }

  @Test
  public void testShutdown() throws Exception {
    setTestInput("shutdown\n");
    startTestObject(RunMode.REQUEST_CLOSE);
    verify(mockExecutive).terminate();
  }

  @Test
  public void testRun() throws Exception {
    setTestInput("run\n");
    startTestObject(RunMode.REQUEST_CLOSE);
    verify(mockExecutive).runNow();
  }

  @Test(timeout=1000)
  public void testUnknownCommandIgnored() throws Exception {
    setTestInput("iamgarbage\nhelp\nclose\n");
    startTestObject(RunMode.EXPECT_SELF_CLOSE);
    assertThat(outputStream.toString())
        .contains("{\"RUN\":\"Begins a new execution of the commands immediately\"}");
  }

  @Test
  public void testLongCommand() throws Exception {
    setTestInput("i am a really long command thats longer thats much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much much much much much much much much much much much much much much "
        + "much much much much longer than even the longer input buffer\n");
    startTestObject(RunMode.REQUEST_CLOSE);
  }

  @Test(timeout=1000)
  public void testStatusChange() throws Exception {
    setTestInput("");
    startTestObject(RunMode.LEAVE_RUNNING);

    // Send two statuses, changing the state in between, then stop the object
    testObject.receiveStatus(TEST_EXEC_STATUS);
    when(mockExecutive.getStatus()).thenReturn(TEST_EXEC_STATUS_2);
    testObject.receiveStatus(TEST_EXEC_STATUS_2);
    testObject.parser.initiateClose();
    runnerThread.join();

    verify(mockExecutive, atLeastOnce()).getStatus();
    verify(mockExecutive, atLeastOnce()).getHistory();
    assertThat(outputStream.toString()).isEqualTo(
        "{\"status\":\"BLOCKED_ON_WIFI\",\"last_start_ms\":\"12345678\"}\n"
        + "{\"status\":\"IDLE\",\"last_start_ms\":\"12345678\"}\n");
  }

  @Test
  public void testWebSocketUpgrade() throws Exception {
    setTestInput("GET /overseer HTTP/1.1\r"
        + "Host: localhost:4321\r\n"
        + "Connection: Upgrade\r\n"
        + "Pragma: no-cache\r\n"
        + "Cache-Control: no-cache\r\n"
        + "Upgrade: websocket\r\n"
        + "Origin: file://\r\n"
        + "Sec-WebSocket-Version: 13\r\n"
        + "User-Agent: Dummy user agent\r\n"
        + "Accept-Encoding: gzip, deflate, sdch\r\n"
        + "Accept-Language: en-US,en;q=0.8\r\n"
        + "Sec-WebSocket-Key: r7oPJjbmnmKEZdkzqALUrQ==\r\n"
        + "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits\r\n"
        + "\r\n");
    startTestObject(RunMode.LEAVE_RUNNING);
    Thread.sleep(EXECUTION_TIME_MILLIS);
    assertThat(testObject.parser).isInstanceOf(String.class);
    testObject.parser.initiateClose();
    // Expected output
    //  HTTP/1.1 101 Switching Protocols"
    //  Upgrade: websocket
    //  Connection: Upgrade
    //  Sec-WebSocket-Accept: K1nj46hfwdYOhmXS2b8cAt1GR4c=

  }

  private void setTestInput(String input) throws IOException {
    InputStream inputStream = new ByteArrayInputStream(input.getBytes());
    when(mockSocket.getInputStream()).thenReturn(inputStream);
  }

  private void startTestObject(RunMode mode) throws IOException, InterruptedException {
    testObject = SocketConnection.from(mockSocket, mockExecutive);
    runnerThread = new Thread(testObject);
    runnerThread.start();
    if (mode == RunMode.REQUEST_CLOSE) {
      Thread.sleep(EXECUTION_TIME_MILLIS);
      testObject.parser.initiateClose();
    }
    if (mode != RunMode.LEAVE_RUNNING) {
      runnerThread.join();
    }
  }
}
