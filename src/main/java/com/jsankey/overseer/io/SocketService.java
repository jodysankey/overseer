/*
 * Copyright (C) 2016-2026 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jsankey.overseer.Executive;

/**
 * Accepts connections to the program via a server socket. Each connection is handled by an
 * instance of {@link SocketConnection}.
 */
public class SocketService {

  /** Maximum number of simultaneous connections. Further connections wait for a free thread. */
  private static final int CONNECTION_LIMIT = 4;
  /** Total threads in the pool: one per connection plus one for the {@link SocketAcceptor}. */
  private static final int THREAD_LIMIT = CONNECTION_LIMIT + 1;
  /** Delay before retrying after a failure to accept a connection, to avoid a tight loop. */
  private static final int ACCEPT_RETRY_MILLIS = 1000;

  private static final Logger LOG = Logger.getLogger(SocketService.class.getCanonicalName());

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
      LOG.info("Starting server socket listening on port " + serverSocket.getLocalPort());
      while (!serverSocket.isClosed()) {
        Socket socket;
        try {
          socket = serverSocket.accept();
        } catch (IOException e) {
          if (serverSocket.isClosed()) {
            // Normal during shutdown, the loop will exit.
            break;
          }
          // Something like running out of file descriptors; log it and back off before retrying.
          LOG.log(Level.WARNING, "Exception accepting connection", e);
          try {
            Thread.sleep(ACCEPT_RETRY_MILLIS);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
          continue;
        }
        try {
          threadPool.execute(SocketConnection.from(socket, executive));
        } catch (IOException e) {
          // Connection already logged the problem, just clean up and wait for another.
          closeQuietly(socket);
        } catch (RejectedExecutionException e) {
          // The service was closed while we were accepting this connection.
          closeQuietly(socket);
          break;
        }
      }
      LOG.info("Server socket closed, no longer accepting connections");
    }

    private void closeQuietly(Socket socket) {
      try {
        socket.close();
      } catch (IOException e) {
        // Nothing useful to do, the connection is being abandoned anyway.
      }
    }
  }
}
