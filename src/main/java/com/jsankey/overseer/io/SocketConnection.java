/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonException;

import com.google.common.annotations.VisibleForTesting;
import com.jsankey.overseer.Executive;
import com.jsankey.overseer.Executive.Status;
import com.jsankey.overseer.Executive.StatusListener;


/**
 * Handles a single connection to the program from a network peer.
 */
public class SocketConnection implements Runnable, StatusListener {

  private static final Logger LOG = Logger.getLogger(SocketConnection.class.getCanonicalName());

  private final Socket socket;
  private final Executive executive;
  @VisibleForTesting ConnectionParser parser;

  /**
   * Constructs a new connection using the supplied socket.
   */
  private SocketConnection(Socket socket, Executive executive) throws IOException {
    try {
      this.socket = socket;
      this.executive = executive;
      this.parser = new TextConnectionParser(socket);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Exception creating socket connection", e);
      throw e;
    }
  }

  /**
   * Constructs a new connection using the supplied socket.
   */
  public static SocketConnection from(Socket socket, Executive executive) throws IOException {
    return new SocketConnection(socket, executive);
  }

  @Override
  public void run() {
    LOG.info(String.format("Starting connection thread for %s", parser.getSocketName()));
    executive.registerListener(this);
    try {
      while (true) {
        Command command = parser.receiveInput();
        if (command != null) {
          command.execute(parser, executive);
        }
      }
    } catch (InterruptedException e) {
      // Valid exception caused to initiate shutdown.
    } catch (IOException|JsonException e) {
      LOG.log(Level.WARNING, "Exception streaming socket connection", e);
    } finally {
      LOG.info(String.format("Finishing connection thread for %s", parser.getSocketName()));
      executive.unregisterListener(this);
      try {
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Exception closing socket", e);
      }
    }
  }

  @Override
  public void receiveStatus(Status status) {
    try {
      Command.STATUS.execute(parser, executive);
    } catch (JsonException e) {
      // If we fail once, we'll probably fail again. Shut the socket down so the executive
      // thread doesn't  have to deal with this.
      LOG.warning(String.format("Exception receiving status in connection %s, closing socket",
          parser.getSocketName(), e));
      parser.initiateClose();
    }
  }
}
