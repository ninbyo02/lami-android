package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class LocalMultiImageAssessmentTest {
    private val fixtures = listOf(
        LocalMultiImageFixture("F", "cyan", "square"),
        LocalMultiImageFixture("B", "blue", "square"),
        LocalMultiImageFixture("H", "red", "star"),
    )

    @Test fun acceptsAllDistinctImagesInTheirActualNonAlphabeticOrder() {
        val score = LocalMultiImageAssessmentPolicy.assess("F: cyan:square\nB: blue:square\nH: red:star", fixtures)
        assertTrue(score.allRecognized)
        assertTrue(score.ordered)
        assertTrue(score.endpointsRecognized)
    }

    @Test fun recognitionAndOrderAreIndependent() {
        val score = LocalMultiImageAssessmentPolicy.assess("B: blue:square\nF: cyan:square\nH: red:star", fixtures)
        assertTrue(score.allRecognized)
        assertFalse(score.ordered)
    }

    @Test fun omissionsAreReportedEvenWhenEndpointsAreCorrect() {
        val score = LocalMultiImageAssessmentPolicy.assess("F: cyan:square\nH: red:star", fixtures)
        assertEquals(listOf("B"), score.missing)
        assertTrue(score.endpointsRecognized)
        assertFalse(score.allRecognized)
    }

    @Test fun swappedColorsAndShapesFailTheirIndividualRows() {
        val score = LocalMultiImageAssessmentPolicy.assess("F: blue:square\nB: cyan:circle\nH: red:star", fixtures)
        assertFalse(score.recognitions[0].colorCorrect)
        assertFalse(score.recognitions[1].shapeCorrect)
        assertFalse(score.allRecognized)
    }

    @Test fun duplicateOrUnexpectedLabelsDoNotCountAsCompleteRecognition() {
        val score = LocalMultiImageAssessmentPolicy.assess("F: cyan:square\nF: cyan:square\nB: blue:square\nH: red:star\nA: red:circle", fixtures)
        assertEquals(listOf("F"), score.duplicates)
        assertEquals(listOf("A"), score.unexpected)
        assertFalse(score.allRecognized)
    }

    @Test fun caseBulletsAndDeclaredColorSynonymsAreAccepted() {
        val score = LocalMultiImageAssessmentPolicy.assess("1. f: turquoise square\n- b: blue square\n* h: red star", fixtures)
        assertTrue(score.allRecognized)
    }

    @Test fun labelWordAndMarkdownDoNotBecomeFalseOmissions() {
        val score = LocalMultiImageAssessmentPolicy.assess("label:F:cyan:square\n**B**:blue:square\n| H | red | star |", fixtures)
        assertTrue(score.allRecognized)
        assertTrue(score.ordered)
    }

    @Test fun extraLetterDescriptionDoesNotHideCorrectShapeRecognition() {
        val score = LocalMultiImageAssessmentPolicy.assess("label:F:black:letter\nlabel:F:cyan:square\nB:blue:square\nH:red:star", fixtures)
        assertTrue(score.recognitions.all { it.correct })
        assertTrue(score.distinctOrdered)
        assertFalse(score.ordered)
        assertEquals(listOf("F"), score.duplicates)
    }

    @Test fun newVisualAttributeCanBeAssessedWithoutReusingTheCaption() {
        val dots = listOf(fixtures[0].copy(dotCount = 3), fixtures[2].copy(dotCount = 1))
        assertTrue(LocalMultiImageAssessmentPolicy.assessDots("F:3\nH:one", dots).allCorrect)
        assertFalse(LocalMultiImageAssessmentPolicy.assessDots("F:one\nH:3", dots).allCorrect)
        assertFalse(LocalMultiImageAssessmentPolicy.assessDots("H:one\nF:3", dots).allCorrect)
    }

    @Test fun commaSeparatedActualAnswerDoesNotBecomeFalseOmissions() {
        val dots = listOf(
            LocalMultiImageFixture("F", "cyan", "square", 3),
            LocalMultiImageFixture("J", "green", "square", 1),
            LocalMultiImageFixture("C", "green", "triangle", 3),
        )
        assertTrue(LocalMultiImageAssessmentPolicy.assessDots("F:3, J:1, C:3", dots).allCorrect)
        assertFalse(LocalMultiImageAssessmentPolicy.assessDots("F:3, J:3, C:3", dots).allCorrect)
    }
}
