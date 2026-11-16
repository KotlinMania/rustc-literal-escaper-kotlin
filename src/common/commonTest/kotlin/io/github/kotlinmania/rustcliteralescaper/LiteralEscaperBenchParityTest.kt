// port-lint: tests benches/benches.rs
package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.Chars
import io.github.kotlinmania.rustcliteralescaper.compat.utf8ByteLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val LEN: Int = 1_000

private class Bencher {
    fun iter(block: () -> Unit) {
        block()
    }
}

private fun <T> blackBox(value: T): T = value

private data class IndexedScalar(
    val byteOffset: Int,
    val scalar: Int,
)

private fun charIndicesOf(s: String): List<IndexedScalar> {
    val out = mutableListOf<IndexedScalar>()
    val chars = Chars(s)
    while (true) {
        val pos = chars.byteIndex
        val scalar = chars.next() ?: break
        out.add(IndexedScalar(pos, scalar))
    }
    return out
}

class LiteralEscaperBenchParityTest {
    private val bencher = Bencher()

    private fun benchSkipAsciiWhitespace(b: Bencher) {
        val input: String =
            blackBox(
                buildString(LEN + 2) {
                    append("\\\n")
                    repeat(LEN - 1) { append(' ') }
                    append('\n')
                },
            )
        assertEquals(LEN, input.encodeToByteArray().size - 2)
        assertTrue(input.contains('\n'))
        b.iter {
            val output = mutableListOf<CharEscapeStep>()
            // This is internal, so call indirectly
            // skipAsciiWhitespace(input.chars(), 0, { range, res -> output.add(...) })
            unescapeStr(input) { range, res -> output.add(CharEscapeStep(range, res)) }
            assertEquals(
                listOf(
                    CharEscapeStep(
                        ByteRange(0, LEN + 2),
                        CharEscapeOutcome.err(EscapeError.MultipleSkippedLinesWarning),
                    ),
                ),
                output,
            )
        }
    }

    private fun benchCheckRawStr(
        b: Bencher,
        s: String,
        expected: List<Int>,
    ) {
        val input: String = blackBox(List(LEN) { s }.joinToString(""))
        val sUtf8Len = s.encodeToByteArray().size
        val sScalars = charIndicesOf(s)
        assertEquals(LEN * sUtf8Len, input.encodeToByteArray().size)
        b.iter {
            val output = ArrayList<CharEscapeStep>(expected.size)
            checkRawStr(input) { range, res -> output.add(CharEscapeStep(range, res)) }
            assertEquals(LEN * sScalars.size, output.size)
            for (i in sScalars.indices) {
                val e = expected[i]
                val p = sScalars[i].byteOffset
                val c = sScalars[i].scalar
                assertEquals(
                    CharEscapeStep(ByteRange(p, p + utf8ByteLength(c)), CharEscapeOutcome.ok(e)),
                    output[i],
                )
            }
        }
    }

    private fun benchCheckRawByteStr(
        b: Bencher,
        s: String,
        expected: List<UByte>,
    ) {
        val input: String = blackBox(List(LEN) { s }.joinToString(""))
        val sUtf8Len = s.encodeToByteArray().size
        val sScalars = charIndicesOf(s)
        assertEquals(LEN * sUtf8Len, input.encodeToByteArray().size)
        b.iter {
            val output = ArrayList<ByteEscapeStep>(expected.size)
            checkRawByteStr(input) { range, res -> output.add(ByteEscapeStep(range, res)) }
            assertEquals(LEN * sScalars.size, output.size)
            for (i in sScalars.indices) {
                val e = expected[i]
                val p = sScalars[i].byteOffset
                val c = sScalars[i].scalar
                assertEquals(
                    ByteEscapeStep(ByteRange(p, p + utf8ByteLength(c)), ByteEscapeOutcome.ok(e)),
                    output[i],
                )
            }
        }
    }

    private fun benchCheckRawCStr(
        b: Bencher,
        s: String,
        expected: List<NonZeroChar>,
    ) {
        val input: String = blackBox(List(LEN) { s }.joinToString(""))
        val sUtf8Len = s.encodeToByteArray().size
        val sScalars = charIndicesOf(s)
        assertEquals(LEN * sUtf8Len, input.encodeToByteArray().size)
        b.iter {
            val output = ArrayList<NonZeroCharEscapeStep>(expected.size)
            checkRawCStr(input) { range, res -> output.add(NonZeroCharEscapeStep(range, res)) }
            assertEquals(LEN * sScalars.size, output.size)
            for (i in sScalars.indices) {
                val e = expected[i]
                val p = sScalars[i].byteOffset
                val c = sScalars[i].scalar
                assertEquals(
                    NonZeroCharEscapeStep(ByteRange(p, p + utf8ByteLength(c)), NonZeroCharEscapeOutcome.ok(e)),
                    output[i],
                )
            }
        }
    }

