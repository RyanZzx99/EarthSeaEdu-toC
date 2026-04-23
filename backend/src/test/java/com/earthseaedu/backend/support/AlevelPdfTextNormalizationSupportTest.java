package com.earthseaedu.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlevelPdfTextNormalizationSupportTest {

    @Test
    void normalizesScientificSymbolsAndSuperscripts() {
        assertThat(AlevelPdfTextNormalizationSupport.normalizeLine(" 6.0 \u00D7 10\u207B3 m\u00B2 "))
            .isEqualTo("6.0 \u00D7 10-3 m2");
        assertThat(AlevelPdfTextNormalizationSupport.normalizeLine("13 rad s\u207B\u00B9"))
            .isEqualTo("13 rad s-1");
        assertThat(AlevelPdfTextNormalizationSupport.normalizeLine("27 \u03A9 resistor"))
            .isEqualTo("27 \u03A9 resistor");
    }

    @Test
    void normalizesCommonMojibakeSequences() {
        assertThat(AlevelPdfTextNormalizationSupport.normalizeLine("\u00C3\u2014 10\u00E2\u02C6\u20193"))
            .isEqualTo("\u00D7 10-3");
        assertThat(AlevelPdfTextNormalizationSupport.normalizeLine("Turn off the timebase \u2713 Increase the y-gain"))
            .isEqualTo("Turn off the timebase \u2022 Increase the y-gain");
    }

    @Test
    void preservesParagraphBreaksForTextBlocks() {
        assertThat(AlevelPdfTextNormalizationSupport.normalizeTextBlock("First line\u00A0\n\nSecond\u207A line"))
            .isEqualTo("First line\n\nSecond+ line");
    }

    @Test
    void buildsSearchTextFromNormalizedContent() {
        assertThat(AlevelPdfTextNormalizationSupport.normalizeSearchText("A-Level Physics Unit 3, May/June 2022"))
            .isEqualTo(" a level physics unit 3 may june 2022 ");
    }
}
