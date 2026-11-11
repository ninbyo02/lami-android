package io.github.ninbyo02.lami.tts

import io.github.ninbyo02.lami.ui.screens.home.sanitizeAssistantTextForTts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LamiKokoroJapaneseFrontendTest {
    @Test
    fun matchesTrainingPronunciationForKanjiAndAppName() {
        assertEquals(
            "sɨtoɾeːʥi no akʲi joːɾʲoː o kakɯɲiɴ ɕi maɕita.",
            phonemes("ストレージの空き容量を確認しました。"),
        )
        assertEquals("koɲɲiʨiβa.ɾamʲidesɨ.", phonemes("こんにちは。ラミィです。").replace(" ", ""))
    }

    @Test
    fun readsUnitsAndCountersInJapanese() {
        assertEquals(
            "ɡeɴʣainoondoβaɲiʥɨːsaɴteɴɡododesɨ.",
            phonemes("現在の温度は２３．５℃です。").replace(" ", ""),
        )
        assertTrue(phonemes("残り時間は15分です。").endsWith("ɸɯɴ desɨ."))
        assertTrue(phonemes("2026年10月4日").endsWith("joʔka"))
        assertTrue(phonemes("GPUは87%です。").contains("paːsento"))
    }

    @Test
    fun readsTemperaturesAndSpacedElectricalUnitsAfterSpeechNormalization() {
        val degrees = phonemes("気温は26度です。")
        for (input in listOf("26℃", "26°C", "26° C")) {
            assertEquals(degrees, phonemes(sanitizeAssistantTextForTts("気温は${input}です。")))
        }
        for (unit in listOf("A", "V", "W")) {
            assertEquals(phonemes("12${unit}です。"), phonemes("12 ${unit}です。"))
        }
    }

    @Test
    fun readsIrregularHoursInClockTimes() {
        assertTrue(phonemes("9:05").replace(" ", "").startsWith("kɯʥiɡoɸɯɴ"))
        assertTrue(phonemes("14:30").replace(" ", "").startsWith("ʥɨːjoʥi"))
        assertTrue(phonemes("7時です。").replace(" ", "").startsWith("ɕiʨiʥi"))
        assertEquals(phonemes("九時間です。"), phonemes("9時間です。"))
    }

    @Test
    fun normalizesCommonNameVariantsAndReportsUnknownKanji() {
        assertEquals(phonemes("吉田さんに連絡します。"), phonemes("𠮷田さんに連絡します。"))
        assertEquals(phonemes("高崎です。"), phonemes("髙﨑です。"))
        assertThrows(IllegalArgumentException::class.java) { phonemes("𫝀田さんに連絡します。") }
    }

    @Test
    fun readsAProgrammingValueAsAtai() {
        assertEquals(phonemes("関数からあたいを返します。"), phonemes("関数から値を返します。"))
        for (text in listOf("値を返す。", "値を渡す。", "関数の値を計算する。", "値を表示する。")) {
            assertEquals(text, phonemes(text.replace("値", "あたい")), phonemes(text))
        }
    }

    @Test
    fun preservesDictionaryReadingsForCompoundValues() {
        val readings = mapOf(
            "戻り値を返す。" to "modoɾʲiʨi",
            "補正値を計算する。" to "hoseːʨi",
            "測定値を比較する。" to "sokɯteːʨi",
            "初期値を取得する。" to "ɕokʲiʨi",
            "安値を比較する。" to "jasɨne",
            "売り値を計算する。" to "ɯɾʲine",
        )
        for ((text, expected) in readings) {
            val actual = phonemes(text).replace(" ", "")
            assertTrue("$text: $actual", actual.startsWith(expected))
        }
        assertFalse(phonemes("しきい値を計算する。").contains("atai"))
    }

    @Test
    fun joinsVolitionalAndConjecturalAuxiliariesAsLongO() {
        assertEquals("soɾosoɾo saɡʲoː o haʥime maɕoː.", phonemes("そろそろ作業を始めましょう。"))
        assertEquals("aɕita βa hajame ɲi ikoː.", phonemes("明日は早めに行こう。"))
        assertEquals("eːɡa o iʔɕoɲi mʲijoː.", phonemes("映画を一緒に見よう。"))
        assertEquals("ɡoɡo βa haɾeɾɯ deɕoː.", phonemes("午後は晴れるでしょう。"))
        assertEquals("aɕita βa haɾeɾɯ daɾoː.", phonemes("明日は晴れるだろう。"))
    }

    @Test
    fun preservesLexicalUAndExplicitWordBoundaries() {
        assertEquals("soː omoɯ.", phonemes("そう思う。"))
        assertEquals("ɡaʔkoː ɲi kajoɯ.", phonemes("学校に通う。"))
        assertEquals("isoide doːɡɯ o ʦɨkaɯ.", phonemes("急いで道具を使う。"))
        assertEquals("aɕita βa hajame ɲi iko ɯ.", phonemes("明日は早めに行こ う。"))
    }

    private fun phonemes(text: String): String = LamiKokoroJapaneseFrontend.phonemize(text)
}
