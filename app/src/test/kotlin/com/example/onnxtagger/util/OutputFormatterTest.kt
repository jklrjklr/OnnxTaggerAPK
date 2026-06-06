package com.example.onnxtagger.util

import com.example.onnxtagger.data.model.ActOnExisting
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputFormatterTest {

    private fun apply(
        newContent: String,
        existing: String = "",
        prepend: String = "",
        append: String = "",
        mode: ActOnExisting,
        sep: String = "\n",
    ) = OutputFormatter.apply(
        newContent = newContent,
        existingAnchor = existing,
        prepend = prepend,
        append = append,
        actOnExisting = mode,
        separator = sep,
    )

    @Test
    fun `IGNORE with non-blank existing produces decorated without merging`() {
        val result = apply("new", existing = "old", mode = ActOnExisting.IGNORE)
        assertEquals("new", result)
    }

    @Test
    fun `IGNORE with blank existing produces decorated`() {
        val result = apply("new", existing = "", mode = ActOnExisting.IGNORE)
        assertEquals("new", result)
    }

    @Test
    fun `OVERWRITE produces decorated`() {
        val result = apply("new", existing = "old", mode = ActOnExisting.OVERWRITE)
        assertEquals("new", result)
    }

    @Test
    fun `APPEND joins existing and new with separator`() {
        val result = apply("bar", existing = "foo", mode = ActOnExisting.APPEND, sep = "\n")
        assertEquals("foo\nbar", result)
    }

    @Test
    fun `PREPEND puts new before existing with separator`() {
        val result = apply("bar", existing = "foo", mode = ActOnExisting.PREPEND, sep = "\n")
        assertEquals("bar\nfoo", result)
    }

    @Test
    fun `APPEND with blank existing produces decorated without leading separator`() {
        val result = apply("bar", existing = "", mode = ActOnExisting.APPEND, sep = "\n")
        assertEquals("bar", result)
    }

    @Test
    fun `prepend and append text are included in decorated`() {
        val result = apply("content", prepend = "START\n", append = "\nEND", mode = ActOnExisting.OVERWRITE)
        assertEquals("START\ncontent\nEND", result)
    }
}
