/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.IOException;
import java.time.Instant;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import com.google.common.base.Optional;
import com.jsankey.overseer.Configuration;
import com.jsankey.overseer.Executive;
import com.jsankey.overseer.history.CommandEvent;
import com.jsankey.overseer.history.CommandHistory;

/**
 * Enumeration of all commands accepted on the interface, with a method to perform each.
 */
enum Command {
  HELP("Returns the list of commands") {
    @Override
    public void execute(ConnectionParser parser, Executive executive) throws IOException {
      JsonArrayBuilder commandBuilder = Json.createArrayBuilder();
      for (Command command : Command.values()) {
        commandBuilder.add(Json.createObjectBuilder().add(command.name(), command.help));
      }
      JsonObject json = Json.createObjectBuilder()
          .add("version", Configuration.VERSION_STRING)
          .add("commands", commandBuilder)
          .build();
      parser.sendJson(json);
    }
  },
  RUN("Begins a new execution of the commands immediately") {
    @Override
    public void execute(ConnectionParser parser, Executive executive) {
      executive.runNow();
    }
  },
  STATUS("Returns a summary of the current status") {
    @Override
    public void execute(ConnectionParser parser, Executive executive) throws IOException {
      Optional<Instant> lastStart = executive.getHistory().getOldestStart();
      JsonObject json = Json.createObjectBuilder()
          .add("status", executive.getStatus().toString())
          .add("last_start_ms", lastStart.isPresent()
              ? String.valueOf(lastStart.get().toEpochMilli()) : "NONE")
          .build();
      parser.sendJson(json);
    }
  },
  HISTORY("Returns full history for all commands") {
    @Override
    public void execute(ConnectionParser parser, Executive executive) throws IOException {
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
      parser.sendJson(jsonCommands.build());
    }
  },
  VERSION("Returns software version") {
    @Override
    public void execute(ConnectionParser parser, Executive executive) throws IOException {
      JsonObject json = Json.createObjectBuilder()
          .add("version", Configuration.VERSION_STRING)
          .build();
      parser.sendJson(json);
    }
  },
  CLOSE("Closes the current connection") {
    @Override
    public void execute(ConnectionParser parser, Executive executive) {
      parser.initiateClose();
    }
  },
  SHUTDOWN("Begins a graceful shutdown") {
    @Override
    public void execute(ConnectionParser parser, Executive executive) {
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
  public abstract void execute(ConnectionParser parser, Executive executive) throws IOException;
}

