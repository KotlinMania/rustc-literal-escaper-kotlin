package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.Chars
import io.github.kotlinmania.rustcliteralescaper.compat.MAX_UNICODE_SCALAR
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
    StrUnescape.unescapeSingle(Chars(src))

/**
 * Unescape a byte literal.
 *
 * Takes the contents of a byte literal without quotes and returns an
 * unescaped byte or an error.
 */
public fun unescapeByte(src: String): ByteEscapeOutcome =
    ByteSliceUnescape.unescapeSingle(Chars(src))

/**
 * Unescape a string literal.
 *
 * Takes the contents of a string literal without quotes and produces a
 * sequence of unescaped character scalars or errors, which are returned by
 * invoking [callback].
 */
public fun unescapeStr(
    src: String,
    callback: CharEscapeCallback,
) {
    StrUnescape.unescape(src, callback::onUnit)
}

/**
 * Unescape a byte string literal.
 *
 * Takes the contents of a byte string literal without quotes and produces a
 * sequence of unescaped bytes or errors, which are returned by invoking
 * [callback].
 */
public fun unescapeByteStr(
    src: String,
    callback: ByteEscapeCallback,
) {
    ByteSliceUnescape.unescape(src, callback::onUnit)
}

/**
 * Unescape a C string literal.
 *
 * Takes the contents of a C string literal without quotes and produces a
 * sequence of unescaped [MixedUnit] values or errors, which are returned by
 * invoking [callback].
 */
