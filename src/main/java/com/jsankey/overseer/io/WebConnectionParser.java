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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.xml.bind.DatatypeConverter;

/**
 * Handles a WebSocket based protocol including upgrade from a raw socket.
 */
public class WebConnectionParser extends ConnectionParser {

  private static final Logger LOG = Logger.getLogger(WebConnectionParser.class.getCanonicalName());

  private static final Pattern WEBSOCKET_KEY_PATTERN = Pattern.compile("Sec-WebSocket-Key: (.*)\r");
  private static final String WEBSOCKET_UPGRADE_RESPONSE =
      "HTTP/1.1 101 Switching Protocols\r\n"
      + "Upgrade: websocket\r\n"
      + "Connection: Upgrade\r\n"
      + "Sec-WebSocket-Accept: %s\r\n\r\n";
  private static final String WEBSOCKET_UPGRADE_FAILURE = "400 Bad Request\r\n\r\n";
  private static final String WEBSOCKET_HASH_SUFFIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

  static final String WEBSOCKET_UPGRADE_START = "GET /overseer HTTP/1.1\r";

  /**
   * Simple representation of a decoded packet.
   */
  private static class Packet {
    public enum OpCode {
      TEXT(0x1),
      CLOSE(0x8),
      PING(0x9),
      PONG(0xA);

      private final int value;
      private static final Map<Integer, OpCode> map = new HashMap<>();

      private OpCode(int value) {
        this.value = value;
      }

      public static OpCode fromInteger(int value) {
        if (map.isEmpty()) {
          for (OpCode c : OpCode.values()) {
            map.put(c.value, c);
          }
        }
        return map.get(value);
      }
    }

    public final OpCode opCode;
    public final byte[] data;

    public Packet(OpCode opCode, byte[] data) {
      this.opCode = opCode;
      this.data = data;
    }
  }

  /**
   * Handles a web socket based socket protocol, including the upgrade process.
   */
  public WebConnectionParser(Socket socket) throws IOException {
    super(socket);
  }

  /**
   * Attempts to upgrade the socket to the WebSocket protocol.
   * 
   * @return true on success or false if the upgrade could not be negotiated successfully.
   */
  public boolean upgrade() throws InterruptedException, IOException {
    try {
      // Read text lines until we get a blank, storing the KEY
      String key = null;
      while (true) {
        String line = readToLf();
        if (line == null) {
          return false;
        } else if (line.equals("\r")) {
          break;
        }
        Matcher keyMatch = WEBSOCKET_KEY_PATTERN.matcher(line);
        if (keyMatch.matches()) {
          key = keyMatch.group(1);
        }
      }

      // Verify the key was received
      if (key == null) {
        LOG.info("Denying websocket upgrade request without key");
        output.write(WEBSOCKET_UPGRADE_FAILURE.getBytes());
      } else {
        String response = String.format(
            WEBSOCKET_UPGRADE_RESPONSE,
            DatatypeConverter.printBase64Binary(MessageDigest.getInstance("SHA-1")
                .digest((key + WEBSOCKET_HASH_SUFFIX).getBytes("UTF-8"))));
        output.write(response.getBytes());
        output.flush();
        LOG.info("Successfully upgraded to websocket");
        return true;
      }
    } catch (NoSuchAlgorithmException e) {
      LOG.warning("SHA-1 algorithm missing from the crypto provider");
    }
    return false;
  }


  @Override
  public Command receiveInput() throws InterruptedException, IOException {
    Packet packet = readPacket();
    LOG.info(String.format("Received packet (%s/%s)", packet.opCode.name(), new String(packet.data)));
    switch (packet.opCode) {
      //TODO(jody): Handle close and ping pong
      case PING:
      case PONG:
      case CLOSE:
        return null;
      case TEXT:
        try {
          return Command.valueOf(new String(packet.data).toUpperCase());
        } catch (IllegalArgumentException e) {
          LOG.info(
              String.format("Unknown command on web socket %s: %s", getSocketName(), packet.data));
        }
    }
    return null;

  }

  @Override
  public void sendJson(JsonStructure json) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    JsonWriter writer = Json.createWriter(buffer);
    writer.write(json);
    try {
      writePacket(new Packet(Packet.OpCode.TEXT, buffer.toByteArray()));
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Exception writing JSON to WebSocket", e);
    }

  }

  @Override
  public void initiateClose() {
    // TODO Auto-generated method stub

  }

  /**
   * Read a complete WebSocket packet from the socket.
   *
   * @throws InterruptedException if close is requested
   * @throws IOException if an IOError occurs or EOF is reached
   */
  private Packet readPacket() throws InterruptedException, IOException {

    int opCodeByte = readByte();
    if (opCodeByte < 0x80) {
      throw new IOException("Received frame without FIN set.");
    }
    Packet.OpCode opCode = Packet.OpCode.fromInteger(opCodeByte & 0xF);
    if (opCode == null) {
      throw new IOException(String.format("Unsupported opcode: %02x", opCodeByte & 0xF));
    }

    int lengthByte = readByte();
    if (lengthByte < 0x80) {
      throw new IOException("Received unmasked frame from client.");
    }
    long length = 0;
    if ((lengthByte & 0x7F) < 126) {
      length = lengthByte & 0x7F;
    } else {
      LOG.info(String.format("Received long length byte: %d", lengthByte & 0x7F));
      for (int i = 0; i < (((lengthByte & 0x7F) == 126) ? 2 : 8); i++) {
        length = (length << 8) + readByte();
      }
    }

    int mask[] = new int[4];
    for (int i = 0; i < 4; i++) {
      mask[i] = readByte();
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    for (long i = 0; i < length; i++) {
      buffer.write(readByte() ^ mask[(int)i % 4]);
    }
    return new Packet(opCode, buffer.toByteArray());
  }

  /**
   * Write a WebSocket packet to the socket.
   *
   * @throws InterruptedException if close is requested
   * @throws IOException if an IOError occurs or EOF is reached
   */
  private void writePacket(Packet packet) throws IOException {
    output.write(0x80 | packet.opCode.value);
    if (packet.data.length < 126) {
      output.write(packet.data.length);
    } else if (packet.data.length < Integer.MAX_VALUE) {
      output.write(127);
      output.write(ByteBuffer.allocate(4).putInt(packet.data.length).array());
    } else {
      throw new IOException("Sending packets longer than 2GB is not supported.");
    }
    output.write(packet.data);
    output.flush();
  }
}
