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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.json.JsonStructure;

/**
 * Interface for classes that can parse commands from and
 * send information to a socket using a particular protocol.
 */
abstract class ConnectionParser {

  private static final int INPUT_SIZE = 1024;
  protected static final int SOCKET_INPUT_CHECK_MILLIS = 200;

  protected static final Logger LOG = Logger.getLogger(ConnectionParser.class.getCanonicalName());

  private final Socket socket;
  private final InputStream input;
  private final OutputStream output;
  protected final Lock readLock;
  protected final Lock writeLock;

  private boolean closeRequested;

  public class UpgradeRequestedException extends Exception {
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
    this.readLock = new ReentrantLock();
    this.writeLock = new ReentrantLock();
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
   * @throws UpgradeRequestedException if the remote host asks to upgrade to a web socket
   */
  public abstract Command receiveInput()
      throws InterruptedException, IOException, UpgradeRequestedException;

  /**
   * Outputs a supplied {@link JsonStructure} on the socket.
   *
   * @throws IOException is an IOError occurs
   */
  public abstract void sendJson(JsonStructure json) throws IOException;

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
    // Hold our  own additional lock to ensure we receive a contiguous line
    readLock.lock();
    try {
      while (true) {
        int b = readByte();
        if (b == '\n') {
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
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Returns the next byte from the socket, waiting if necessary and handling shutdown requests
   * gracefully.
   *
   * @throws InterruptedException if close is requested
   * @throws IOException if an IOError occurs or EOF is reached
   */
  protected int readByte() throws InterruptedException, IOException {
    readLock.lock();
    try {
      while (true) {
        if (closeRequested) {
          throw new InterruptedException("Socket close requested");
        } else if (input.available() == 0) {
          Thread.sleep(SOCKET_INPUT_CHECK_MILLIS);
        } else {
          int b = input.read();
          if (b < 0) {
            throw new IOException("Read EOF from socket");
          }
          return b;
        }
      }
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Writes a byte array to the socket then flushes the buffer
   *
   * @param byteArray
   * @throws IOException if an IOError occurs
   */
  protected void writeWithFlush(byte[] byteArray) throws IOException {
    writeLock.lock();
    try {
      output.write(byteArray);
      output.flush();
    } finally {
      writeLock.unlock();
    }
  }
}
