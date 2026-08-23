package io.github.ninbyo02.lami.util

import java.net.MalformedURLException
import java.net.URL
import java.text.Normalizer
import java.util.Locale

const val PORT_ERROR_MESSAGE = "URLは http://host:port 形式で入力してください。半角数字でポートを入力してください"
const val PUBLIC_CLEARTEXT_ERROR_MESSAGE =
    "公開ネットワークへのHTTP接続は許可されません。HTTPSまたはローカルネットワークのアドレスを使用してください"

fun normalizeUrlInput(input: String): String {
    return Normalizer.normalize(input.trim(), Normalizer.Form.NFKC)
}

data class UrlValidationResult(
    val normalizedUrl: String,
    val isValid: Boolean,
    val errorMessage: String? = null
)

fun validateUrlFormat(urlString: String): UrlValidationResult {
    val normalized = normalizeUrlInput(urlString)
    if (normalized.isBlank()) {
        return UrlValidationResult(normalized, false, PORT_ERROR_MESSAGE)
    }

    return try {
        val url = URL(normalized)
        if (url.port == 0 || url.port > 65_535) {
            return UrlValidationResult(normalized, false, PORT_ERROR_MESSAGE)
        }
        val protocol = url.protocol.lowercase(Locale.ROOT)
        if (protocol !in setOf("http", "https") || url.host.isBlank()) {
            return UrlValidationResult(normalized, false, PORT_ERROR_MESSAGE)
        }
        if (protocol == "http" && !isLocalOrPrivateHost(url.host)) {
            return UrlValidationResult(normalized, false, PUBLIC_CLEARTEXT_ERROR_MESSAGE)
        }
        UrlValidationResult(normalized, true)
    } catch (e: MalformedURLException) {
        UrlValidationResult(normalized, false, PORT_ERROR_MESSAGE)
    } catch (e: IllegalArgumentException) {
        UrlValidationResult(normalized, false, PORT_ERROR_MESSAGE)
    }
}

internal fun isLocalOrPrivateHost(rawHost: String): Boolean {
    val host = rawHost
        .trim()
        .removePrefix("[")
        .removeSuffix("]")
        .trimEnd('.')
        .lowercase(Locale.ROOT)
    if (host.isBlank()) return false

    if (
        host == "localhost" ||
        host.endsWith(".localhost") ||
        host.endsWith(".local") ||
        host.endsWith(".lan") ||
        host.endsWith(".home.arpa") ||
        (!host.contains('.') && !host.contains(':'))
    ) {
        return true
    }

    val ipv4 = host.split('.').mapNotNull { it.toIntOrNull() }
    if (ipv4.size == 4 && ipv4.all { it in 0..255 }) {
        val first = ipv4[0]
        val second = ipv4[1]
        return first == 10 ||
            first == 127 ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 100 && second in 64..127)
    }

    val ipv6 = host.substringBefore('%')
    if (!ipv6.contains(':')) return false
    return ipv6 == "::1" ||
        ipv6.startsWith("fc") ||
        ipv6.startsWith("fd") ||
        Regex("^fe[89ab]", RegexOption.IGNORE_CASE).containsMatchIn(ipv6)
}
