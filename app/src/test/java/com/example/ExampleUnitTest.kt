package com.example

import com.example.ui.theme.AppPalettes
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testAllPresetThemesHaveValidColorsAndHexCodes() {
        val presets = AppPalettes.allPresets
        assertTrue("Should have at least 8 preset themes", presets.size >= 8)

        for (palette in presets) {
            assertNotNull(palette.id)
            assertNotNull(palette.nameAr)
            assertNotNull(palette.pdfColors)

            // Validate that all hex colors start with '#' and have 7 characters (#RRGGBB)
            assertTrue("Invalid primaryDarkHex: ${palette.pdfColors.primaryDarkHex}", palette.pdfColors.primaryDarkHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            assertTrue("Invalid primaryNormalHex: ${palette.pdfColors.primaryNormalHex}", palette.pdfColors.primaryNormalHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            assertTrue("Invalid primaryLightHex: ${palette.pdfColors.primaryLightHex}", palette.pdfColors.primaryLightHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            assertTrue("Invalid accentHex: ${palette.pdfColors.accentHex}", palette.pdfColors.accentHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            assertTrue("Invalid goldHex: ${palette.pdfColors.goldHex}", palette.pdfColors.goldHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            assertTrue("Invalid rowAlternateHex: ${palette.pdfColors.rowAlternateHex}", palette.pdfColors.rowAlternateHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            assertTrue("Invalid subHeaderBgHex: ${palette.pdfColors.subHeaderBgHex}", palette.pdfColors.subHeaderBgHex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
        }
    }

    @Test
    fun testCustomThemeCreation() {
        val custom = AppPalettes.createCustom("#2E7D32", "#D32F2F")
        assertEquals("custom", custom.id)
        assertTrue(custom.pdfColors.primaryDarkHex.startsWith("#"))
        assertTrue(custom.pdfColors.accentHex.startsWith("#"))
    }
}
