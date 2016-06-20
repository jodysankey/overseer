/*
 * Copyright (C) 2016 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
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
