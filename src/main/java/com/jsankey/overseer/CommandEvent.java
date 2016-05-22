package com.jsankey.overseer;

import java.io.Serializable;
import java.time.Instant;

import com.google.common.base.Preconditions;

/**
 * Represents a single execution of a command, in terms of the time execution
 * started and completed and the final outcome.
 * 
 * @author Jody
 */
public class CommandEvent implements Serializable {
  
  /** The exit code used when a command was forcible terminated instead of ending naturally. */
  public static final int ENFORCED_TERMINATION = 999;
  private static final long serialVersionUID = -643219034946287968L;
  private final Instant start;
  private final Instant end;
  private final int exitCode;
    
  /**
   * Constructs a new {@link CommandEvent}.
   * 
   * @param start the {@link Instant} at which execution started.
   * @param end the [approximate] {@link Instant} at which execution ended.
   * @param exitCode the exit code returned upon completion.
   */
  public CommandEvent(Instant start, Instant end, int exitCode) {
    Preconditions.checkArgument(end.isAfter(start));
    this.start = start;
    this.end = end;
    this.exitCode = exitCode;
  }

  /**
   * Returns the {@link Instant} at which execution started.
   */
  public Instant getStart() {
    return start;
  }

  /**
   * Returns the [approximate] {@link Instant} at which execution ended.
   */
  public Instant getEnd() {
    return end;
  }

  /**
   * Returns the total number of milliseconds that the command was executing.
   */
  public long getDurationMillis() {
    return end.toEpochMilli() - start.toEpochMilli();
  }

  /**
   * Returns the exit code returned upon completion. 
   */
  public int getExitCode() {
    return exitCode;
  }
    
  /**
   * Returns true iff the command completed successfully based on its return code.
   */
  public boolean isSuccessful() {
    return exitCode == 0;
  }
}
