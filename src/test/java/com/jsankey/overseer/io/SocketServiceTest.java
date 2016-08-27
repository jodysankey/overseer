/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.jsankey.overseer.Executive;

@SuppressWarnings("static-method")
public class SocketServiceTest {

  private static final int TEST_PORT = 9999;
  // Time to wait after sending a command before receiving results. Note this is
  // longer than the sleep in the socket command procesor
  private static final long RESPONSE_DELAY_MILLIS = 300L;

  private Executive mockExecutive;
  private SocketService socketService;

  @Before
  public void setUp() throws Exception {
    mockExecutive = mock(Executive.class);
    socketService = SocketService.from(TEST_PORT, mockExecutive);
  }

  @After
  public void shutdown() {
    socketService.close(); 
  }

  @Test(timeout=1000)
  public void testSingleConnection() throws Exception {
    TestSocket connection = TestSocket.connect();

    connection.sendCommand("help\n"); 
    Thread.sleep(RESPONSE_DELAY_MILLIS);
    assertThat(connection.readResponse()).contains("\"commands\":[{");

    connection.sendCommand("close\n");
    Thread.sleep(RESPONSE_DELAY_MILLIS);
    assertThat(connection.readResponse()).isNull();
  }

  @Test(timeout=1000)
  public void testDoubleConnection() throws Exception {
    TestSocket c1 = TestSocket.connect();
    TestSocket c2 = TestSocket.connect();

    c1.sendCommand("help\n"); 
    c2.sendCommand("help\n"); 
    Thread.sleep(RESPONSE_DELAY_MILLIS);
    assertThat(c1.readResponse()).contains("\"commands\":[{");
    assertThat(c2.readResponse()).contains("\"commands\":[{");

    c2.sendCommand("close\n");
    c1.sendCommand("help\n"); 
    Thread.sleep(RESPONSE_DELAY_MILLIS);
    assertThat(c1.readResponse()).contains("\"commands\":[{");
    assertThat(c2.readResponse()).isNull();

    c1.sendCommand("close\n");
    Thread.sleep(100);
    assertThat(c1.readResponse()).isNull();
  }

  private static class TestSocket {
    private final Socket socket;
    private final BufferedReader reader;

    public static TestSocket connect() throws IOException {
      return new TestSocket(new Socket(InetAddress.getLoopbackAddress(), TEST_PORT));
    }

    private TestSocket(Socket socket) throws IOException {
      this.socket = socket;
      this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void sendCommand(String command) throws IOException {
      socket.getOutputStream().write(command.getBytes());
      socket.getOutputStream().flush();
    }

    public String readResponse() throws IOException {
      return reader.readLine();
    }
  }
}
