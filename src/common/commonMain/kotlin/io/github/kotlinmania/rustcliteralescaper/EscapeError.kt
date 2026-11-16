package io.github.kotlinmania.rustcliteralescaper

/**
 * Errors and warnings that can occur during string, char, and byte unescaping.
 *
 * Mostly relating to malformed escape sequences, along with a few other problems.
 */
public enum class EscapeError {
    /** Expected 1 char, but 0 were found. */
    ZeroChars,

    /** Expected 1 char, but more than 1 were found. */
    MoreThanOneChar,

    /** Escaped backslash character without continuation. */
    LoneSlash,

    /** Invalid escape character, for example backslash followed by `z`. */
    InvalidEscape,

    /** Raw carriage return encountered. */
    BareCarriageReturn,

    /** Raw carriage return encountered in raw string. */
    BareCarriageReturnInRawString,

    /** Unescaped character that was expected to be escaped, for example raw tab. */
    EscapeOnlyChar,

    /** Numeric character escape is too short, for example a single hex digit. */
    TooShortHexEscape,

    /** Invalid character in numeric escape. */
    InvalidCharInHexEscape,

    /** Character code in numeric escape is non-ASCII. */
    OutOfRangeHexEscape,

    /** Unicode escape not followed by an opening brace. */
    NoBraceInUnicodeEscape,

    /** Non-hexadecimal value in a Unicode escape. */
    InvalidCharInUnicodeEscape,

    /** Empty Unicode escape braces. */
    EmptyUnicodeEscape,

    /** No closing brace in a Unicode escape. */
    UnclosedUnicodeEscape,

    /** Unicode escape begins with a leading underscore. */
    LeadingUnderscoreUnicodeEscape,

    /** More than 6 hexadecimal digits in a Unicode escape. */
    OverlongUnicodeEscape,

    /** Surrogate code point in a Unicode escape. */
    LoneSurrogateUnicodeEscape,

    /** Out of bounds Unicode character code. */
    OutOfRangeUnicodeEscape,

    /** Unicode escape code in a byte literal. */
    UnicodeEscapeInByte,

    /** Non-ASCII character in a byte literal, byte string literal, or raw byte string literal. */
    NonAsciiCharInByte,

    /** Zero byte in a C string literal. */
    NulInCStr,

    /**
     * After a line ending with a backslash, the next line contains whitespace
     * characters that are not skipped.
     */
    UnskippedWhitespaceWarning,

    /** After a line ending with a backslash, multiple lines are skipped. */
    MultipleSkippedLinesWarning,
    ;

    /** Returns true for actual errors, as opposed to warnings. */
    public fun isFatal(): Boolean =
        this != UnskippedWhitespaceWarning && this != MultipleSkippedLinesWarning
}
