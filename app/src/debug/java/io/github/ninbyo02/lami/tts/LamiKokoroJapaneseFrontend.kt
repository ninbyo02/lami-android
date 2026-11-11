package io.github.ninbyo02.lami.tts

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Maps Japanese text to the IPA character sequence used to train Kokoro. */
internal object LamiKokoroJapaneseFrontend {
    val available: Boolean = true
    private const val O_VOWEL_KANA = "オコゴソゾトドノホボポモヨョロ"

    private val tokenizer by lazy { Tokenizer() }
    private val compounds by lazy {
        val resource = checkNotNull(javaClass.getResourceAsStream("/lami/tts/kokoro/ja-compounds.txt.gz"))
        GZIPInputStream(resource).bufferedReader(Charsets.UTF_8).useLines { it.toHashSet() }
    }
    private val digits = listOf("ゼロ", "イチ", "ニ", "サン", "ヨン", "ゴ", "ロク", "ナナ", "ハチ", "キュー")
    private val letters = listOf(
        "エー", "ビー", "シー", "ディー", "イー", "エフ", "ジー", "エイチ", "アイ",
        "ジェー", "ケー", "エル", "エム", "エヌ", "オー", "ピー", "キュー", "アール",
        "エス", "ティー", "ユー", "ブイ", "ダブリュー", "エックス", "ワイ", "ゼット",
    )
    private val acronyms = mapOf(
        "CPU" to "シーピーユー", "GPU" to "ジーピーユー", "NPU" to "エヌピーユー",
        "RAM" to "ラム", "USB" to "ユーエスビー", "LAMI" to "ラミ",
    )
    private val date = Regex("""(20[0-9]{2})(?:年|[-/])([0-9]{1,2})(?:月|[-/])([0-9]{1,2})日?""")
    private val time = Regex("""(?<![0-9])([0-9]{1,2}):([0-9]{2})(?![0-9])""")
    private val clockHour = Regex("""(?<![0-9])([0-9]{1,2})時(?![間限])""")
    private val decimal = Regex("""([0-9]+)\.([0-9]+)""")
    private val unit = Regex("""(?<=[0-9])[ \t]*([VAW])(?![A-Za-z0-9_])""")
    private val numericValueAction = Regex("を(?:返|代入|取得|比較|計算|渡|表示)")
    private val latin = Regex("[A-Za-z]+")
    private val integer = Regex("[0-9]+")
    private val spaces = Regex("\\s+")
    private val spaceBeforePunctuation = Regex(""" +([.,!?;:”])""")
    private val longO = Regex("([$O_VOWEL_KANA])ウ")
    private val longE = Regex("([エケゲセゼテデネヘベペメレェ])イ")
    private val smallKana = "ゃゅょぁぃぅぇぉ"
    private val punctuation = mapOf(
        '。' to '.', '、' to ',', '「' to '“', '」' to '”', '『' to '“', '』' to '”',
        '【' to '(', '】' to ')', '・' to ' ', '〜' to '—', '~' to '—',
    )

