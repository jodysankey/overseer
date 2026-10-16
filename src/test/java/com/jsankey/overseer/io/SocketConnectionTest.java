/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

  private static final int EXECUTION_TIME_MILLIS = 1000;

  private static final String TEST_WEBSOCKET_KEY = "r7oPJjbmnmKEZdkzqALUrQ==";
  private static final byte[] TEST_MASK = {(byte) 0x37, (byte) 0xfa, (byte) 0x21, (byte) 0x3d};
  private static final int OPCODE_TEXT = 0x1;
  private static final int OPCODE_CLOSE = 0x8;
  private static final int OPCODE_PING = 0x9;
  private static final int OPCODE_PONG = 0xA;
  private static final String EXPECTED_STATUS_JSON =
      "{\"status\":\"BLOCKED_ON_WIFI\",\"last_start_ms\":\"12345678\"}";
  // Accept value for TEST_WEBSOCKET_KEY as defined in RFC 6455 section 4.2.2.
  private static final String EXPECTED_UPGRADE_RESPONSE =
      "HTTP/1.1 101 Switching Protocols\r\n"
      + "Upgrade: websocket\r\n"
      + "Connection: Upgrade\r\n"
      + "Sec-WebSocket-Accept: K1nj46hfwdYOhmXS2b8cAt1GR4c=\r\n\r\n";

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
  public void testStatusWithCrLf() throws Exception {
    setTestInput("status\r\n");
    startTestObject(RunMode.REQUEST_CLOSE);
    verify(mockExecutive).getStatus();
    verify(mockExecutive).getHistory();
    assertThat(outputStream.toString()).isEqualTo(EXPECTED_STATUS_JSON + "\n");
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

  @Test(timeout=2000)
  public void testShutdownWhenAlreadyTerminated() throws Exception {
    doThrow(new IllegalStateException()).when(mockExecutive).terminate();
    setTestInput("shutdown\nclose\n");
    startTestObject(RunMode.EXPECT_SELF_CLOSE);
    // The connection should survive the failed shutdown and go on to process the close.
    verify(mockExecutive).terminate();
    verify(mockSocket).close();
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
    setTestInput(webSocketRequest("Sec-WebSocket-Key: " + TEST_WEBSOCKET_KEY + "\r\n"));
    startTestObject(RunMode.LEAVE_RUNNING);
    Thread.sleep(EXECUTION_TIME_MILLIS);
    assertThat(testObject.parser).isInstanceOf(WebConnectionParser.class);
    testObject.parser.initiateClose();
    runnerThread.join();

    // Expect the upgrade response followed by a websocket close frame.
    ByteArrayOutputStream expected = new ByteArrayOutputStream();
    expected.write(EXPECTED_UPGRADE_RESPONSE.getBytes(StandardCharsets.US_ASCII));
    expected.write(new byte[] {(byte) 0x88, 0x00});
    assertThat(outputStream.toByteArray()).isEqualTo(expected.toByteArray());
  }

  @Test
  public void testWebSocketCommandsAndPing() throws Exception {
    ByteArrayOutputStream input = new ByteArrayOutputStream();
    input.write(webSocketRequest("Sec-WebSocket-Key: " + TEST_WEBSOCKET_KEY + "\r\n")
        .getBytes(StandardCharsets.US_ASCII));
    input.write(maskedFrame(OPCODE_TEXT, "status"));
    input.write(maskedFrame(OPCODE_PING, "hi"));
    input.write(maskedFrame(OPCODE_CLOSE, ""));
    setTestInput(input.toByteArray());
    startTestObject(RunMode.EXPECT_SELF_CLOSE);
    verify(mockExecutive).getStatus();
    verify(mockExecutive).getHistory();

    // Expect the upgrade response, the status as a text frame, a pong echoing the ping data,
    // and finally a close frame in response to the client's close.
    ByteArrayOutputStream expected = new ByteArrayOutputStream();
    expected.write(EXPECTED_UPGRADE_RESPONSE.getBytes(StandardCharsets.US_ASCII));
    expected.write(unmaskedFrame(OPCODE_TEXT, EXPECTED_STATUS_JSON));
    expected.write(unmaskedFrame(OPCODE_PONG, "hi"));
    expected.write(unmaskedFrame(OPCODE_CLOSE, ""));
    assertThat(outputStream.toByteArray()).isEqualTo(expected.toByteArray());
    verify(mockSocket).close();
  }

  @Test
  public void testWebSocketUpgradeHeaderCaseInsensitive() throws Exception {
    setTestInput(webSocketRequest("sec-websocket-key:  " + TEST_WEBSOCKET_KEY + " \r\n"));
    startTestObject(RunMode.LEAVE_RUNNING);
    Thread.sleep(EXECUTION_TIME_MILLIS);
    assertThat(testObject.parser).isInstanceOf(WebConnectionParser.class);
    testObject.parser.initiateClose();
    runnerThread.join();
    assertThat(new String(outputStream.toByteArray(), StandardCharsets.US_ASCII))
        .startsWith(EXPECTED_UPGRADE_RESPONSE);
  }

  @Test
  public void testWebSocketUpgradeWithoutKey() throws Exception {
    setTestInput(webSocketRequest(""));
    startTestObject(RunMode.REQUEST_CLOSE);
    assertThat(testObject.parser).isInstanceOf(TextConnectionParser.class);
    assertThat(outputStream.toString(StandardCharsets.US_ASCII))
        .isEqualTo("HTTP/1.1 400 Bad Request\r\n\r\n");
  }

  /** Returns a websocket upgrade request, including the supplied key header line if any. */
  private static String webSocketRequest(String keyLine) {
    return "GET /overseer HTTP/1.1\r\n"
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
        + keyLine
        + "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits\r\n"
        + "\r\n";
  }

  private void setTestInput(String input) throws IOException {
    setTestInput(input.getBytes(StandardCharsets.US_ASCII));
  }

  private void setTestInput(byte[] input) throws IOException {
    InputStream inputStream = new ByteArrayInputStream(input);
    when(mockSocket.getInputStream()).thenReturn(inputStream);
  }

  /** Returns a client-to-server (and hence masked) websocket frame with the supplied opcode. */
  private static byte[] maskedFrame(int opCode, String payload) {
    byte[] data = payload.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    frame.write(0x80 | opCode);
    frame.write(0x80 | data.length);
    frame.write(TEST_MASK, 0, TEST_MASK.length);
    for (int i = 0; i < data.length; i++) {
      frame.write(data[i] ^ TEST_MASK[i % 4]);
    }
    return frame.toByteArray();
  }

  /** Returns a server-to-client (and hence unmasked) websocket frame with the supplied opcode. */
  private static byte[] unmaskedFrame(int opCode, String payload) {
    byte[] data = payload.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    frame.write(0x80 | opCode);
    frame.write(data.length);
    frame.write(data, 0, data.length);
    return frame.toByteArray();
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
