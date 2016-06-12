package com.jsankey.overseer;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.jsankey.util.ReadOnlyIterator;

/**
 * Represents an ordered set of executions for a command, in terms of the time execution
 * started and completed and the final outcome. Maximum size is constrained to a fixed value
 * and old executions are discarded if necessary.
 *
 * @author Jody
 */
public class CommandHistory extends AbstractCollection<CommandEvent> implements Serializable {

  private static final long serialVersionUID = 2413546670260463936L;

  /** The maximum number of executions that are retained. */
  @VisibleForTesting
  static final int MAX_HISTORY_SIZE = 10;

  /** Logger for the current class. */
  private static final Logger LOG = Logger.getLogger(CommandHistory.class.getCanonicalName());

  /** The command whose history we store. */
  private final String command;

  /** Queue of {@link ExecutionEvent} objects for each execution. */
  private final Deque<CommandEvent> events;

  /**
   * Constructs a new instance for the specified command.
   */
  @VisibleForTesting
  public CommandHistory(String command) {
    this.command = command;
    this.events = new ArrayDeque<CommandEvent>(MAX_HISTORY_SIZE);
  }

  /**
   * Returns the command whose history we store.
   */
  public String getCommand() {
    return command;
  }

  @Override
  public boolean add(CommandEvent event) {
    synchronized (this) {
      // Ensure we keep the history monotonically increasing. This problem could occur in cases
      // of system clock problems, so don't throw an exception here, just ignore.
      if (!events.isEmpty() && event.getEnd().isBefore(events.getLast().getEnd())) {
        LOG.warning(String.format("Ignoring new event at earlier time than history (%d < %d)",
            event.getEnd().getEpochSecond(), events.getLast().getEnd().getEpochSecond()));
        return false;
      }
      // Mutate our deque for this command, clearing space if necessary.
      if (events.size() >= MAX_HISTORY_SIZE) {
        events.removeFirst();
      }
      events.addLast(event);
      return true;
    }
  }

  @Override
  public int size() {
    return events.size();
  }

  /**
   * Returns the final {@link CommandEvent} in the history, or null if no events exist.
   */
  public CommandEvent getLast() {
    return events.isEmpty() ? null : events.getLast();
  }

  @Override
  public Iterator<CommandEvent> iterator() {
    return new ReadOnlyIterator<CommandEvent>(events.iterator());
  }
}