    fun phonemize(text: String): String {
        val spoken = normalize(text)
        val words = synchronized(tokenizer) { tokenizer.tokenize(spoken) }
        val phonemes = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            var end = start + 1
            val surface = StringBuilder(words[start].surface)
            for (candidate in start + 1 until words.size) {
                surface.append(words[candidate].surface)
                if (surface.length > 24) break
                if (surface.toString() in compounds) end = candidate + 1
            }
            if (end < words.size && isLongOAuxiliarySuffix(words[end - 1], words[end])) end++
            val reading = (start until end).joinToString("") { index ->
                if (index > start && isLongOAuxiliarySuffix(words[index - 1], words[index])) "ー"
                else numericValueReading(spoken, words, index) ?: pronunciation(words[index])
            }
            phonemes += kanaToIpa(reading)
            start = end
        }
        return phonemes.joinToString(" ").replace(spaces, " ")
            .replace(spaceBeforePunctuation, "$1")
            .trim()
    }

    private fun isLongOAuxiliarySuffix(stem: Token, suffix: Token): Boolean {
        if (suffix.surface != "う" || suffix.partOfSpeechLevel1 != "助動詞" ||
            stem.position + stem.surface.length != suffix.position
        ) return false
        // IPADIC separates the volitional and conjectural ending from its stem.
        val volitionalStem = stem.conjugationForm == "未然ウ接続"
        val conjecturalStem = stem.partOfSpeechLevel1 == "助動詞" && stem.conjugationForm == "未然形"
        return (volitionalStem || conjecturalStem) &&
            pronunciation(stem).lastOrNull()?.let { it in O_VOWEL_KANA } == true
    }

    private fun numericValueReading(spoken: String, words: List<Token>, index: Int): String? {
        val word = words[index]
        if (word.surface != "値" || word.partOfSpeechLevel1 != "名詞" || word.partOfSpeechLevel2 != "一般" ||
            numericValueAction.matchAt(spoken, word.position + word.surface.length) == null
        ) return null
        // Some compounds split into a stem and a general noun in IPADIC.
        val stem = words.getOrNull(index - 1)
        val attachedStem = stem != null && stem.position + stem.surface.length == word.position &&
            (stem.partOfSpeechLevel1 == "名詞" || stem.partOfSpeechLevel1 == "接頭詞" ||
                stem.conjugationForm?.startsWith("連用") == true)
        return if (attachedStem) null else "アタイ"
    }

    private fun pronunciation(token: Token): String {
        val dictionaryReading = token.pronunciation.takeUnless { it.isNullOrEmpty() || it == "*" }
            ?: token.reading.takeUnless { it.isNullOrEmpty() || it == "*" }
        val containsKanji = token.surface.codePoints().anyMatch {
            Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
        }
        require(dictionaryReading != null || !containsKanji) {
            "Kokoroで読みを確認できない漢字が含まれています。"
        }
        val reading = dictionaryReading ?: token.surface
        // IPADIC sometimes spells the long vowels of Sino-Japanese nouns out.
        return if (token.partOfSpeechLevel1 == "名詞" && token.partOfSpeechLevel2 != "固有名詞" &&
            containsKanji
        ) {
            reading.replace(longO, "$1ー").replace(longE, "$1ー")
        } else {
            reading
        }
    }

    private fun normalize(text: String): String {
        var spoken = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(JapaneseCodeSpeechText.celsiusUnit, "度")
            .replace("%", "パーセント").replace("ラミィ", "ラミ")
            .replace("𠮷", "吉").replace('髙', '高').replace('﨑', '崎')
        spoken = date.replace(spoken) { match ->
            val (_, year, month, day) = match.groupValues
            "${kanjiNumber(year)}年${kanjiNumber(month)}月${dayReading(day)}"
        }
        spoken = time.replace(spoken) { match ->
            val (_, hour, minute) = match.groupValues
            "${hourReading(hour)}${minuteReading(minute)}"
        }
        spoken = clockHour.replace(spoken) { match -> hourReading(match.groupValues[1]) }
        spoken = unit.replace(spoken) { match ->
            when (match.groupValues[1]) {
                "V" -> "ボルト"
                "A" -> "アンペア"
                else -> "ワット"
            }
        }
        spoken = decimal.replace(spoken) { match ->
            val (_, whole, fraction) = match.groupValues
            "${kanjiNumber(whole)}点${spellDigits(fraction)}"
        }
        spoken = latin.replace(spoken) { match ->
            val word = match.value.uppercase(Locale.ROOT)
            acronyms[word] ?: word.map { letters[it - 'A'] }.joinToString(" ")
        }
        return integer.replace(spoken) { kanjiNumber(it.value) }
    }

    private fun spellDigits(value: String): String = value.map { digits[it - '0'] }.joinToString(" ")

    private fun kanjiNumber(value: String): String {
        val number = value.toIntOrNull()
        if (number == null || number > 9999) return spellDigits(value)
        if (number == 0) return digits[0]
        val kanji = "〇一二三四五六七八九"
        return buildString {
            var remainder = number
            for ((power, unit) in listOf(1000 to "千", 100 to "百", 10 to "十", 1 to "")) {
                val digit = remainder / power
                remainder %= power
                if (digit == 0) continue
                if (digit != 1 || power == 1) append(kanji[digit])
                append(unit)
            }
        }
    }

    private fun dayReading(day: String): String {
        val irregular = mapOf(
            1 to "ツイタチ", 2 to "フツカ", 3 to "ミッカ", 4 to "ヨッカ", 5 to "イツカ",
            6 to "ムイカ", 7 to "ナノカ", 8 to "ヨーカ", 9 to "ココノカ", 10 to "トオカ",
            14 to "ジューヨッカ", 20 to "ハツカ", 24 to "ニジューヨッカ",
        )
        return irregular[day.toInt()] ?: "${kanjiNumber(day)}日"
    }

    private fun hourReading(hour: String): String {
        val number = hour.toInt()
        val ending = when (number % 10) {
            4 -> "ヨジ"
            7 -> "シチジ"
            9 -> "クジ"
            else -> return "${kanjiNumber(hour)}時"
        }
        val tens = number / 10
        return when (tens) {
            0 -> ending
            1 -> "ジュー$ending"
            else -> "${digits[tens]}ジュー$ending"
        }
    }

    private fun minuteReading(minute: String): String {
        val number = minute.toInt()
        if (number == 0) return "ゼロフン"
        val ending = listOf("ジュップン", "イップン", "ニフン", "サンプン", "ヨンプン",
            "ゴフン", "ロップン", "ナナフン", "ハップン", "キューフン")
        val tens = number / 10
        val ones = number % 10
        return buildString {
            if (tens > 1) append(digits[tens])
            if (tens > 0 && ones > 0) append("ジュー")
            append(ending[ones])
        }
    }

    private fun kanaToIpa(reading: String): String {
        val hiragana = reading.map { char ->
            if (char in 'ァ'..'ヶ') char - 0x60 else char
        }
        return buildString {
            var index = 0
            while (index < hiragana.size) {
                val char = hiragana[index]
                val next = hiragana.getOrNull(index + 1)
                val single = kokoroMoraTable[char.toString()]
                val nextMora = next?.let { kokoroMoraTable[it.toString()] }
                val digraph = next?.let { kokoroMoraTable["$char$it"] }
                when {
                    digraph != null -> {
                        append(digraph)
                        index++
                    }
                    char == 'っ' -> append('ʔ')
                    char == 'ー' -> append('ː')
                    char == 'ん' -> append(nasal(nextMora))
                    single != null && next != null && next in smallKana && nextMora != null -> {
                        append(single.dropLast(1))
                        append(nextMora)
                        index++
                    }
                    single != null -> append(single)
                    char in ".,!?;:()—“”…\"" -> append(char)
                    char in punctuation -> append(punctuation.getValue(char))
                }
                index++
            }
        }
    }

    private fun nasal(nextMora: String?): Char = when (nextMora?.firstOrNull()) {
        'm', 'p', 'b' -> 'm'
        'k', 'ɡ' -> 'ŋ'
        'ɲ', 'ʨ', 'ʥ' -> 'ɲ'
        'n', 't', 'd', 'ɾ', 'z' -> 'n'
        else -> 'ɴ'
    }
}
