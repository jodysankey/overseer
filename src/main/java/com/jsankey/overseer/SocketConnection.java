package com.jsankey.overseer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Handles a single text based connection to the program from a network peer.
 *
 * @author Jody
 */
public class SocketConnection implements Runnable {

  private static final Logger LOG = Logger.getLogger(SocketConnection.class.getCanonicalName());

  private final Socket socket;
  private final BufferedReader input;
  // TODO(jody): BufferedWriter or some such
  private final PrintWriter output;


  /**
   * Constructs a new connection using the supplied socket.
   * @throws IOException 
   */
  private SocketConnection(Socket socket) throws IOException {
    try {
      this.socket = socket;
      this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      this.output = new PrintWriter(socket.getOutputStream());
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Exception creating socket connection", e);
      throw e;
    }
  }

  /**
   * Constructs a new connection using the supplied socket.
   * @throws IOException 
   */
  public static SocketConnection from(Socket socket) throws IOException {
    return new SocketConnection(socket);
  }

  @Override
  public void run() {
    output.println("hi there!!");
    output.flush();
    try {
      String line;
      while ((line = input.readLine()) != null && line.length() > 0) {
        output.print("Our survey said: " + line);
        output.flush();
      }
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Exception streaming socket connection", e);
    }

    try {
      socket.close();
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Exception closing socket", e);
    }
  }
}