    private fun benchUnescapeStr(
        b: Bencher,
        s: String,
        expected: List<CharEscapeStep>,
    ) {
        val input: String = blackBox(List(LEN) { s }.joinToString(""))
        b.iter {
            val output = ArrayList<CharEscapeStep>(expected.size)
            unescapeStr(input) { range, res -> output.add(CharEscapeStep(range, res)) }
            for (i in expected.indices) {
                assertEquals(expected[i], output[i])
            }
        }
    }

    private fun benchUnescapeByteStr(
        b: Bencher,
        s: String,
        expected: List<ByteEscapeStep>,
    ) {
        val input: String = blackBox(List(LEN) { s }.joinToString(""))
        b.iter {
            val output = ArrayList<ByteEscapeStep>(expected.size)
            unescapeByteStr(input) { range, res -> output.add(ByteEscapeStep(range, res)) }
            for (i in expected.indices) {
                assertEquals(expected[i], output[i])
            }
        }
    }

    private fun benchUnescapeCStr(
        b: Bencher,
        s: String,
        expected: List<MixedUnitEscapeStep>,
    ) {
        val input: String = blackBox(List(LEN) { s }.joinToString(""))
        b.iter {
            val output = ArrayList<MixedUnitEscapeStep>(expected.size)
            unescapeCStr(input) { range, res -> output.add(MixedUnitEscapeStep(range, res)) }
            for (i in expected.indices) {
                assertEquals(expected[i], output[i])
            }
        }
    }

    @Test
    fun benchSkipAsciiWhitespaceTest() {
        benchSkipAsciiWhitespace(bencher)
    }

    @Test
    fun benchCheckRawStrAscii() {
        benchCheckRawStr(bencher, "a", List(LEN) { 'a'.code })
    }

    @Test
    fun benchCheckRawStrNonAscii() {
        benchCheckRawStr(bencher, "🦀", List(LEN) { 0x1f980 })
    }

    @Test
    fun benchCheckRawStrUnicode() {
        benchCheckRawStr(
            bencher,
            "a🦀🚀z",
            List(4 * LEN) { i ->
                when (i % 4) {
                    0 -> 'a'.code
                    1 -> 0x1f980
                    2 -> 0x1f680
                    3 -> 'z'.code
                    else -> error("unreachable")
                }
            },
        )
    }

    @Test
    fun benchCheckRawByteStrAscii() {
        benchCheckRawByteStr(bencher, "a", List(LEN) { 97u })
    }

    @Test
    fun benchCheckRawCStrAscii() {
        benchCheckRawCStr(bencher, "a", List(LEN) { NonZeroChar.new('a')!! })
    }

    @Test
    fun benchCheckRawCStrNonAscii() {
        benchCheckRawCStr(bencher, "🦀", List(LEN) { NonZeroChar.new(0x1f980)!! })
    }

    @Test
    fun benchCheckRawCStrUnicode() {
        benchCheckRawCStr(
            bencher,
            "a🦀🚀z",
            List(4 * LEN) { i ->
                NonZeroChar.new(
                    when (i % 4) {
                        0 -> 'a'.code
                        1 -> 0x1f980
                        2 -> 0x1f680
                        3 -> 'z'.code
                        else -> error("unreachable")
                    },
                )!!
            },
        )
    }

    @Test
    fun benchUnescapeStrAscii() {
        benchUnescapeStr(
            bencher,
            "a",
            List(LEN) { i -> CharEscapeStep(ByteRange(i, i + 1), CharEscapeOutcome.ok('a')) },
        )
    }

    @Test
    fun benchUnescapeStrNonAscii() {
        benchUnescapeStr(
            bencher,
            "🦀",
            List(LEN) { i -> CharEscapeStep(ByteRange(4 * i, 4 * (i + 1)), CharEscapeOutcome.ok(0x1f980)) },
        )
    }

