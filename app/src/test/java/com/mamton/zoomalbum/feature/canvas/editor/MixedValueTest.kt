package com.mamton.zoomalbum.feature.canvas.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MixedValueTest {

    @Test
    fun `single element collection reduces to Same`() {
        assertEquals(MixedValue.Same(42), listOf(42).toMixedValue())
    }

    @Test
    fun `all-equal collection reduces to Same`() {
        assertEquals(MixedValue.Same("x"), listOf("x", "x", "x").toMixedValue())
    }

    @Test
    fun `divergent collection reduces to Mixed`() {
        assertEquals(MixedValue.Mixed, listOf(1, 2, 3).toMixedValue())
    }

    @Test
    fun `two-element collection with one disagreement reduces to Mixed`() {
        assertEquals(MixedValue.Mixed, listOf(1, 2).toMixedValue())
    }

    @Test
    fun `empty collection reduces to Mixed`() {
        assertEquals(MixedValue.Mixed, emptyList<Int>().toMixedValue())
    }

    @Test
    fun `reduction handles null entries`() {
        assertEquals(MixedValue.Same<Int?>(null), listOf<Int?>(null, null).toMixedValue())
        assertEquals(MixedValue.Mixed, listOf<Int?>(null, 1).toMixedValue())
    }
}
