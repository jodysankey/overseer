/*
 * Copyright (C) 2016-2026 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

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
    } else if (line.startsWith(WebConnectionParser.WEBSOCKET_UPGRADE_START)) {
      throw new UpgradeRequestedException();
    } else {
      try {
        // Trim to tolerate CRLF line endings (e.g. from telnet) and stray whitespace.
        return Command.valueOf(line.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        LOG.info(String.format("Unknown command on connection %s: %s", getSocketName(), line));
      }
    }
    return null;
  }

  @Override
  public void sendJson(JsonStructure json) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Json.createWriter(buffer).write(json);
    buffer.write('\n');
    writeWithFlush(buffer.toByteArray());
  }
}
