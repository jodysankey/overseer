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
import com.jsankey.overseer.io.ConnectionParser.UpgradeRequestedException;


/**
 * Handles a single connection to the program from a network peer.
 */
public class SocketConnection implements Runnable, StatusListener {

  private static final Logger LOG = Logger.getLogger(SocketConnection.class.getCanonicalName());

  private final Socket socket;
  private final Executive executive;
  @VisibleForTesting volatile ConnectionParser parser;

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
        try {
          Command command = parser.receiveInput();
          if (command != null) {
            command.execute(parser, executive);
          }
        } catch (UpgradeRequestedException e) {
          attemptUpgrade();
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
      } catch (IOException e) {
        // Common if the client already closed its end, carry on and close regardless.
        LOG.log(Level.FINE, "Exception shutting down socket", e);
      }
      try {
        socket.close();
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Exception closing socket", e);
      }
    }
  }

  @Override
  public void receiveStatus(Status status) {
    // Occasionally we'll null out the parser during an upgrade, if so just discard the update.
    // Take a local copy since the connection thread may change the field at any time.
    ConnectionParser currentParser = parser;
    if (currentParser != null) {
      try {
        Command.STATUS.execute(currentParser, executive);
      } catch (IOException e) {
        // If we fail once, we'll probably fail again. Shut the socket down so the executive
        // thread doesn't have to deal with this.
        LOG.log(Level.WARNING, String.format(
            "Exception receiving status in connection %s, closing socket",
            currentParser.getSocketName()), e);
        currentParser.initiateClose();
      }
      if (status == Status.TERMINATED) {
        LOG.info(String.format(
            "Sending close on termination for %s", currentParser.getSocketName()));
        try {
          Command.CLOSE.execute(currentParser, executive);
        } catch (IOException e) {
          // Close exceptions are common if client initiated shutdown - ignore
        }
      }
    }
  }

  /**
   * Attempts to safely replace the current parser with a websocket upgraded one.
   */
  private void attemptUpgrade() {
    // Suspend the old parser while we're working, and try to create a new one
    ConnectionParser oldParser = parser;
    parser = null;
    LOG.info("Starting attempt to upgrade to websocket");
    try {
      WebConnectionParser newParser = new WebConnectionParser(socket);
      parser = newParser.upgrade() ? newParser : oldParser;
    } catch (InterruptedException|IOException e) {
      // Interrupted exception shouldn't happen during upgrade since the parser isn't handling any
      // traffic yet. In the case of an IO exception just fallback to the old parser.
      LOG.log(Level.WARNING,
          String.format("Exception upgrading socket %s", oldParser.getSocketName()), e);
      parser = oldParser;
    }
  }
}
