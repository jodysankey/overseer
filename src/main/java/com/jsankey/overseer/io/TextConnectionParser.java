/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonWriter;

/**
 * Handles a standard text based raw socket protocol.
 */
class TextConnectionParser extends ConnectionParser {

  private static final Logger LOG = Logger.getLogger(TextConnectionParser.class.getCanonicalName());

  public TextConnectionParser(Socket socket) throws IOException {
    super(socket);
  }

  @Override
  public Command receiveInput() throws InterruptedException, IOException {
    String line = readToLf();
    if (line == null) {
      LOG.info(String.format("Command too long on connection %s", getSocketName()));
    //} else if (line.equals(WebConnectionParser.WEBSOCKET_UPGRADE_START)) {
    } else {
      try {
        return Command.valueOf(line.toUpperCase());
      } catch (IllegalArgumentException e) {
        LOG.info(String.format("Unknown command on connection %s: %s", getSocketName(), line));
      }
    }
    return null;
  }

  @Override
  public synchronized void sendJson(JsonStructure json) {
    JsonWriter writer = Json.createWriter(output);
    writer.write(json);
    try {
      output.write("\n".getBytes());
    } catch (IOException e) {
      LOG.warning("Exception adding newline");
    }
  }
}
