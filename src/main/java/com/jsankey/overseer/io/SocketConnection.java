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
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.jsankey.overseer.Configuration;
import com.jsankey.overseer.Executive;
import com.jsankey.overseer.Executive.Status;
import com.jsankey.overseer.Executive.StatusListener;
import com.jsankey.overseer.history.CommandEvent;
import com.jsankey.overseer.history.CommandHistory;


/**
 * Handles a single text based connection to the program from a network peer.
 *
 * @author Jody
 */
public class SocketConnection implements Runnable, StatusListener {

  private static final Logger LOG = Logger.getLogger(SocketConnection.class.getCanonicalName());
  private static final int INPUT_SIZE = 20;
  private static final int SOCKET_INPUT_CHECK_MILLIS = 200;

  /**
   * Enumeration of all commands accepted on the interface, with a method to perform each.
   */
  private enum Command {
    HELP("Returns the list of commands") {
      @Override
      public void execute(SocketConnection connection, Executive executive) {
        JsonArrayBuilder commandBuilder = Json.createArrayBuilder();
        for (Command command : Command.values()) {
          commandBuilder.add(Json.createObjectBuilder().add(command.name(), command.help));
        }
        JsonObject json = Json.createObjectBuilder()
            .add("version", Configuration.VERSION_STRING)
            .add("commands", commandBuilder)
            .build();
        connection.write(json);
      }
    },
    RUN("Begins a new execution of the commands immediately") {
      @Override
      public void execute(SocketConnection connection, Executive executive) {
        executive.runNow();
      }
    },
    STATUS("Returns a summary of the current status") {
      @Override
      public void execute(SocketConnection connection, Executive executive) {
        Optional<Instant> lastStart = executive.getHistory().getOldestStart();
        JsonObject json = Json.createObjectBuilder()
            .add("status", executive.getStatus().toString())
            .add("last_start_ms", lastStart.isPresent()
                ? String.valueOf(lastStart.get().toEpochMilli()) : "NONE")
            .build();
        connection.write(json);
      }
    },
    HISTORY("Returns full history for all commands") {
      @Override
      public void execute(SocketConnection connection, Executive executive) {
        JsonArrayBuilder jsonCommands = Json.createArrayBuilder();
        for (CommandHistory command : executive.getHistory()) {
          JsonArrayBuilder jsonExecutions = Json.createArrayBuilder();
          for (CommandEvent event : command) {
            jsonExecutions.add(Json.createObjectBuilder()
                .add("start_ms", event.getStart().toEpochMilli())
                .add("end_ms", event.getEnd().toEpochMilli())
                .add("exit_code", event.getExitCode()));
          }
          jsonCommands.add(Json.createObjectBuilder()
              .add("command", command.getCommand())
              .add("executions", jsonExecutions));
        }
        connection.write(jsonCommands.build());
      }
    },
    CLOSE("Closes the current connection") {
      @Override
      public void execute(SocketConnection connection, Executive executive) {
        connection.closeRequested = true;
      }
    },
    SHUTDOWN("Begins a graceful shutdown") {
      @Override
      public void execute(SocketConnection connection, Executive executive) {
        executive.terminate();
      }
    };

    private String help;

    private Command(String help) {
      this.help = help;
    }

    /**
     * Perform the command, mutating or reading from the supplied {@link Executive} as required.
     */
    public abstract void execute(SocketConnection connection, Executive executive);
  }

  private final Socket socket;
  private final InputStream input;
  private final OutputStream output;
  private final Executive executive;
  @VisibleForTesting boolean closeRequested;

  /**
   * Constructs a new connection using the supplied socket.
   */
  private SocketConnection(Socket socket, Executive executive) throws IOException {
    try {
      this.socket = socket;
      this.executive = executive;
      this.input = socket.getInputStream();
      this.output = socket.getOutputStream();
      this.closeRequested = false;
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
    LOG.info(String.format("Starting connection thread for %s", getSocketName()));
    executive.registerListener(this);
    try {
      while (true) {
        Command command = readCommand();
        if (command != null) {
          command.execute(this, executive);
        }
      }
    } catch (InterruptedException e) {
      // Valid exception caused to initiate shutdown.
    } catch (IOException|JsonException e) {
      LOG.log(Level.WARNING, "Exception streaming socket connection", e);
    } finally {
      LOG.info(String.format("Finishing connection thread for %s", getSocketName()));
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
      Command.STATUS.execute(this, executive);
    } catch (JsonException e) {
      // If we fail once, we'll probably fail again. Shut the socket down so the executive
      // thread doesn't  have to deal with this.
      LOG.warning(String.format("Exception receiving status in connection %s, closing socket",
          getSocketName(), e));
      closeRequested = true;
    }
  }

  /**
   * Returns the hostname and port of the remote socket.
   */
  private String getSocketName() {
    return String.format("%s:%d", socket.getInetAddress().getHostAddress(), socket.getPort());
  }

  /**
   * Outputs a supplied {@link JsonStructure} on the socket with LF termination.
   */
  private synchronized void write(JsonStructure json) {
    JsonWriter writer = Json.createWriter(output);
    writer.write(json);
    try {
      output.write("\n".getBytes());
    } catch (IOException e) {
      LOG.warning("Exception adding newline");
    }
  }

  /**
   * Returns the next LF terminated command from the socket, or null if the line exceeds the max
   * provisioned length. Adds log entries to document any problems parsing a valid command.
   *
   * @throws InterruptedException if close is requested
   * @throws IOException is an IOError occurs or EOF is reached
   */
  private Command readCommand() throws InterruptedException, IOException {
    String line = readLine();
    if (line == null) {
      LOG.info(String.format("Command too long on connection %s", getSocketName()));
    } else {
      try {
        return Command.valueOf(line.toUpperCase());
      } catch (IllegalArgumentException e) {
        LOG.info(String.format("Unknown command on connection %s: %s", getSocketName(), line));
      }
    }
    return null;
  }

  /**
   * Returns the next LF terminated command from the socket, or null if the line exceeds the max
   * provisioned length.
   *
   * @throws InterruptedException if close is requested
   * @throws IOException is an IOError occurs or EOF is reached
   */
  private String readLine() throws InterruptedException, IOException {
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
