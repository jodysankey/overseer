/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.json.JsonStructure;

/**
 * Interface for classes that can parse commands from and
 * send information to a socket using a particular protocol.
 */
abstract class ConnectionParser {

  private static final int INPUT_SIZE = 1024;
  private static final int SOCKET_INPUT_CHECK_MILLIS = 200;

  protected final Socket socket;
  protected final InputStream input;
  protected final OutputStream output;

  protected boolean closeRequested;

  public class UpgradeRequested extends Exception {
    private static final long serialVersionUID = 228774278790018938L;
  }

  /**
   * Constructs a new {@link ConnectionParser}
   * @param socket the {@link Socket} to communicate over
   * @throws IOException on failing to access input or output streams for the socket
   */
  public ConnectionParser(Socket socket) throws IOException {
    this.socket = socket;
    this.input = socket.getInputStream();
    this.output = socket.getOutputStream();
    this.closeRequested = false;
  }

  /**
   * Returns the hostname and port of the remote socket.
   */
  public String getSocketName() {
    return String.format("%s:%d", socket.getInetAddress().getHostAddress(), socket.getPort());
  }

  /**
   * Returns the next command from the socket, or null if an problem occurred. IO necessary to 
   * comply with protocol specific requirements may be conducted before the command is returned.
   *
   * @throws InterruptedException if close is requested
   * @throws IOException is an IOError occurs or EOF is reached
   */
  public abstract Command receiveInput() throws InterruptedException, IOException;

  /**
   * Outputs a supplied {@link JsonStructure} on the socket.
   */
  public abstract void sendJson(JsonStructure json);

  /**
   * Initiates an orderly shutdown for those protocols that require it.
   */
  public void initiateClose() {
    closeRequested = true;
  }

  /**
   * Returns the next LF terminated command from the socket, or null if the line exceeds the max
   * provisioned length.
   *
   * @throws InterruptedException if close is requested
   * @throws IOException is an IOError occurs or EOF is reached
   */
  protected String readToLf() throws InterruptedException, IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    while (true) {
      if (closeRequested) {
        throw new InterruptedException("Socket close requested");
      } else if (input.available() == 0) {
        Thread.sleep(SOCKET_INPUT_CHECK_MILLIS);
      } else {
        int b = input.read();
        if (b < 0) {
          throw new IOException("Read EOF from socket");
        } else if (b == '\n') {
          // EOL reached, only return buffer if we didn't completely fill/overflow it
          if (buffer.size() > INPUT_SIZE) {
            return null;
          } else {
            return buffer.toString();
          }
        } else if (buffer.size() <= INPUT_SIZE) {
          buffer.write(b);
        }
      }
    }
  }

}
