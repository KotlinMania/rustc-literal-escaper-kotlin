package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.MAX_UNICODE_SCALAR
import io.github.kotlinmania.rustcliteralescaper.compat.ScalarCursor
import io.github.kotlinmania.rustcliteralescaper.compat.hexDigitValue
import io.github.kotlinmania.rustcliteralescaper.compat.isUnicodeWhitespace
import io.github.kotlinmania.rustcliteralescaper.compat.isValidUnicodeScalar
import io.github.kotlinmania.rustcliteralescaper.compat.utf8ByteLength

/**
 * Unescape a character literal.
 *
 * Takes the contents of a character literal without quotes and returns an
 * unescaped character scalar or an error.
 */
public fun unescapeChar(src: String): CharEscapeOutcome =
    unescapeSingleChar(ScalarCursor(src))

/**
 * Unescape a byte literal.
 *
 * Takes the contents of a byte literal without quotes and returns an
 * unescaped byte or an error.
 */
public fun unescapeByte(src: String): ByteEscapeOutcome =
    unescapeSingleByte(ScalarCursor(src))

/**
 * Unescape a string literal.
 *
 * Takes the contents of a string literal without quotes and produces a
 * sequence of unescaped character scalars or errors through [callback].
 */
public fun unescapeStr(
    src: String,
    callback: CharEscapeCallback,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val scalar = chars.nextCodePoint() ?: break
        val outcome =
            when (scalar) {
                '\\'.code -> {
                    if (chars.peekCodePoint() == '\n'.code) {
                        chars.nextCodePoint()
                        skipAsciiWhitespace(chars, start) { range, err ->
                            callback.onUnit(range, CharEscapeOutcome.err(err))
                        }
                        continue
                    }
                    unescape1Char(chars)
                }
                '"'.code -> CharEscapeOutcome.err(EscapeError.EscapeOnlyChar)
                '\r'.code -> CharEscapeOutcome.err(EscapeError.BareCarriageReturn)
                else -> CharEscapeOutcome.okCodePoint(scalar)
            }
        callback.onUnit(ByteRange(start, chars.byteIndex), outcome)
    }
}

/**
 * Unescape a byte string literal.
 *
 * Takes the contents of a byte string literal without quotes and produces a
 * sequence of unescaped bytes or errors through [callback].
 */
public fun unescapeByteStr(
    src: String,
    callback: ByteEscapeCallback,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val scalar = chars.nextCodePoint() ?: break
        val outcome =
            when (scalar) {
                '\\'.code -> {
                    if (chars.peekCodePoint() == '\n'.code) {
                        chars.nextCodePoint()
                        skipAsciiWhitespace(chars, start) { range, err ->
                            callback.onUnit(range, ByteEscapeOutcome.err(err))
                        }
                        continue
                    }
                    unescape1Byte(chars)
                }
                '"'.code -> ByteEscapeOutcome.err(EscapeError.EscapeOnlyChar)
                '\r'.code -> ByteEscapeOutcome.err(EscapeError.BareCarriageReturn)
                else -> char2byte(scalar)
            }
        callback.onUnit(ByteRange(start, chars.byteIndex), outcome)
    }
}

/**
 * Unescape a C string literal.
 *
 * Takes the contents of a C string literal without quotes and produces a
 * sequence of unescaped [MixedUnit] values or errors through [callback].
 */
public fun unescapeCStr(
    src: String,
    callback: MixedUnitEscapeCallback,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val scalar = chars.nextCodePoint() ?: break
        val outcome =
            when (scalar) {
                '\\'.code -> {
                    if (chars.peekCodePoint() == '\n'.code) {
                        chars.nextCodePoint()
                        skipAsciiWhitespace(chars, start) { range, err ->
                            callback.onUnit(range, MixedUnitEscapeOutcome.err(err))
                        }
                        continue
                    }
                    unescape1CStr(chars)
                }
                '"'.code -> MixedUnitEscapeOutcome.err(EscapeError.EscapeOnlyChar)
                '\r'.code -> MixedUnitEscapeOutcome.err(EscapeError.BareCarriageReturn)
                else -> MixedUnit.tryFromCodePoint(scalar)
            }
        callback.onUnit(ByteRange(start, chars.byteIndex), outcome)
    }
}

/**
 * Collect all steps produced by [unescapeStr] into an immutable list.
 */
public fun collectUnescapeStr(src: String): List<CharEscapeStep> {
    val steps = mutableListOf<CharEscapeStep>()
    unescapeStr(src) { range, outcome ->
        steps.add(CharEscapeStep(range, outcome))
    }
    return steps.toList()
}

/**
 * Collect all steps produced by [unescapeByteStr] into an immutable list.
 */
public fun collectUnescapeByteStr(src: String): List<ByteEscapeStep> {
    val steps = mutableListOf<ByteEscapeStep>()
    unescapeByteStr(src) { range, outcome ->
        steps.add(ByteEscapeStep(range, outcome))
    }
    return steps.toList()
}

/**
 * Collect all steps produced by [unescapeCStr] into an immutable list.
 */
