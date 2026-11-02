package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.ScalarCursor

/**
 * Literal mode describing the quote syntax and prefix of a Rust literal.
 */
public enum class Mode {
    /** Single-quoted character literal. */
    Char,

    /** Single-quoted byte literal. */
    Byte,

    /** Double-quoted string literal. */
    Str,

    /** Raw string literal. */
    RawStr,

    /** Double-quoted byte string literal. */
    ByteStr,

    /** Raw byte string literal. */
    RawByteStr,

    /** Double-quoted C string literal. */
    CStr,

    /** Raw C string literal. */
    RawCStr,
    ;

    public fun inDoubleQuotes(): Boolean =
        when (this) {
            Str,
            RawStr,
            ByteStr,
            RawByteStr,
            CStr,
            RawCStr,
            -> true
            Char,
            Byte,
            -> false
        }

    public fun prefixNoraw(): String =
        when (this) {
            Char,
            Str,
            RawStr,
            -> ""
            Byte,
            ByteStr,
            RawByteStr,
            -> "b"
            CStr,
            RawCStr,
            -> "c"
        }
}

/**
 * Check a literal body only for escape or validation errors.
 *
 * Takes the contents of a literal without quotes and invokes [errorCallback]
 * for each error or warning encountered.
 */
public fun checkForErrors(
    src: String,
    mode: Mode,
    errorCallback: EscapeErrorCallback,
) {
    when (mode) {
        Mode.Char -> {
            val chars = ScalarCursor(src)
            val outcome = unescapeSingleChar(chars)
            val err = outcome.errorOrNull()
            if (err != null) {
                errorCallback.onError(ByteRange(0, chars.byteIndex), err)
            }
        }
        Mode.Byte -> {
            val chars = ScalarCursor(src)
            val outcome = unescapeSingleByte(chars)
            val err = outcome.errorOrNull()
            if (err != null) {
                errorCallback.onError(ByteRange(0, chars.byteIndex), err)
            }
        }
        Mode.Str ->
            unescapeStr(src) { range, outcome ->
                val err = outcome.errorOrNull()
                if (err != null) {
                    errorCallback.onError(range, err)
                }
            }
        Mode.ByteStr ->
            unescapeByteStr(src) { range, outcome ->
                val err = outcome.errorOrNull()
                if (err != null) {
                    errorCallback.onError(range, err)
                }
            }
        Mode.CStr ->
            unescapeCStr(src) { range, outcome ->
                val err = outcome.errorOrNull()
                if (err != null) {
                    errorCallback.onError(range, err)
                }
            }
        Mode.RawStr ->
            checkRawStr(src) { range, outcome ->
                val err = outcome.errorOrNull()
                if (err != null) {
                    errorCallback.onError(range, err)
                }
            }
        Mode.RawByteStr ->
            checkRawByteStr(src) { range, outcome ->
                val err = outcome.errorOrNull()
                if (err != null) {
                    errorCallback.onError(range, err)
                }
            }
        Mode.RawCStr ->
            checkRawCStr(src) { range, outcome ->
                val err = outcome.errorOrNull()
                if (err != null) {
                    errorCallback.onError(range, err)
                }
            }
    }
}

/**
 * Collect all error and warning steps reported by [checkForErrors] into an immutable list.
 */
public fun collectCheckForErrors(
    src: String,
    mode: Mode,
): List<EscapeErrorStep> {
    val steps = mutableListOf<EscapeErrorStep>()
    checkForErrors(src, mode) { range, error ->
        steps.add(EscapeErrorStep(range, error))
    }
    return steps.toList()
}
