package com.jsankey.overseer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

  /**
   * Constructs a new service listening on the specified port.
   */
  private SocketService(int port) throws IOException {
    serverSocket = new ServerSocket(port);
    threadPool = Executors.newFixedThreadPool(THREAD_LIMIT);
    threadPool.execute(new SocketAcceptor());
  }

  /**
   * Constructs a new service listening on the specified port.
   */
  public static SocketService from(int port) throws IOException {
    return new SocketService(port);
  }

  /**
   * Runnable that sits on the thread pool to watch for new connections.
   */
  private class SocketAcceptor implements Runnable {
    @Override
    public void run() {
      while (true) {
        try {
          SocketConnection connection = SocketConnection.from(serverSocket.accept());
          threadPool.execute(connection);
        } catch (IOException e) {
          // Connection already logged the problem, just wait for another.
        }
      }
    }
  }
}