public fun collectUnescapeCStr(src: String): List<MixedUnitEscapeStep> {
    val steps = mutableListOf<MixedUnitEscapeStep>()
    unescapeCStr(src) { range, outcome ->
        steps.add(MixedUnitEscapeStep(range, outcome))
    }
    return steps.toList()
}

internal fun unescapeSingleChar(chars: ScalarCursor): CharEscapeOutcome {
    val first =
        when (val scalar = chars.nextCodePoint()) {
            null -> return CharEscapeOutcome.err(EscapeError.ZeroChars)
            '\\'.code -> unescape1Char(chars)
            '\n'.code, '\t'.code, '\''.code -> CharEscapeOutcome.err(EscapeError.EscapeOnlyChar)
            '\r'.code -> CharEscapeOutcome.err(EscapeError.BareCarriageReturn)
            else -> CharEscapeOutcome.okCodePoint(scalar)
        }
    if (first.isErr) {
        return first
    }
    if (chars.nextCodePoint() != null) {
        return CharEscapeOutcome.err(EscapeError.MoreThanOneChar)
    }
    return first
}

internal fun unescapeSingleByte(chars: ScalarCursor): ByteEscapeOutcome {
    val first =
        when (val scalar = chars.nextCodePoint()) {
            null -> return ByteEscapeOutcome.err(EscapeError.ZeroChars)
            '\\'.code -> unescape1Byte(chars)
            '\n'.code, '\t'.code, '\''.code -> ByteEscapeOutcome.err(EscapeError.EscapeOnlyChar)
            '\r'.code -> ByteEscapeOutcome.err(EscapeError.BareCarriageReturn)
            else -> char2byte(scalar)
        }
    if (first.isErr) {
        return first
    }
    if (chars.nextCodePoint() != null) {
        return ByteEscapeOutcome.err(EscapeError.MoreThanOneChar)
    }
    return first
}

private fun unescape1Char(chars: ScalarCursor): CharEscapeOutcome {
    val scalar = chars.nextCodePoint() ?: return CharEscapeOutcome.err(EscapeError.LoneSlash)
    if (scalar == '0'.code) {
        return CharEscapeOutcome.okCodePoint(0)
    }
    val simple = simpleEscape(scalar)
    if (simple != null) {
        return CharEscapeOutcome.okCodePoint(simple.get().toInt())
    }
    return when (scalar) {
        'x'.code -> {
            val byteOutcome = hexEscape(chars)
            val err = byteOutcome.errorOrNull()
            if (err != null) {
                CharEscapeOutcome.err(err)
            } else {
                val b = byteOutcome.unwrap()
                if (b <= 0x7Fu) {
                    CharEscapeOutcome.okCodePoint(b.toInt())
                } else {
                    CharEscapeOutcome.err(EscapeError.OutOfRangeHexEscape)
                }
            }
        }
        'u'.code -> {
            when (val raw = unicodeEscape(chars)) {
                is UnicodeEscapeParse.Err -> CharEscapeOutcome.err(raw.error)
                is UnicodeEscapeParse.Ok -> decodeUnicodeScalar(raw.value)
            }
        }
        else -> CharEscapeOutcome.err(EscapeError.InvalidEscape)
    }
}

private fun unescape1Byte(chars: ScalarCursor): ByteEscapeOutcome {
    val scalar = chars.nextCodePoint() ?: return ByteEscapeOutcome.err(EscapeError.LoneSlash)
    if (scalar == '0'.code) {
        return ByteEscapeOutcome.ok(0u)
    }
    val simple = simpleEscape(scalar)
    if (simple != null) {
        return ByteEscapeOutcome.ok(simple.get())
    }
    return when (scalar) {
        'x'.code -> hexEscape(chars)
        'u'.code -> {
            when (val raw = unicodeEscape(chars)) {
                is UnicodeEscapeParse.Err -> ByteEscapeOutcome.err(raw.error)
                is UnicodeEscapeParse.Ok -> {
                    decodeUnicodeScalar(raw.value)
                    ByteEscapeOutcome.err(EscapeError.UnicodeEscapeInByte)
                }
            }
        }
        else -> ByteEscapeOutcome.err(EscapeError.InvalidEscape)
    }
}

private fun unescape1CStr(chars: ScalarCursor): MixedUnitEscapeOutcome {
    val scalar = chars.nextCodePoint() ?: return MixedUnitEscapeOutcome.err(EscapeError.LoneSlash)
    if (scalar == '0'.code) {
        return MixedUnitEscapeOutcome.err(EscapeError.NulInCStr)
    }
    val simple = simpleEscape(scalar)
    if (simple != null) {
        return MixedUnitEscapeOutcome.ok(MixedUnit.from(simple))
    }
    return when (scalar) {
        'x'.code -> {
            val byteOutcome = hexEscape(chars)
            val err = byteOutcome.errorOrNull()
            if (err != null) {
                MixedUnitEscapeOutcome.err(err)
            } else {
                MixedUnit.tryFrom(byteOutcome.unwrap())
            }
        }
        'u'.code -> {
            when (val raw = unicodeEscape(chars)) {
                is UnicodeEscapeParse.Err -> MixedUnitEscapeOutcome.err(raw.error)
                is UnicodeEscapeParse.Ok -> {
                    val decoded = decodeUnicodeScalar(raw.value)
                    val err = decoded.errorOrNull()
                    if (err != null) {
                        MixedUnitEscapeOutcome.err(err)
                    } else {
                        MixedUnit.tryFromCodePoint(decoded.unwrapCodePoint())
                    }
                }
            }
        }
        else -> MixedUnitEscapeOutcome.err(EscapeError.InvalidEscape)
    }
}