public fun unescapeCStr(
    src: String,
    callback: MixedUnitEscapeCallback,
) {
    CStrUnescape.unescape(src, callback::onUnit)
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

/**
 * Internal interface for unescaping escape sequences in character, byte, and string literals.
 */
internal interface Unescape<U> {
    /** Outcome of unescaping the zero escape (`'\0'`). */
    val ZERO_RESULT: U

    /** Converts non-zero ASCII bytes to the unit outcome. */
    fun nonzeroByte2unit(b: NonZeroU8): U

    /** Converts Unicode scalars to the unit outcome. */
    fun char2unit(codePoint: Int): U

    /** Converts the byte of a hexadecimal escape to the unit outcome. */
    fun hex2unit(b: UByte): U

    /** Converts the outcome of a Unicode escape to the unit outcome. */
    fun unicode2unit(r: CharEscapeOutcome): U

    /** Wraps an [EscapeError] into the unit outcome. */
    fun error2unit(error: EscapeError): U

    /** Returns true when [unit] holds an [EscapeError]. */
    fun isError(unit: U): Boolean

    /** Unescape a single unit (single-quote syntax). */
    fun unescapeSingle(chars: Chars): U {
        val res =
            when (val scalar = chars.next()) {
                null -> return error2unit(EscapeError.ZeroChars)
                '\\'.code -> unescape1(chars)
                '\n'.code, '\t'.code, '\''.code -> error2unit(EscapeError.EscapeOnlyChar)
                '\r'.code -> error2unit(EscapeError.BareCarriageReturn)
                else -> char2unit(scalar)
            }
        if (isError(res)) {
            return res
        }
        if (chars.next() != null) {
            return error2unit(EscapeError.MoreThanOneChar)
        }
        return res
    }

    /** Unescape the first unit following a leading backslash (double-quote or single-quote syntax). */
    fun unescape1(chars: Chars): U {
        val scalar = chars.next() ?: return error2unit(EscapeError.LoneSlash)
        if (scalar == '0'.code) {
            return ZERO_RESULT
        }
        val simple = simpleEscape(scalar)
        if (simple != null) {
            return nonzeroByte2unit(simple)
        }
        return when (scalar) {
            'x'.code -> {
                val hex = hexEscape(chars)
                val err = hex.errorOrNull()
                if (err != null) {
                    error2unit(err)
                } else {
                    hex2unit(hex.unwrap())
                }
            }
            'u'.code -> {
                val raw = unicodeEscape(chars)
                when (raw) {
                    is UnicodeEscapeParse.Err -> error2unit(raw.error)
                    is UnicodeEscapeParse.Ok -> {
                        val decoded =
                            when {
                                raw.value > MAX_UNICODE_SCALAR ->
                                    CharEscapeOutcome.err(EscapeError.OutOfRangeUnicodeEscape)
                                !isValidUnicodeScalar(raw.value) ->
                                    CharEscapeOutcome.err(EscapeError.LoneSurrogateUnicodeEscape)
                                else ->
                                    CharEscapeOutcome.okCodePoint(raw.value)
                            }
                        unicode2unit(decoded)
                    }
                }
            }
            else -> error2unit(EscapeError.InvalidEscape)
        }
    }

    /**
     * Unescape a double-quoted string literal body and report each step via [callback].
     */
    fun unescape(
        src: String,
        callback: (ByteRange, U) -> Unit,
    ) {
        val chars = Chars(src)
        while (true) {
            val start = chars.byteIndex
            val scalar = chars.next() ?: break
            val res =
                when (scalar) {
                    '\\'.code -> {
                        if (chars.peek() == '\n'.code) {
                            chars.next()
                            skipAsciiWhitespace(chars, start) { range, err ->
                                callback(range, error2unit(err))
                            }
                            continue
                        }
                        unescape1(chars)
                    }
                    '"'.code -> error2unit(EscapeError.EscapeOnlyChar)
                    '\r'.code -> error2unit(EscapeError.BareCarriageReturn)
                    else -> char2unit(scalar)
                }
            val end = chars.byteIndex
            callback(ByteRange(start, end), res)
        }
    }
}

internal object StrUnescape : Unescape<CharEscapeOutcome> {
    override val ZERO_RESULT: CharEscapeOutcome = CharEscapeOutcome.okCodePoint(0)

    override fun nonzeroByte2unit(b: NonZeroU8): CharEscapeOutcome =
        CharEscapeOutcome.okCodePoint(b.get().toInt())

    override fun char2unit(codePoint: Int): CharEscapeOutcome =
        CharEscapeOutcome.okCodePoint(codePoint)

    override fun hex2unit(b: UByte): CharEscapeOutcome =
        if (b <= 0x7Fu) {
            CharEscapeOutcome.okCodePoint(b.toInt())
        } else {
            CharEscapeOutcome.err(EscapeError.OutOfRangeHexEscape)
        }

    override fun unicode2unit(r: CharEscapeOutcome): CharEscapeOutcome = r

    override fun error2unit(error: EscapeError): CharEscapeOutcome = CharEscapeOutcome.err(error)

    override fun isError(unit: CharEscapeOutcome): Boolean = unit.isErr
}

internal object ByteSliceUnescape : Unescape<ByteEscapeOutcome> {
    override val ZERO_RESULT: ByteEscapeOutcome = ByteEscapeOutcome.ok(0u)

    override fun nonzeroByte2unit(b: NonZeroU8): ByteEscapeOutcome = ByteEscapeOutcome.ok(b.get())

    override fun char2unit(codePoint: Int): ByteEscapeOutcome = char2byte(codePoint)

    override fun hex2unit(b: UByte): ByteEscapeOutcome = ByteEscapeOutcome.ok(b)

    override fun unicode2unit(r: CharEscapeOutcome): ByteEscapeOutcome =
        ByteEscapeOutcome.err(EscapeError.UnicodeEscapeInByte)

    override fun error2unit(error: EscapeError): ByteEscapeOutcome = ByteEscapeOutcome.err(error)

    override fun isError(unit: ByteEscapeOutcome): Boolean = unit.isErr
}

internal object CStrUnescape : Unescape<MixedUnitEscapeOutcome> {
    override val ZERO_RESULT: MixedUnitEscapeOutcome =
        MixedUnitEscapeOutcome.err(EscapeError.NulInCStr)

    override fun nonzeroByte2unit(b: NonZeroU8): MixedUnitEscapeOutcome =
        MixedUnitEscapeOutcome.ok(MixedUnit.from(b))

    override fun char2unit(codePoint: Int): MixedUnitEscapeOutcome =
        MixedUnit.tryFromCodePoint(codePoint)

    override fun hex2unit(b: UByte): MixedUnitEscapeOutcome =
        MixedUnit.tryFrom(b)

    override fun unicode2unit(r: CharEscapeOutcome): MixedUnitEscapeOutcome {
        val err = r.errorOrNull()
        return if (err != null) {
            MixedUnitEscapeOutcome.err(err)
        } else {
            char2unit(r.unwrapCodePoint())
        }
    }

    override fun error2unit(error: EscapeError): MixedUnitEscapeOutcome =
        MixedUnitEscapeOutcome.err(error)

    override fun isError(unit: MixedUnitEscapeOutcome): Boolean = unit.isErr
}

/**
 * Interpret a non-nul ASCII escape without the leading backslash.
 */
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

/**
 * Interpret the two hexadecimal digits of a hexadecimal escape without the leading `\x`.
 */
private fun hexEscape(chars: Chars): ByteEscapeOutcome {
    val hiScalar = chars.next() ?: return ByteEscapeOutcome.err(EscapeError.TooShortHexEscape)
    val hi = hexDigitValue(hiScalar) ?: return ByteEscapeOutcome.err(EscapeError.InvalidCharInHexEscape)
    val loScalar = chars.next() ?: return ByteEscapeOutcome.err(EscapeError.TooShortHexEscape)
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

/**
 * Parse the `{...}` hexadecimal digits (and underscores) of a Unicode escape.
 */
private fun unicodeEscape(chars: Chars): UnicodeEscapeParse {
    if (chars.next() != '{'.code) {
        return UnicodeEscapeParse.Err(EscapeError.NoBraceInUnicodeEscape)
    }
    var value =
        when (val first = chars.next()) {
            null -> return UnicodeEscapeParse.Err(EscapeError.UnclosedUnicodeEscape)
            '_'.code -> return UnicodeEscapeParse.Err(EscapeError.LeadingUnderscoreUnicodeEscape)
            '}'.code -> return UnicodeEscapeParse.Err(EscapeError.EmptyUnicodeEscape)
            else -> hexDigitValue(first) ?: return UnicodeEscapeParse.Err(EscapeError.InvalidCharInUnicodeEscape)
        }

    var nDigits = 1
    while (true) {
        when (val next = chars.next()) {
            null -> return UnicodeEscapeParse.Err(EscapeError.UnclosedUnicodeEscape)
            '_'.code -> continue
            '}'.code -> {
                return if (nDigits > 6) {
                    UnicodeEscapeParse.Err(EscapeError.OverlongUnicodeEscape)
                } else {
                    UnicodeEscapeParse.Ok(value)
                }
            }
            else -> {
                val digit =
                    hexDigitValue(next)
                        ?: return UnicodeEscapeParse.Err(EscapeError.InvalidCharInUnicodeEscape)
                nDigits += 1
                if (nDigits > 6) {
                    continue
                }
                value = (value shl 4) + digit
            }
        }
    }
}

/**
 * Interpret a string continuation escape, skipping ASCII whitespace except formfeed,
 * and warning on multiple skipped lines or unskipped Unicode whitespace.
 */
private fun skipAsciiWhitespace(
    chars: Chars,
    start: Int,
    callback: EscapeErrorCallback,
) {
    var sawNewline = false
    while (true) {
        when (chars.peek()) {
            ' '.code, '\t'.code, '\r'.code -> {
                chars.next()
            }
            '\n'.code -> {
                chars.next()
                sawNewline = true
            }
            else -> break
        }
    }
    val end = chars.byteIndex
    if (sawNewline) {
        callback.onError(ByteRange(start, end), EscapeError.MultipleSkippedLinesWarning)
    }
    val unskipped = chars.clone().next()
    if (unskipped != null && isUnicodeWhitespace(unskipped)) {
        callback.onError(
            ByteRange(start, end + utf8ByteLength(unskipped)),
            EscapeError.UnskippedWhitespaceWarning,
        )
    }
}
