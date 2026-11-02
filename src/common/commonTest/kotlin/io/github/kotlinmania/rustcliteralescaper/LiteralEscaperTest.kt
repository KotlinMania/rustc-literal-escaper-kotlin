// port-lint: tests tests/test.rs
package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.codePointToString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiteralEscaperTest {
    @Test
    fun testUnescapeCharBad() {
        fun check(
            literalText: String,
            expectedError: EscapeError,
        ) {
            assertEquals(CharEscapeOutcome.err(expectedError), unescapeChar(literalText))
        }
        check("", EscapeError.ZeroChars)
        check("\\", EscapeError.LoneSlash)
        check("\n", EscapeError.EscapeOnlyChar)
        check("\t", EscapeError.EscapeOnlyChar)
        check("'", EscapeError.EscapeOnlyChar)
        check("\r", EscapeError.BareCarriageReturn)
        check("spam", EscapeError.MoreThanOneChar)
        check("\\x0ff", EscapeError.MoreThanOneChar)
        check("\\\"a", EscapeError.MoreThanOneChar)
        check("\\na", EscapeError.MoreThanOneChar)
        check("\\ra", EscapeError.MoreThanOneChar)
        check("\\ta", EscapeError.MoreThanOneChar)
        check("\\\\a", EscapeError.MoreThanOneChar)
        check("\\'a", EscapeError.MoreThanOneChar)
        check("\\0a", EscapeError.MoreThanOneChar)
        check("\\u{0}x", EscapeError.MoreThanOneChar)
        check("\\u{1F63b}}", EscapeError.MoreThanOneChar)
        check("\\v", EscapeError.InvalidEscape)
        check("\\💩", EscapeError.InvalidEscape)
        check("\\●", EscapeError.InvalidEscape)
        check("\\\r", EscapeError.InvalidEscape)
        check("\\x", EscapeError.TooShortHexEscape)
        check("\\x0", EscapeError.TooShortHexEscape)
        check("\\xf", EscapeError.TooShortHexEscape)
        check("\\xa", EscapeError.TooShortHexEscape)
        check("\\xx", EscapeError.InvalidCharInHexEscape)
        check("\\xы", EscapeError.InvalidCharInHexEscape)
        check("\\x🦀", EscapeError.InvalidCharInHexEscape)
        check("\\xtt", EscapeError.InvalidCharInHexEscape)
        check("\\xff", EscapeError.OutOfRangeHexEscape)
        check("\\xFF", EscapeError.OutOfRangeHexEscape)
        check("\\x80", EscapeError.OutOfRangeHexEscape)
        check("\\u", EscapeError.NoBraceInUnicodeEscape)
        check("\\u[0123]", EscapeError.NoBraceInUnicodeEscape)
        check("\\u{0x}", EscapeError.InvalidCharInUnicodeEscape)
        check("\\u{", EscapeError.UnclosedUnicodeEscape)
        check("\\u{0000", EscapeError.UnclosedUnicodeEscape)
        check("\\u{}", EscapeError.EmptyUnicodeEscape)
        check("\\u{_0000}", EscapeError.LeadingUnderscoreUnicodeEscape)
        check("\\u{0000000}", EscapeError.OverlongUnicodeEscape)
        check("\\u{FFFFFF}", EscapeError.OutOfRangeUnicodeEscape)
        check("\\u{ffffff}", EscapeError.OutOfRangeUnicodeEscape)
        check("\\u{ffffff}", EscapeError.OutOfRangeUnicodeEscape)
        check("\\u{DC00}", EscapeError.LoneSurrogateUnicodeEscape)
        check("\\u{DDDD}", EscapeError.LoneSurrogateUnicodeEscape)
        check("\\u{DFFF}", EscapeError.LoneSurrogateUnicodeEscape)
        check("\\u{D800}", EscapeError.LoneSurrogateUnicodeEscape)
        check("\\u{DAAA}", EscapeError.LoneSurrogateUnicodeEscape)
        check("\\u{DBFF}", EscapeError.LoneSurrogateUnicodeEscape)
    }

    @Test
    fun testUnescapeCharGood() {
        fun check(
            literalText: String,
            expectedChar: Char,
        ) {
            assertEquals(CharEscapeOutcome.ok(expectedChar), unescapeChar(literalText))
        }

        fun check(
            literalText: String,
            expectedCodePoint: Int,
        ) {
            assertEquals(CharEscapeOutcome.ok(expectedCodePoint), unescapeChar(literalText))
        }
        check("a", 'a')
        check("ы", '\u044b')
        check("🦀", 0x1f980)
        check("\\\"", '"')
        check("\\n", '\n')
        check("\\r", '\r')
        check("\\t", '\t')
        check("\\\\", '\\')
        check("\\'", '\'')
        check("\\0", '\u0000')
        check("\\x00", '\u0000')
        check("\\x5a", 'Z')
        check("\\x5A", 'Z')
        check("\\x7f", 127.toChar())
        check("\\u{0}", '\u0000')
        check("\\u{000000}", '\u0000')
        check("\\u{41}", 'A')
        check("\\u{0041}", 'A')
        check("\\u{00_41}", 'A')
        check("\\u{4__1__}", 'A')
        check("\\u{1F63b}", 0x1f63b)
    }

    @Test
    fun testUnescapeStrWarn() {
        fun check(
            literal: String,
            expected: List<CharEscapeStep>,
        ) {
            val unescaped = ArrayList<CharEscapeStep>(literal.length)
            unescapeStr(literal) { range, res -> unescaped.add(CharEscapeStep(range, res)) }
            assertEquals(expected, unescaped)
        }
        // Check we can handle escaped newlines at the end of a file.
        check("\\\n", listOf())
        check("\\\n ", listOf())
        check(
            "\\\n \u00a0 x",
            listOf(
                CharEscapeStep(ByteRange(0, 5), CharEscapeOutcome.err(EscapeError.UnskippedWhitespaceWarning)),
                CharEscapeStep(ByteRange(3, 5), CharEscapeOutcome.ok('\u00a0')),
                CharEscapeStep(ByteRange(5, 6), CharEscapeOutcome.ok(' ')),
                CharEscapeStep(ByteRange(6, 7), CharEscapeOutcome.ok('x')),
            ),
        )
        check(
            "\\\n  \n  x",
            listOf(
                CharEscapeStep(ByteRange(0, 7), CharEscapeOutcome.err(EscapeError.MultipleSkippedLinesWarning)),
                CharEscapeStep(ByteRange(7, 8), CharEscapeOutcome.ok('x')),
            ),
        )
    }

    @Test
    fun testUnescapeStrGood() {
        fun check(
            literalText: String,
            expected: String,
        ) {
            val builder = StringBuilder(literalText.length)
            var firstError: EscapeErrorStep? = null
            unescapeStr(literalText) { range, c ->
                if (firstError == null) {
                    if (c.isOk) {
                        builder.append(codePointToString(c.unwrapCodePoint()))
                    } else {
                        firstError = EscapeErrorStep(range, c.unwrapErr())
                    }
                }
            }
            assertEquals(null, firstError)
            assertEquals(expected, builder.toString())
        }
        check("foo", "foo")
        check("", "")
        check(" \t\n", " \t\n")
        check("hello \\\n     world", "hello world")
        check("thread's", "thread's")
    }

    @Test
    fun testUnescapeByteBad() {
        fun check(
            literalText: String,
            expectedError: EscapeError,
        ) {
            assertEquals(ByteEscapeOutcome.err(expectedError), unescapeByte(literalText))
        }
        check("", EscapeError.ZeroChars)
        check("\\", EscapeError.LoneSlash)
        check("\n", EscapeError.EscapeOnlyChar)
        check("\t", EscapeError.EscapeOnlyChar)
        check("'", EscapeError.EscapeOnlyChar)
        check("\r", EscapeError.BareCarriageReturn)
        check("spam", EscapeError.MoreThanOneChar)
        check("\\x0ff", EscapeError.MoreThanOneChar)
        check("\\\"a", EscapeError.MoreThanOneChar)
        check("\\na", EscapeError.MoreThanOneChar)
        check("\\ra", EscapeError.MoreThanOneChar)
        check("\\ta", EscapeError.MoreThanOneChar)
        check("\\\\a", EscapeError.MoreThanOneChar)
        check("\\'a", EscapeError.MoreThanOneChar)
        check("\\0a", EscapeError.MoreThanOneChar)
        check("\\v", EscapeError.InvalidEscape)
        check("\\💩", EscapeError.InvalidEscape)
        check("\\●", EscapeError.InvalidEscape)
        check("\\x", EscapeError.TooShortHexEscape)
        check("\\x0", EscapeError.TooShortHexEscape)
        check("\\xa", EscapeError.TooShortHexEscape)
        check("\\xf", EscapeError.TooShortHexEscape)
        check("\\xx", EscapeError.InvalidCharInHexEscape)
        check("\\xы", EscapeError.InvalidCharInHexEscape)
        check("\\x🦀", EscapeError.InvalidCharInHexEscape)
        check("\\xtt", EscapeError.InvalidCharInHexEscape)
        check("\\u", EscapeError.NoBraceInUnicodeEscape)
        check("\\u[0123]", EscapeError.NoBraceInUnicodeEscape)
        check("\\u{0x}", EscapeError.InvalidCharInUnicodeEscape)
        check("\\u{", EscapeError.UnclosedUnicodeEscape)
        check("\\u{0000", EscapeError.UnclosedUnicodeEscape)
        check("\\u{}", EscapeError.EmptyUnicodeEscape)
        check("\\u{_0000}", EscapeError.LeadingUnderscoreUnicodeEscape)
        check("\\u{0000000}", EscapeError.OverlongUnicodeEscape)
        check("ы", EscapeError.NonAsciiCharInByte)
        check("🦀", EscapeError.NonAsciiCharInByte)
        check("\\u{0}", EscapeError.UnicodeEscapeInByte)
        check("\\u{000000}", EscapeError.UnicodeEscapeInByte)
        check("\\u{41}", EscapeError.UnicodeEscapeInByte)
        check("\\u{0041}", EscapeError.UnicodeEscapeInByte)
        check("\\u{00_41}", EscapeError.UnicodeEscapeInByte)
        check("\\u{4__1__}", EscapeError.UnicodeEscapeInByte)
        check("\\u{1F63b}", EscapeError.UnicodeEscapeInByte)
        check("\\u{0}x", EscapeError.UnicodeEscapeInByte)
        check("\\u{1F63b}}", EscapeError.UnicodeEscapeInByte)
        check("\\u{FFFFFF}", EscapeError.UnicodeEscapeInByte)
        check("\\u{ffffff}", EscapeError.UnicodeEscapeInByte)
        check("\\u{ffffff}", EscapeError.UnicodeEscapeInByte)
        check("\\u{DC00}", EscapeError.UnicodeEscapeInByte)
        check("\\u{DDDD}", EscapeError.UnicodeEscapeInByte)
        check("\\u{DFFF}", EscapeError.UnicodeEscapeInByte)
        check("\\u{D800}", EscapeError.UnicodeEscapeInByte)
        check("\\u{DAAA}", EscapeError.UnicodeEscapeInByte)
        check("\\u{DBFF}", EscapeError.UnicodeEscapeInByte)
    }

    @Test
    fun testUnescapeByteGood() {
        fun check(
            literalText: String,
            expectedByte: UByte,
        ) {
            assertEquals(ByteEscapeOutcome.ok(expectedByte), unescapeByte(literalText))
        }
        check("a", 97u)
        check("\\\"", 34u)
        check("\\n", 10u)
        check("\\r", 13u)
        check("\\t", 9u)
        check("\\\\", 92u)
        check("\\'", 39u)
        check("\\0", 0u)
        check("\\x00", 0u)
        check("\\x5a", 90u)
        check("\\x5A", 90u)
        check("\\x7f", 127u)
        check("\\x80", 128u)
        check("\\xff", 255u)
        check("\\xFF", 255u)
    }

    @Test
    fun testUnescapeByteStrGood() {
        fun check(
            literalText: String,
            expected: ByteArray,
        ) {
            val buf = ArrayList<Byte>(literalText.length)
            var firstError: EscapeErrorStep? = null
            unescapeByteStr(literalText) { range, res ->
                if (firstError == null) {
                    if (res.isOk) {
                        buf.add(res.unwrap().toByte())
                    } else {
                        firstError = EscapeErrorStep(range, res.unwrapErr())
                    }
                }
            }
            assertEquals(null, firstError)
            assertContentEquals(expected, buf.toByteArray())
        }
        check("foo", "foo".encodeToByteArray())
        check("", "".encodeToByteArray())
        check(" \t\n", " \t\n".encodeToByteArray())
        check("hello \\\n     world", "hello world".encodeToByteArray())
        check("thread's", "thread's".encodeToByteArray())
    }

    @Test
    fun testUnescapeRawStr() {
        fun check(
            literal: String,
            expected: List<CharEscapeStep>,
        ) {
            val unescaped = ArrayList<CharEscapeStep>(literal.length)
            checkRawStr(literal) { range, res -> unescaped.add(CharEscapeStep(range, res)) }
            assertEquals(expected, unescaped)
        }
        check(
            "\r",
            listOf(
                CharEscapeStep(ByteRange(0, 1), CharEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)),
            ),
        )
        check(
            "\rx",
            listOf(
                CharEscapeStep(ByteRange(0, 1), CharEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)),
                CharEscapeStep(ByteRange(1, 2), CharEscapeOutcome.ok('x')),
            ),
        )
    }

    @Test
    fun testUnescapeRawByteStr() {
        fun check(
            literal: String,
            expected: List<ByteEscapeStep>,
        ) {
            val unescaped = ArrayList<ByteEscapeStep>(literal.length)
            checkRawByteStr(literal) { range, res -> unescaped.add(ByteEscapeStep(range, res)) }
            assertEquals(expected, unescaped)
        }
        check(
            "\r",
            listOf(
                ByteEscapeStep(ByteRange(0, 1), ByteEscapeOutcome.err(EscapeError.BareCarriageReturnInRawString)),
            ),
        )
        check(
            "🦀",
            listOf(
                ByteEscapeStep(ByteRange(0, 4), ByteEscapeOutcome.err(EscapeError.NonAsciiCharInByte)),
            ),
        )
        check(
            "🦀a",
            listOf(
                ByteEscapeStep(ByteRange(0, 4), ByteEscapeOutcome.err(EscapeError.NonAsciiCharInByte)),
                ByteEscapeStep(ByteRange(4, 5), ByteEscapeOutcome.ok(97u)),
            ),
        )
    }

    @Test
    fun testModeAndCheckForErrors() {
        assertFalse(Mode.Char.inDoubleQuotes())
        assertFalse(Mode.Byte.inDoubleQuotes())
        assertTrue(Mode.Str.inDoubleQuotes())
        assertTrue(Mode.RawStr.inDoubleQuotes())
        assertTrue(Mode.ByteStr.inDoubleQuotes())
        assertTrue(Mode.RawByteStr.inDoubleQuotes())
        assertTrue(Mode.CStr.inDoubleQuotes())
        assertTrue(Mode.RawCStr.inDoubleQuotes())

        assertEquals("", Mode.Char.prefixNoraw())
        assertEquals("", Mode.Str.prefixNoraw())
        assertEquals("", Mode.RawStr.prefixNoraw())
        assertEquals("b", Mode.Byte.prefixNoraw())
        assertEquals("b", Mode.ByteStr.prefixNoraw())
        assertEquals("b", Mode.RawByteStr.prefixNoraw())
        assertEquals("c", Mode.CStr.prefixNoraw())
        assertEquals("c", Mode.RawCStr.prefixNoraw())

        assertTrue(EscapeError.ZeroChars.isFatal())
        assertFalse(EscapeError.UnskippedWhitespaceWarning.isFatal())
        assertFalse(EscapeError.MultipleSkippedLinesWarning.isFatal())

        assertEquals(
            listOf(EscapeErrorStep(ByteRange(0, 2), EscapeError.NulInCStr)),
            collectCheckForErrors("\\0", Mode.CStr),
        )
        assertEquals(
            listOf(EscapeErrorStep(ByteRange(1, 2), EscapeError.NulInCStr)),
            collectCheckForErrors("a\u0000b", Mode.RawCStr),
        )
    }
}