private fun decodeUnicodeScalar(value: Int): CharEscapeOutcome =
    when {
        value > MAX_UNICODE_SCALAR -> CharEscapeOutcome.err(EscapeError.OutOfRangeUnicodeEscape)
        !isValidUnicodeScalar(value) -> CharEscapeOutcome.err(EscapeError.LoneSurrogateUnicodeEscape)
        else -> CharEscapeOutcome.okCodePoint(value)
    }

private fun simpleEscape(codePoint: Int): NonZeroU8? {
    val escaped: UByte =
        when (codePoint) {
            '"'.code -> '"'.code.toUByte()
            'n'.code -> '\n'.code.toUByte()
            'r'.code -> '\r'.code.toUByte()
            't'.code -> '\t'.code.toUByte()
            '\\'.code -> '\\'.code.toUByte()
            '\''.code -> '\''.code.toUByte()
            else -> return null
        }
    return NonZeroU8.new(escaped)
}

private fun hexEscape(chars: ScalarCursor): ByteEscapeOutcome {
    val hiScalar = chars.nextCodePoint() ?: return ByteEscapeOutcome.err(EscapeError.TooShortHexEscape)
    val hi = hexDigitValue(hiScalar) ?: return ByteEscapeOutcome.err(EscapeError.InvalidCharInHexEscape)
    val loScalar = chars.nextCodePoint() ?: return ByteEscapeOutcome.err(EscapeError.TooShortHexEscape)
    val lo = hexDigitValue(loScalar) ?: return ByteEscapeOutcome.err(EscapeError.InvalidCharInHexEscape)
    return ByteEscapeOutcome.ok(((hi shl 4) or lo).toUByte())
}

private sealed class UnicodeEscapeParse {
    data class Ok(
        val value: Int,
    ) : UnicodeEscapeParse()

    data class Err(
        val error: EscapeError,
    ) : UnicodeEscapeParse()
}

private fun unicodeEscape(chars: ScalarCursor): UnicodeEscapeParse {
    if (chars.nextCodePoint() != '{'.code) {
        return UnicodeEscapeParse.Err(EscapeError.NoBraceInUnicodeEscape)
    }
    var value =
        when (val first = chars.nextCodePoint()) {
            null -> return UnicodeEscapeParse.Err(EscapeError.UnclosedUnicodeEscape)
            '_'.code -> return UnicodeEscapeParse.Err(EscapeError.LeadingUnderscoreUnicodeEscape)
            '}'.code -> return UnicodeEscapeParse.Err(EscapeError.EmptyUnicodeEscape)
            else -> hexDigitValue(first) ?: return UnicodeEscapeParse.Err(EscapeError.InvalidCharInUnicodeEscape)
        }

    var digitCount = 1
    while (true) {
        when (val next = chars.nextCodePoint()) {
            null -> return UnicodeEscapeParse.Err(EscapeError.UnclosedUnicodeEscape)
            '_'.code -> continue
            '}'.code -> {
                return if (digitCount > 6) {
                    UnicodeEscapeParse.Err(EscapeError.OverlongUnicodeEscape)
                } else {
                    UnicodeEscapeParse.Ok(value)
                }
            }
            else -> {
                val digit =
                    hexDigitValue(next)
                        ?: return UnicodeEscapeParse.Err(EscapeError.InvalidCharInUnicodeEscape)
                digitCount += 1
                if (digitCount > 6) {
                    continue
                }
                value = (value shl 4) + digit
            }
        }
    }
}

private fun skipAsciiWhitespace(
    chars: ScalarCursor,
    start: Int,
    callback: EscapeErrorCallback,
) {
    var sawNewline = false
    while (true) {
        when (chars.peekCodePoint()) {
            ' '.code, '\t'.code, '\r'.code -> {
                chars.nextCodePoint()
            }
            '\n'.code -> {
                chars.nextCodePoint()
                sawNewline = true
            }
            else -> break
        }
    }
    val end = chars.byteIndex
    if (sawNewline) {
        callback.onError(ByteRange(start, end), EscapeError.MultipleSkippedLinesWarning)
    }
    val unskipped = chars.peekCodePoint()
    if (unskipped != null && isUnicodeWhitespace(unskipped)) {
        callback.onError(
            ByteRange(start, end + utf8ByteLength(unskipped)),
            EscapeError.UnskippedWhitespaceWarning,
        )
    }
}
