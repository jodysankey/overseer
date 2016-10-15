/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.overseer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

/**
 * Defines a configuration for the application, and creates this configuration
 * from command line options.
 *
 * @author Jody
 */
public class Configuration {

  public static final String APP_NAME = "Overseer";
  public static final String VERSION_STRING = "0.0.4";

  // Statics for the parser and each of its component spec elements.
  private static final OptionParser PARSER;
  private static final ArgumentAcceptingOptionSpec<String> SSID_SPEC;
  private static final ArgumentAcceptingOptionSpec<String> LOG_FILE_SPEC;
  private static final ArgumentAcceptingOptionSpec<String> STATUS_FILE_SPEC;
  private static final ArgumentAcceptingOptionSpec<Integer> SOCKET_SPEC;
  private static final ArgumentAcceptingOptionSpec<Integer> RUN_INTERVAL_SPEC;
  private static final OptionSpec<Void> HELP_SPEC;
  private static final OptionSpec<Void> VERSION_SPEC;
  private static final ArgumentAcceptingOptionSpec<String> COMMAND_SPEC;

  static {
    PARSER = new OptionParser();
    SSID_SPEC = PARSER
        .accepts("ssid", "Only attempt to run commands while connected to this wifi SSID.")
        .withRequiredArg();
    LOG_FILE_SPEC = PARSER
        .accepts("log_file", "Location of log file. Log will be output to stdout if ommited.")
        .withRequiredArg();
    STATUS_FILE_SPEC = PARSER
        .accepts("status_file", "Optional file to cache and restore command execution status, "
            + "last run times and states to be maintained through power cycles.")
        .withRequiredArg();
    RUN_INTERVAL_SPEC = PARSER
        .accepts("run_interval", "Minimum time between attempted command executions, in seconds.")
        .withRequiredArg()
        .ofType(Integer.class)
        .defaultsTo(300);
    HELP_SPEC = PARSER
        .accepts("help", "Prints this help string.")
        .forHelp();
    VERSION_SPEC = PARSER
        .accepts("version", "Prints version information.")
        .forHelp();
    COMMAND_SPEC = PARSER
        .accepts("command", "Command to be executed periodically. May be specified multiple times.")
        .requiredUnless(HELP_SPEC, VERSION_SPEC)
        .withRequiredArg();
    SOCKET_SPEC = PARSER
        .accepts("socket", "Socket to listen for interactive commands.")
        .withRequiredArg()
        .ofType(Integer.class);
  }

  // Instance members store the results of parsing a particular input. 
  private final Optional<String> ssid;
  private final Optional<String> logFile;
  private final Optional<String> statusFile;
  private final Optional<Integer> socket;
  private final int runIntervalSec;
  private final ImmutableList<String> commands;
  private final boolean helpRequested;
  private final boolean versionRequested;

  private Configuration(String[] arguments) throws RuntimeException {
    OptionSet options = PARSER.parse(arguments);
    ssid = optionalFromOption(options, SSID_SPEC);
    logFile = optionalFromOption(options, LOG_FILE_SPEC);
    statusFile = optionalFromOption(options, STATUS_FILE_SPEC);
    socket = optionalFromOption(options, SOCKET_SPEC);
    runIntervalSec = options.valueOf(RUN_INTERVAL_SPEC);
    helpRequested = options.has(HELP_SPEC);
    versionRequested = options.has(VERSION_SPEC);
    commands = ImmutableList.copyOf(options.valuesOf(COMMAND_SPEC));
  }

  /**
   * Construct a new {@link Configuration} from a command line argument array.
   * 
   * @param arguments Array of strings as supplied a program initialization
   */
  public static Configuration from(String[] arguments) throws OptionException {
    return new Configuration(arguments);
  }

  /**
   * Writes the program version information to a supplied {@link OutputStream}.
   */
  public static void printVersionOn(OutputStream sink) throws IOException {
    OutputStreamWriter writer = new OutputStreamWriter(sink);
    writer.write((String.format("  %s version %s%n", APP_NAME, VERSION_STRING)));
    writer.flush();
  }

  /**
   * Writes the command line syntax to a supplied {@link OutputStream}.
   */
  public static void printHelpOn(OutputStream sink) throws IOException {
    PARSER.printHelpOn(sink);
  }

  /**
   * Returns the wifi SSID used to gate execution of commands, if supplied.
   */
  public Optional<String> getSsid() {
    return ssid;
  }

  /**
   * Returns the path used to write a log file, if supplied.
   */
  public Optional<String> getLogFile() {
    return logFile;
  }

  /**
   * Returns the path used to write and read a status file, if supplied.
   */
  public Optional<String> getStatusFile() {
    return statusFile;
  }

  /**
   * Returns the socket on which to listen for interactive connections.
   */
  public Optional<Integer> getSocket() {
    return socket;
  }

  /**
   * Returns the minimum time between attempted command executions, in seconds.
   */
  public int getRunIntervalSec() {
    return runIntervalSec;
  }

  /**
   * Returns the commands to be executed periodically.
   */
  public ImmutableList<String> getCommands() {
    return commands;
  }

  /**
   * Returns true iff the help string has been requested.
   */
  public boolean isHelpRequested() {
    return helpRequested;
  }

  /**
   * Returns true iff the version string has been requested.
   */
  public boolean isVersionRequested() {
    return versionRequested;
  }

  /**
   * Returns an {@link Optional} of a value in the supplied {@link OptionSet} if present,
   * or an empty {@link Optional} otherwise.
   */
  private static <T> Optional<T> optionalFromOption(
      OptionSet options, ArgumentAcceptingOptionSpec<T> spec) {
    if (options.has(spec)) {
      return Optional.of(options.valueOf(spec));
    } else {
      return Optional.<T>absent();
    }
  }

}

