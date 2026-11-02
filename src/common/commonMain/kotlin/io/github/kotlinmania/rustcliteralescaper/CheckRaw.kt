package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.Chars

internal typealias RawUnit = CharEscapeOutcome

/**
 * Check a raw string literal for validity.
 *
 * Takes the contents of a raw string literal without quotes and produces a
 * sequence of characters or errors, which are returned by invoking [callback].
 * Note: Does no escaping, but produces errors for bare carriage return (`'\r'`).
 */
public fun checkRawStr(
    src: String,
    callback: CharEscapeCallback,
) {
    StrCheckRaw.checkRaw(src, callback::onUnit)
}

/**
 * Check a raw byte string literal for validity.
 *
 * Takes the contents of a raw byte string literal without quotes and produces a
 * sequence of bytes or errors, which are returned by invoking [callback].
 * Note: Does no escaping, but produces errors for bare carriage return (`'\r'`).
 */
public fun checkRawByteStr(
    src: String,
    callback: ByteEscapeCallback,
) {
    ByteSliceCheckRaw.checkRaw(src, callback::onUnit)
}

/**
 * Check a raw C string literal for validity.
 *
 * Takes the contents of a raw C string literal without quotes and produces a
 * sequence of non-zero characters or errors, which are returned by invoking [callback].
 * Note: Does no escaping, but produces errors for bare carriage return (`'\r'`).
 */
public fun checkRawCStr(
    src: String,
    callback: NonZeroCharEscapeCallback,
) {
    CStrCheckRaw.checkRaw(src, callback::onUnit)
}

/**
 * Collect all steps produced by [checkRawStr] into an immutable list.
 */
public fun collectCheckRawStr(src: String): List<CharEscapeStep> {
    val steps = mutableListOf<CharEscapeStep>()
    checkRawStr(src) { range, outcome ->
        steps.add(CharEscapeStep(range, outcome))
    }
    return steps.toList()
}

/**
 * Collect all steps produced by [checkRawByteStr] into an immutable list.
 */
public fun collectCheckRawByteStr(src: String): List<ByteEscapeStep> {
    val steps = mutableListOf<ByteEscapeStep>()
    checkRawByteStr(src) { range, outcome ->
        steps.add(ByteEscapeStep(range, outcome))
    }
    return steps.toList()
}

/**
 * Collect all steps produced by [checkRawCStr] into an immutable list.
 */
public fun collectCheckRawCStr(src: String): List<NonZeroCharEscapeStep> {
    val steps = mutableListOf<NonZeroCharEscapeStep>()
    checkRawCStr(src) { range, outcome ->
        steps.add(NonZeroCharEscapeStep(range, outcome))
    }
    return steps.toList()
}

/**
 * Internal interface for checking raw string literals for validity.
 */
internal interface CheckRaw<RawUnit> {
    /** Converts Unicode scalars to the unit outcome of the literal type. */
    fun char2rawUnit(codePoint: Int): RawUnit

    /** Outcome produced when a bare carriage return (`'\r'`) is encountered. */
    fun bareCarriageReturnUnit(): RawUnit

    /**
     * Takes the contents of a raw literal without quotes and produces a
     * sequence of unit outcomes via [callback].
     */
    fun checkRaw(
        src: String,
        callback: (ByteRange, RawUnit) -> Unit,
    ) {
        val chars = Chars(src)
        while (true) {
            val start = chars.byteIndex
            val scalar = chars.next() ?: break
            val outcome =
                if (scalar == '\r'.code) {
                    bareCarriageReturnUnit()
                } else {
                    char2rawUnit(scalar)
                }
            callback(ByteRange(start, chars.byteIndex), outcome)
        }
    }
}

private object StrCheckRaw : CheckRaw<CharEscapeOutcome> {
    override fun char2rawUnit(codePoint: Int): CharEscapeOutcome = CharEscapeOutcome.okCodePoint(codePoint)

    override fun bareCarriageReturnUnit(): CharEscapeOutcome =
        CharEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)
}

private object ByteSliceCheckRaw : CheckRaw<ByteEscapeOutcome> {
    override fun char2rawUnit(codePoint: Int): ByteEscapeOutcome = char2byte(codePoint)

    override fun bareCarriageReturnUnit(): ByteEscapeOutcome =
        ByteEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)
}

private object CStrCheckRaw : CheckRaw<NonZeroCharEscapeOutcome> {
    override fun char2rawUnit(codePoint: Int): NonZeroCharEscapeOutcome =
        NonZeroChar.fromCodePoint(codePoint)?.let(NonZeroCharEscapeOutcome::ok)
            ?: NonZeroCharEscapeOutcome.err(EscapeError.NulInCStr)

    override fun bareCarriageReturnUnit(): NonZeroCharEscapeOutcome =
        NonZeroCharEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)
}

/** Turn an ASCII scalar into a byte outcome. */
internal fun char2byte(codePoint: Int): ByteEscapeOutcome =
    if (codePoint in 0..0x7F) {
        ByteEscapeOutcome.ok(codePoint.toUByte())
    } else {
        ByteEscapeOutcome.err(EscapeError.NonAsciiCharInByte)
    }
