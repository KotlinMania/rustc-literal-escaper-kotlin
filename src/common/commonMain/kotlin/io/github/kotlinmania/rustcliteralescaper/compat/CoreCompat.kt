package io.github.kotlinmania.rustcliteralescaper.compat

internal const val MAX_UNICODE_SCALAR: Int = 0x10FFFF
internal const val SURROGATE_START: Int = 0xD800
internal const val SURROGATE_END: Int = 0xDFFF

internal fun isValidUnicodeScalar(codePoint: Int): Boolean =
    codePoint in 0..MAX_UNICODE_SCALAR && codePoint !in SURROGATE_START..SURROGATE_END

internal fun utf8ByteLength(codePoint: Int): Int =
    when {
        codePoint <= 0x7F -> 1
        codePoint <= 0x7FF -> 2
        codePoint <= 0xFFFF -> 3
        else -> 4
    }

internal fun codePointToString(codePoint: Int): String {
    require(isValidUnicodeScalar(codePoint)) {
        "Invalid Unicode scalar value: $codePoint"
    }
    if (codePoint <= 0xFFFF) {
        return codePoint.toChar().toString()
    }
    val offset = codePoint - 0x10000
    val high = ((offset ushr 10) + 0xD800).toChar()
    val low = ((offset and 0x3FF) + 0xDC00).toChar()
    return buildString(2) {
        append(high)
        append(low)
    }
}

internal fun codePointToUtf8Bytes(codePoint: Int): ByteArray {
    require(isValidUnicodeScalar(codePoint)) {
        "Invalid Unicode scalar value: $codePoint"
    }
    return when {
        codePoint <= 0x7F ->
            byteArrayOf(codePoint.toByte())
        codePoint <= 0x7FF ->
            byteArrayOf(
                (0xC0 or (codePoint ushr 6)).toByte(),
                (0x80 or (codePoint and 0x3F)).toByte(),
            )
        codePoint <= 0xFFFF ->
            byteArrayOf(
                (0xE0 or (codePoint ushr 12)).toByte(),
                (0x80 or ((codePoint ushr 6) and 0x3F)).toByte(),
                (0x80 or (codePoint and 0x3F)).toByte(),
            )
        else ->
            byteArrayOf(
                (0xF0 or (codePoint ushr 18)).toByte(),
                (0x80 or ((codePoint ushr 12) and 0x3F)).toByte(),
                (0x80 or ((codePoint ushr 6) and 0x3F)).toByte(),
                (0x80 or (codePoint and 0x3F)).toByte(),
            )
    }
}

internal fun isUnicodeWhitespace(codePoint: Int): Boolean =
    when (codePoint) {
        in 0x0009..0x000D,
        0x0020,
        0x0085,
        0x00A0,
        0x1680,
        in 0x2000..0x200A,
        0x2028,
        0x2029,
        0x202F,
        0x205F,
        0x3000,
        -> true
        else -> false
    }

internal fun hexDigitValue(codePoint: Int): Int? =
    when (codePoint) {
        in '0'.code..'9'.code -> codePoint - '0'.code
        in 'a'.code..'f'.code -> codePoint - 'a'.code + 10
        in 'A'.code..'F'.code -> codePoint - 'A'.code + 10
        else -> null
    }

internal class ScalarCursor(
    private val source: String,
) {
    private var utf16Index: Int = 0
    var byteIndex: Int = 0
        private set

    fun nextCodePoint(): Int? {
        if (utf16Index >= source.length) {
            return null
        }
        val first = source[utf16Index]
        if (first.isHighSurrogate() && utf16Index + 1 < source.length) {
            val second = source[utf16Index + 1]
            if (second.isLowSurrogate()) {
                val scalar = 0x10000 + ((first.code - 0xD800) shl 10) + (second.code - 0xDC00)
                utf16Index += 2
                byteIndex += 4
                return scalar
            }
        }
        val scalar = first.code
        utf16Index += 1
        byteIndex += utf8ByteLength(scalar)
        return scalar
    }

    fun peekCodePoint(): Int? {
        if (utf16Index >= source.length) {
            return null
        }
        val first = source[utf16Index]
        if (first.isHighSurrogate() && utf16Index + 1 < source.length) {
            val second = source[utf16Index + 1]
            if (second.isLowSurrogate()) {
                return 0x10000 + ((first.code - 0xD800) shl 10) + (second.code - 0xDC00)
            }
        }
        return first.code
    }
}
