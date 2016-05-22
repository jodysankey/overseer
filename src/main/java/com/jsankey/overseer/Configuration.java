package com.jsankey.overseer;

import java.io.IOException;
import java.io.OutputStream;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Defines a configuration for the application, and creates this configuration
 * from command line options.
 * 
 * @author Jody
 *
 */
public class Configuration {

  private static final OptionParser PARSER;
  private static final ArgumentAcceptingOptionSpec<String> SSID_SPEC;
  private static final ArgumentAcceptingOptionSpec<String> LOG_FILE_SPEC;
  private static final ArgumentAcceptingOptionSpec<String> STATUS_FILE_SPEC;
  private static final ArgumentAcceptingOptionSpec<Integer> CHECK_INTERVAL_SPEC;
  private static final ArgumentAcceptingOptionSpec<Integer> RUN_INTERVAL_SPEC;
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
    CHECK_INTERVAL_SPEC = PARSER
        .accepts("check_interval",
            "Minimum time between checks for execution eligibility, in seconds.")
        .withRequiredArg()
        .ofType(Integer.class)
        .defaultsTo(30);
    RUN_INTERVAL_SPEC = PARSER
        .accepts("run_interval", "Minimum time between attempted command executions, in seconds.")
        .withRequiredArg()
        .ofType(Integer.class)
        .defaultsTo(300);
    COMMAND_SPEC = PARSER
        .accepts("command", "Command to be executed periodically. May be specified multiple times.")
        .withRequiredArg();
  }
  
  private final Optional<String> ssid;
  private final Optional<String> logFile;
  private final Optional<String> statusFile;
  private final int checkIntervalSec;
  private final int runIntervalSec;
  private final ImmutableList<String> commands;

  private Configuration(String[] arguments) throws OptionException {
    OptionSet options = PARSER.parse(arguments);
    ssid = optionalFromOption(options, SSID_SPEC);
    logFile = optionalFromOption(options, LOG_FILE_SPEC);
    statusFile = optionalFromOption(options, STATUS_FILE_SPEC);
    checkIntervalSec = options.valueOf(CHECK_INTERVAL_SPEC);
    runIntervalSec = options.valueOf(RUN_INTERVAL_SPEC);
    commands = ImmutableList.copyOf(options.valuesOf(COMMAND_SPEC));
    // Would be much better to either make command globally required (can't because no API) or
    // construct an OptionException directly (can't because constructor is package private).
    if (commands.isEmpty()) {
      throw new RuntimeException("At least one --command option must be supplied");
    }
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
   * Returns the minimum time between checks for execution eligibility, in seconds.
   */
  public int getCheckIntervalSec() {
    return checkIntervalSec;
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

