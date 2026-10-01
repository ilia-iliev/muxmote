package dev.muxmote.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionTest {
  @Test
  fun typingAtAtTheStartOrAfterASpaceOpensThePicker() {
    assertTrue(typedMention("", "@", 1))
    assertTrue(typedMention("see ", "see @", 5))
    assertTrue(typedMention("a\nb", "a\n@b", 3))
  }

  @Test
  fun anAtInsideAWordDoesNot() {
    assertFalse(typedMention("me", "me@", 3))
  }

  @Test
  fun onlyTypingItDoes() {
    // The cursor moving next to an existing @, or deleting after one.
    assertFalse(typedMention("@", "@", 1))
    assertFalse(typedMention("@a", "@", 1))
    // Pasting text that ends in @.
    assertFalse(typedMention("", "x @", 3))
  }
}
