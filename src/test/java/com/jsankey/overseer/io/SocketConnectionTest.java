package com.jsankey.overseer.io;

import static com.google.common.truth.Truth.assertThat;
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

  private Socket mockSocket;
  private Executive mockExecutive;
  private ExecutionHistory testHistory;
  private ByteArrayOutputStream outputStream;
  private SocketConnection testObject;

  private enum RunMode {
    SINGLE_READ,
    LOOP_UNTIL_CLOSE
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
    Mockito.verifyNoMoreInteractions(mockExecutive);
  }

  @Test
  public void testHelp() throws IOException {
    setTestInput("help\n");
    startTestObject(RunMode.SINGLE_READ);
    assertThat(outputStream.toString())
        .contains("{\"RUN\":\"Begins a new execution of the commands immediately\"}");
  }

  @Test(timeout=1000)
  public void testClose() throws IOException {
    setTestInput("close\n");
    startTestObject(RunMode.LOOP_UNTIL_CLOSE);
    verify(mockSocket).close();
  }

  @Test
  public void testStatus() throws IOException {
    setTestInput("status\n");
    startTestObject(RunMode.SINGLE_READ);
    verify(mockExecutive).getStatus();
    verify(mockExecutive).getHistory();
    assertThat(outputStream.toString())
        .isEqualTo("{\"status\":\"BLOCKED_ON_WIFI\",\"last_start_ms\":\"12345678\"}");
  }

  @Test
  public void testHistory() throws IOException {
    setTestInput("history\n");
    startTestObject(RunMode.SINGLE_READ);
    verify(mockExecutive).getHistory();
    assertThat(outputStream.toString())
        .isEqualTo("[{\"command\":\"test command one\",\"executions\":"
            + "[{\"start_ms\":12345678,\"end_ms\":23456789,\"exit_code\":0}]}]");
  }

  @Test
  public void testShutdown() throws IOException {
    setTestInput("shutdown\n");
    startTestObject(RunMode.SINGLE_READ);
    verify(mockExecutive).terminate();
  }

  @Test
  public void testRun() throws IOException {
    setTestInput("run\n");
    startTestObject(RunMode.SINGLE_READ);
    verify(mockExecutive).runNow();
  }

  @Test(timeout=1000)
  public void testUnknownCommandIgnored() throws IOException {
    setTestInput("iamgarbage\nhelp\nclose\n");
    startTestObject(RunMode.LOOP_UNTIL_CLOSE);
    assertThat(outputStream.toString())
        .contains("{\"RUN\":\"Begins a new execution of the commands immediately\"}");
  }

  @Test
  public void testLongCommand() throws IOException {
    setTestInput("i am a really long command thats longer than the input buffer\n");
    startTestObject(RunMode.SINGLE_READ);
  }

  private void setTestInput(String input) throws IOException {
    InputStream inputStream = new ByteArrayInputStream(input.getBytes());
    when(mockSocket.getInputStream()).thenReturn(inputStream);
  }

  private void startTestObject(RunMode runMode) throws IOException {
    testObject = SocketConnection.from(mockSocket, mockExecutive);
    testObject.closeRequested = (runMode == RunMode.SINGLE_READ);
    testObject.run();
  }
}