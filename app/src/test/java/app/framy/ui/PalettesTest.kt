package app.framy.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PalettesTest {
    @Test fun presetsAreTheFiveSoftOnes() =
        assertEquals(setOf("slate", "sage", "teal", "sand", "rose"), Palettes.presets.keys)

    @Test fun courseColourIsStablePerCourse() =
        assertEquals(Palettes.course("slate", 7, false), Palettes.course("slate", 7, false))

    @Test fun neighbouringCoursesDiffer() =
        assertNotEquals(Palettes.course("slate", 7, false), Palettes.course("slate", 8, false))

    @Test fun darkAndLightDiffer() =
        assertNotEquals(Palettes.presets.getValue("sage").first.surface, Palettes.presets.getValue("sage").second.surface)
}
