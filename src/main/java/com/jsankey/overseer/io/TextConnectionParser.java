/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.json.Json;
import javax.json.JsonStructure;

/**
 * Handles a standard text based raw socket protocol.
 */
class TextConnectionParser extends ConnectionParser {

  public TextConnectionParser(Socket socket) throws IOException {
    super(socket);
  }

  @Override
  public Command receiveInput()
      throws InterruptedException, IOException, UpgradeRequestedException {
    String line = readToLf();
    if (line == null) {
      LOG.info(String.format("Command too long on connection %s", getSocketName()));
    } else if (line.equals(WebConnectionParser.WEBSOCKET_UPGRADE_START)) {
      throw new UpgradeRequestedException();
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
  public void sendJson(JsonStructure json) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Json.createWriter(buffer).write(json);;
    buffer.write('\n');
    writeWithFlush(buffer.toByteArray());
  }
}
