package org.freethinkers.transcriber.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TimestampFormatterTest {
    @Test
    fun `formats milliseconds as hours minutes seconds and milliseconds`() {
        assertEquals("01:02:03.045", TimestampFormatter.format(3_723_045))
    }

    @Test
    fun `clamps negative timestamps to zero`() {
        assertEquals("00:00:00.000", TimestampFormatter.format(-1))
    }
}
