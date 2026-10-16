/*
 * Copyright (C) 2026 Jody Sankey
 *
 * This software may be modified and distributed under the terms of the MIT license.
 * See the LICENSE.md file for details.
 */
package com.jsankey.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ReadOnlyIteratorTest {

  @Test
  public void testIteratesWrappedElements() {
    ReadOnlyIterator<String> iterator =
        new ReadOnlyIterator<String>(Arrays.asList("a", "b").iterator());
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("a");
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo("b");
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void testRemoveNotSupported() {
    List<String> list = new ArrayList<String>(Arrays.asList("a", "b"));
    ReadOnlyIterator<String> iterator = new ReadOnlyIterator<String>(list.iterator());
    iterator.next();
    try {
      iterator.remove();
      fail();
    } catch (UnsupportedOperationException e) {
      // Expected
    }
    assertThat(list).containsExactly("a", "b");
  }
}
