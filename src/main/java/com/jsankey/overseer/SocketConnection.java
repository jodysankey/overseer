package com.jsankey.overseer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;

import com.google.common.base.Optional;


/**
 * Handles a single text based connection to the program from a network peer.
 *
 * @author Jody
 */
public class SocketConnection implements Runnable {

  private static final Logger LOG = Logger.getLogger(SocketConnection.class.getCanonicalName());

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
        executive.begin();
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
  private final BufferedReader input;
  private final OutputStream output;
  private final Executive executive;
  private boolean closeRequested;

  /**
   * Constructs a new connection using the supplied socket.
   */
  private SocketConnection(Socket socket, Executive executive) throws IOException {
    try {
      this.socket = socket;
      this.executive = executive;
      this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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
    Command.STATUS.execute(this, executive);
    try {
      do {
        String inputLine = input.readLine().toUpperCase();
        if (inputLine != null && inputLine.length() > 0) {
          try {
            Command.valueOf(inputLine).execute(this, executive);
          } catch (IllegalArgumentException e) {
            LOG.info(String.format("Unknown command on connection: %s", inputLine));
          }
        }
      } while (!closeRequested);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Exception streaming socket connection", e);
    } finally {
      LOG.info(String.format("Finishing connection thread for %s", getSocketName()));
      try {
        socket.close();
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Exception closing socket", e);
      }
    }
  }

  private String getSocketName() {
    return String.format("%s:%d", socket.getInetAddress().getHostAddress(), socket.getPort());
  }

  private synchronized void write(JsonStructure json) {
    JsonWriter writer = Json.createWriter(output);
    writer.write(json);
  }
}