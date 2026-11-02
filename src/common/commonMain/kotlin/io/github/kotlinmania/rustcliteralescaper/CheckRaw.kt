package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.ScalarCursor

/**
 * Check a raw string literal for validity.
 *
 * Takes the contents of a raw string literal without quotes and produces a
 * sequence of characters or errors through [callback].
 * Does no escaping, but produces errors for bare carriage return.
 */
public fun checkRawStr(
    src: String,
    callback: CharEscapeCallback,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val scalar = chars.nextCodePoint() ?: break
        val outcome =
            if (scalar == '\r'.code) {
                CharEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)
            } else {
                CharEscapeOutcome.okCodePoint(scalar)
            }
        callback.onUnit(ByteRange(start, chars.byteIndex), outcome)
    }
}

/**
 * Check a raw byte string literal for validity.
 *
 * Takes the contents of a raw byte string literal without quotes and produces a
 * sequence of bytes or errors through [callback].
 * Does no escaping, but produces errors for bare carriage return.
 */
public fun checkRawByteStr(
    src: String,
    callback: ByteEscapeCallback,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val scalar = chars.nextCodePoint() ?: break
        val outcome =
            if (scalar == '\r'.code) {
                ByteEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)
            } else {
                char2byte(scalar)
            }
        callback.onUnit(ByteRange(start, chars.byteIndex), outcome)
    }
}

/**
 * Check a raw C string literal for validity.
 *
 * Takes the contents of a raw C string literal without quotes and produces a
 * sequence of non-zero characters or errors through [callback].
 * Does no escaping, but produces errors for bare carriage return.
 */
public fun checkRawCStr(
    src: String,
    callback: NonZeroCharEscapeCallback,
) {
    val chars = ScalarCursor(src)
    while (true) {
        val start = chars.byteIndex
        val scalar = chars.nextCodePoint() ?: break
        val outcome =
            if (scalar == '\r'.code) {
                NonZeroCharEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)
            } else {
                NonZeroChar.fromCodePoint(scalar)?.let(NonZeroCharEscapeOutcome::ok)
                    ?: NonZeroCharEscapeOutcome.err(EscapeError.NulInCStr)
            }
        callback.onUnit(ByteRange(start, chars.byteIndex), outcome)
    }
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

/** Turn an ASCII scalar into a byte outcome. */
internal fun char2byte(codePoint: Int): ByteEscapeOutcome =
    if (codePoint in 0..0x7F) {
        ByteEscapeOutcome.ok(codePoint.toUByte())
    } else {
        ByteEscapeOutcome.err(EscapeError.NonAsciiCharInByte)
    }