    @Test
    fun benchUnescapeStrUnicode() {
        val input = "a🦀🚀z"
        val l = input.encodeToByteArray().size
        benchUnescapeStr(
            bencher,
            input,
            List(4 * LEN) { i ->
                when (i % 4) {
                    0 -> CharEscapeStep(ByteRange(i / 4 * l, i / 4 * l + 1), CharEscapeOutcome.ok('a'))
                    1 -> CharEscapeStep(ByteRange(i / 4 * l + 1, i / 4 * l + 5), CharEscapeOutcome.ok(0x1f980))
                    2 -> CharEscapeStep(ByteRange(i / 4 * l + 5, i / 4 * l + 9), CharEscapeOutcome.ok(0x1f680))
                    3 -> CharEscapeStep(ByteRange(i / 4 * l + 9, i / 4 * l + 10), CharEscapeOutcome.ok('z'))
                    else -> error("unreachable")
                }
            },
        )
    }

    @Test
    fun benchUnescapeStrAsciiEscape() {
        benchUnescapeStr(
            bencher,
            "\\n",
            List(LEN) { i -> CharEscapeStep(ByteRange(2 * i, 2 * (i + 1)), CharEscapeOutcome.ok('\n')) },
        )
    }

    @Test
    fun benchUnescapeStrHexEscape() {
        benchUnescapeStr(
            bencher,
            "\\x22",
            List(LEN) { i -> CharEscapeStep(ByteRange(4 * i, 4 * (i + 1)), CharEscapeOutcome.ok('"')) },
        )
    }

    @Test
    fun benchUnescapeStrUnicodeEscape() {
        val input = "\\u{1f980}\\u{1f680}"
        val l = input.encodeToByteArray().size
        benchUnescapeStr(
            bencher,
            input,
            List(2 * LEN) { i ->
                if (i % 2 == 0) {
                    CharEscapeStep(ByteRange(i / 2 * l, i / 2 * l + 9), CharEscapeOutcome.ok(0x1f980))
                } else {
                    CharEscapeStep(ByteRange(i / 2 * l + 9, i / 2 * l + 18), CharEscapeOutcome.ok(0x1f680))
                }
            },
        )
    }

    @Test
    fun benchUnescapeStrMixedEscape() {
        val inputs = listOf("\\n", "\\x22", "\\u{1f980}", "\\u{1f680}")
        val n = inputs.size
        val input = inputs.joinToString("")
        val l = input.encodeToByteArray().size
        benchUnescapeStr(
            bencher,
            input,
            List(n * LEN) { i ->
                when (i % n) {
                    0 -> CharEscapeStep(ByteRange(i / n * l, i / n * l + 2), CharEscapeOutcome.ok('\n'))
                    1 -> CharEscapeStep(ByteRange(i / n * l + 2, i / n * l + 6), CharEscapeOutcome.ok('"'))
                    2 -> CharEscapeStep(ByteRange(i / n * l + 6, i / n * l + 15), CharEscapeOutcome.ok(0x1f980))
                    3 -> CharEscapeStep(ByteRange(i / n * l + 15, i / n * l + 24), CharEscapeOutcome.ok(0x1f680))
                    else -> error("unreachable")
                }
            },
        )
    }

    @Test
    fun benchUnescapeByteStrAscii() {
        benchUnescapeByteStr(
            bencher,
            "a",
            List(LEN) { i -> ByteEscapeStep(ByteRange(i, i + 1), ByteEscapeOutcome.ok(97u)) },
        )
    }

    @Test
    fun benchUnescapeByteStrAsciiEscape() {
        benchUnescapeByteStr(
            bencher,
            "\\n",
            List(LEN) { i -> ByteEscapeStep(ByteRange(2 * i, 2 * (i + 1)), ByteEscapeOutcome.ok(10u)) },
        )
    }

    @Test
    fun benchUnescapeByteStrHexEscape() {
        benchUnescapeByteStr(
            bencher,
            "\\xff",
            List(LEN) { i -> ByteEscapeStep(ByteRange(4 * i, 4 * (i + 1)), ByteEscapeOutcome.ok(255u)) },
        )
    }

    @Test
    fun benchUnescapeByteStrMixedEscape() {
        val inputs = listOf("a", "\\n", "\\xff", "z")
        val input = inputs.joinToString("")
        val n = inputs.size
        val l = input.encodeToByteArray().size
        benchUnescapeByteStr(
            bencher,
            input,
            List(n * LEN) { i ->
                when (i % n) {
                    0 -> ByteEscapeStep(ByteRange(i / n * l, i / n * l + 1), ByteEscapeOutcome.ok(97u))
                    1 -> ByteEscapeStep(ByteRange(i / n * l + 1, i / n * l + 3), ByteEscapeOutcome.ok(10u))
                    2 -> ByteEscapeStep(ByteRange(i / n * l + 3, i / n * l + 7), ByteEscapeOutcome.ok(255u))
                    3 -> ByteEscapeStep(ByteRange(i / n * l + 7, i / n * l + 8), ByteEscapeOutcome.ok(122u))
                    else -> error("unreachable")
                }
            },
        )
    }

