package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.codePointToString
import io.github.kotlinmania.rustcliteralescaper.compat.codePointToUtf8Bytes
import io.github.kotlinmania.rustcliteralescaper.compat.isValidUnicodeScalar

/**
 * Half-open UTF-8 byte range `[start, end)` within the literal body.
 */
public class ByteRange(
    public val start: Int,
    public val end: Int,
) {
    public val length: Int
        get() = if (end > start) end - start else 0

    public fun isEmpty(): Boolean = start >= end

    public fun toIntRange(): IntRange = start until end

    override fun equals(other: Any?): Boolean =
        other is ByteRange && start == other.start && end == other.end

    override fun hashCode(): Int = 31 * start + end

    override fun toString(): String = "$start..$end"
}

/**
 * Non-zero 8-bit unsigned byte value.
 */
public class NonZeroU8 private constructor(
    public val value: UByte,
) {
    public fun get(): UByte = value

    override fun equals(other: Any?): Boolean =
        other is NonZeroU8 && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()

    public companion object {
        public fun new(value: UByte): NonZeroU8? =
            if (value == 0u.toUByte()) null else NonZeroU8(value)
    }
}

/**
 * Non-zero Unicode scalar value (`U+0001` through `U+10FFFF`, excluding surrogates).
 */
public class NonZeroChar private constructor(
    public val codePoint: Int,
) {
    public fun get(): Int = codePoint

    public val isBmpChar: Boolean
        get() = codePoint <= Char.MAX_VALUE.code

    public fun toBmpChar(): Char {
        if (codePoint > Char.MAX_VALUE.code) {
            throw IllegalStateException("Supplementary Unicode scalar $codePoint does not fit in a single UTF-16 Char")
        }
        return codePoint.toChar()
    }

    public fun asString(): String = codePointToString(codePoint)

    public fun toUtf8Bytes(): ByteArray = codePointToUtf8Bytes(codePoint)

    override fun equals(other: Any?): Boolean =
        other is NonZeroChar && codePoint == other.codePoint

    override fun hashCode(): Int = codePoint

    override fun toString(): String = asString()

    public companion object {
        public fun new(c: Char): NonZeroChar? = fromCodePoint(c.code)

        public fun new(codePoint: Int): NonZeroChar? = fromCodePoint(codePoint)

        public fun fromCodePoint(codePoint: Int): NonZeroChar? =
            if (codePoint != 0 && isValidUnicodeScalar(codePoint)) {
                NonZeroChar(codePoint)
            } else {
                null
            }
    }
}
