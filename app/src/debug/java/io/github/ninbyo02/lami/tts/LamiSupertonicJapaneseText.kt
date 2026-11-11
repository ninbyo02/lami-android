package io.github.ninbyo02.lami.tts

import java.time.DateTimeException
import java.time.LocalDate

/** Expands numeric notation whose readings are ambiguous to the Japanese Supertonic model. */
internal object LamiSupertonicJapaneseText {
    private const val DIGIT = "[0-9０-９]"
    private const val ALNUM = "A-Za-zＡ-Ｚａ-ｚ0-9０-９_"
    private const val START = "(?<![$ALNUM+＋−－/／.．-])(?<!$DIGIT[,:，：])"
    private const val END = "(?![$ALNUM]|[.,:．，：]$DIGIT)"
    private const val SIGN = "[+＋−－-]"
    private const val WHOLE = "(?:$DIGIT{1,3}(?:[,，]$DIGIT{3})+|$DIGIT+)"

    private val protectedSpans = Regex("(`{1,3})[\\s\\S]*?\\1|(?:[a-z][a-z0-9+.-]*://|mailto:|tel:|www\\.)[^\\s`、。！？「」『』]+", RegexOption.IGNORE_CASE)
    private val date = Regex("$START($DIGIT{4})([/／－-])($DIGIT{1,2})\\2($DIGIT{1,2})$END")
    private val time = Regex("$START($DIGIT{1,2})[:：]($DIGIT{2})(?:[:：]($DIGIT{2}))?$END(?!の(?:比|割合)|[対章節])")
    private val ratioPrefix = Regex("(?:比率|割合|比)(?:は|が|を|[:：])?[ \\t]*$")
    private val quantity = Regex("$START($SIGN?)(?=$DIGIT|[.．]$DIGIT)($WHOLE)?(?:[.．]($DIGIT+))?[ \\t　]*([VＶAＡWＷ](?![型形版])|[%％](?![ \\t]*[$ALNUM])|℃|°[ \\t]*[CcＣｃ])$END")
    private val digits = listOf("ゼロ", "イチ", "ニ", "サン", "ヨン", "ゴ", "ロク", "ナナ", "ハチ", "キュウ")
    private val shortNumbers = listOf("ぜろ", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
    private val minutes = listOf("じゅっぷん", "いっぷん", "にふん", "さんぷん", "よんぷん",
        "ごふん", "ろっぷん", "ななふん", "はっぷん", "きゅうふん")

    fun split(text: String): List<String> = LamiTtsTextChunks.split(normalize(text))

    fun normalize(text: String): String = buildString {
        var cursor = 0
        for (match in protectedSpans.findAll(text)) {
            append(normalizeProse(text.substring(cursor, match.range.first)))
            append(match.value)
            cursor = match.range.last + 1
        }
        append(normalizeProse(text.substring(cursor)))
    }

    private fun normalizeProse(text: String): String {
        val dated = date.replace(text) { match ->
            val (_, writtenYear, _, writtenMonth, writtenDay) = match.groupValues
            val year = writtenYear.numericValue()
            val month = writtenMonth.numericValue()
            val day = writtenDay.numericValue()
            if (year == 0) return@replace match.value
            try {
                LocalDate.of(year, month, day)
            } catch (_: DateTimeException) {
                return@replace match.value
            }
            "${year.toString().padStart(4, '0')}年${month}月${day}日"
        }
        val timed = time.replace(dated) { match ->
            val hours = match.groupValues[1].numericValue()
            val mins = match.groupValues[2].numericValue()
            val secs = match.groups[3]?.value?.numericValue()
            val prefix = dated.substring(maxOf(0, match.range.first - 12), match.range.first)
            if (hours > 24 || mins > 59 || (secs != null && secs > 59) ||
                (hours == 24 && (mins != 0 || (secs ?: 0) != 0)) || ratioPrefix.containsMatchIn(prefix)
            ) return@replace match.value
            hourReading(hours) + minuteReading(mins) + (secs?.let { "${shortNumber(it)}びょう" } ?: "")
        }
        return quantity.replace(timed) { match ->
            val (_, sign, whole, fraction, unit) = match.groupValues
            val amount = whole.ifEmpty { "0" }.filter { it != ',' && it != '，' }.map { it.digitToInt().digitToChar() }
                .joinToString("").toLongOrNull()?.takeIf { it < 1_000_000_000_000 } ?: return@replace match.value
            val signReading = when (sign) {
                "" -> ""
                "+", "＋" -> "プラス"
                else -> "マイナス"
            }
            val unitReading = when (unit) {
                "V", "Ｖ" -> "ボルト"
                "A", "Ａ" -> "アンペア"
                "W", "Ｗ" -> "ワット"
                "%", "％" -> "パーセント"
                else -> "度"
            }
            val fractionReading = if (fraction.isEmpty()) "" else "点" + fraction.map { digits[it.digitToInt()] }.joinToString("")
            "$signReading${kanjiNumber(amount)}$fractionReading$unitReading"
        }
    }

    private fun String.numericValue(): Int = fold(0) { value, digit -> value * 10 + digit.digitToInt() }

    private fun hourReading(number: Int): String {
        val suffix = when (number % 10) {
            4 -> "よじ"
            7 -> "しちじ"
            9 -> "くじ"
            else -> return "${shortNumber(number)}じ"
        }
        return tens(number) + suffix
    }

    private fun minuteReading(number: Int): String {
        if (number == 0) return "ぜろふん"
        if (number % 10 != 0) return tens(number) + minutes[number % 10]
        return (if (number >= 20) shortNumbers[number / 10] else "") + minutes[0]
    }

    private fun shortNumber(number: Int): String = if (number < 10) shortNumbers[number] else
        tens(number) + if (number % 10 == 0) "" else shortNumbers[number % 10]

    private fun tens(number: Int): String = when (number / 10) {
        0 -> ""
        1 -> "じゅう"
        else -> "${shortNumbers[number / 10]}じゅう"
    }

    private fun kanjiNumber(number: Long): String {
        if (number == 0L) return digits[0]
        val kanji = "〇一二三四五六七八九"
        val groups = listOf(100_000_000L to "億", 10_000L to "万", 1L to "")
        return buildString {
            for ((power, suffix) in groups) {
                val group = (number / power % 10_000).toInt()
                if (group == 0) continue
                for ((place, unit) in listOf(1000 to "千", 100 to "百", 10 to "十", 1 to "")) {
                    val digit = group / place % 10
                    if (digit == 0) continue
                    if (digit != 1 || place == 1) append(kanji[digit])
                    append(unit)
                }
                append(suffix)
            }
        }
    }
}