    @Test
    fun benchUnescapeCStrAscii() {
        benchUnescapeCStr(
            bencher,
            "a",
            List(LEN) { i -> MixedUnitEscapeStep(ByteRange(i, i + 1), MixedUnit.tryFrom('a')) },
        )
    }

    @Test
    fun benchUnescapeCStrNonAscii() {
        benchUnescapeCStr(
            bencher,
            "🦀",
            List(LEN) { i -> MixedUnitEscapeStep(ByteRange(4 * i, 4 * (i + 1)), MixedUnit.tryFrom(0x1f980)) },
        )
    }

    @Test
    fun benchUnescapeCStrUnicode() {
        val input = "a🦀🚀z"
        val l = input.encodeToByteArray().size
        benchUnescapeCStr(
            bencher,
            input,
            List(4 * LEN) { i ->
                when (i % 4) {
                    0 -> MixedUnitEscapeStep(ByteRange(i / 4 * l, i / 4 * l + 1), MixedUnit.tryFrom('a'))
                    1 -> MixedUnitEscapeStep(ByteRange(i / 4 * l + 1, i / 4 * l + 5), MixedUnit.tryFrom(0x1f980))
                    2 -> MixedUnitEscapeStep(ByteRange(i / 4 * l + 5, i / 4 * l + 9), MixedUnit.tryFrom(0x1f680))
                    3 -> MixedUnitEscapeStep(ByteRange(i / 4 * l + 9, i / 4 * l + 10), MixedUnit.tryFrom('z'))
                    else -> error("unreachable")
                }
            },
        )
    }

    @Test
    fun benchUnescapeCStrAsciiEscape() {
        benchUnescapeCStr(
            bencher,
            "\\n",
            List(LEN) { i -> MixedUnitEscapeStep(ByteRange(2 * i, 2 * (i + 1)), MixedUnit.tryFrom('\n')) },
        )
    }

    @Test
    fun benchUnescapeCStrHexEscapeAscii() {
        benchUnescapeCStr(
            bencher,
            "\\x22",
            List(LEN) { i -> MixedUnitEscapeStep(ByteRange(4 * i, 4 * (i + 1)), MixedUnit.tryFrom('"')) },
        )
    }

    @Test
    fun benchUnescapeCStrHexEscapeByte() {
        benchUnescapeCStr(
            bencher,
            "\\xff",
            List(LEN) { i -> MixedUnitEscapeStep(ByteRange(4 * i, 4 * (i + 1)), MixedUnit.tryFrom(255u.toUByte())) },
        )
    }

    @Test
    fun benchUnescapeCStrUnicodeEscape() {
        benchUnescapeCStr(
            bencher,
            "\\u{1f980}",
            List(LEN) { i -> MixedUnitEscapeStep(ByteRange(9 * i, 9 * (i + 1)), MixedUnit.tryFrom(0x1f980)) },
        )
    }

    @Test
    fun benchUnescapeCStrMixedEscape() {
        val inputs = listOf("\\n", "\\x22", "\\u{1f980}", "\\u{1f680}", "\\xff")
        val n = inputs.size
        val input = inputs.joinToString("")
        val l = input.encodeToByteArray().size
        benchUnescapeCStr(
            bencher,
            input,
            List(n * LEN) { i ->
                when (i % n) {
                    0 -> MixedUnitEscapeStep(ByteRange(i / n * l, i / n * l + 2), MixedUnit.tryFrom('\n'))
                    1 -> MixedUnitEscapeStep(ByteRange(i / n * l + 2, i / n * l + 6), MixedUnit.tryFrom('"'))
                    2 -> MixedUnitEscapeStep(ByteRange(i / n * l + 6, i / n * l + 15), MixedUnit.tryFrom(0x1f980))
                    3 -> MixedUnitEscapeStep(ByteRange(i / n * l + 15, i / n * l + 24), MixedUnit.tryFrom(0x1f680))
                    4 ->
                        MixedUnitEscapeStep(
                            ByteRange(i / n * l + 24, i / n * l + 28),
                            MixedUnit.tryFrom(255u.toUByte()),
                        )
                    else -> error("unreachable")
                }
            },
        )
    }
}
