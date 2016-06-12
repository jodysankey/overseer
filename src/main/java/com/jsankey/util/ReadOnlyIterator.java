package com.jsankey.util;

import java.util.Iterator;

/**
 * Simple wrapping iterator class to return a read only perspective on an iterator.
 */
public class ReadOnlyIterator<T> implements Iterator<T> {

  private final Iterator<T> wrapped;

  public ReadOnlyIterator(Iterator<T> iterator) {
    this.wrapped = iterator;
  }

    @Override
  public boolean hasNext() {
   return wrapped.hasNext();
  }

  @Override
    public T next() {
    return wrapped.next();
  }
}
