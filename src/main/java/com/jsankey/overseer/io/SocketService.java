/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.jsankey.overseer.Executive;

/**
 * Accepts text based connections to the program via a server socket. Each connection
 * is handled by an instance of {@link SocketConnection}.
 *
 * @author Jody
 */
public class SocketService {

  private static final int THREAD_LIMIT = 4;

  private final ServerSocket serverSocket;
  private final ExecutorService threadPool;
  private final Executive executive;

  /**
   * Constructs a new service listening on the specified port.
   */
  private SocketService(int port, Executive executive) throws IOException {
    this.executive = executive;
    this.serverSocket = new ServerSocket(port);
    this.threadPool = Executors.newFixedThreadPool(THREAD_LIMIT);
    threadPool.execute(new SocketAcceptor());
  }

  /**
   * Constructs a new service listening on the specified port.
   */
  public static SocketService from(int port, Executive executive) throws IOException {
    return new SocketService(port, executive);
  }

  /**
   * Closes the service and frees all associated resources.
   */
  public void close() {
    threadPool.shutdown();
    try {
      serverSocket.close();
    } catch (IOException e) {
      // Really nothing effective to do about this since we're already in shutdown
    }
  }

  /**
   * Runnable that sits on the thread pool to watch for new connections.
   */
  private class SocketAcceptor implements Runnable {
    @Override
    public void run() {
      while (true) {
        try {
          SocketConnection connection = SocketConnection.from(serverSocket.accept(), executive);
          threadPool.execute(connection);
        } catch (IOException e) {
          // Connection already logged the problem, just wait for another.
        }
      }
    }
  }
}
