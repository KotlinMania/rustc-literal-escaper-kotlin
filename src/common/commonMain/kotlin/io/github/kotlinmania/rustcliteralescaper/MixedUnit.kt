package io.github.kotlinmania.rustcliteralescaper

internal typealias Error = EscapeError

/**
 * Unit representing either a non-zero Unicode scalar or a non-ASCII high byte.
 *
 * Used for mixed UTF-8 C string literals that allow both Unicode characters and raw high bytes.
 */
public class MixedUnit private constructor(
    private val charValue: NonZeroChar?,
    private val highByteValue: NonZeroU8?,
) {
    public val isChar: Boolean
        get() = charValue != null

    public val isHighByte: Boolean
        get() = highByteValue != null

    public fun charOrNull(): NonZeroChar? = charValue

    public fun highByteOrNull(): NonZeroU8? = highByteValue

    public fun toUtf8Bytes(): ByteArray =
        when {
            charValue != null -> charValue.toUtf8Bytes()
            highByteValue != null -> byteArrayOf(highByteValue.get().toByte())
            else -> ByteArray(0)
        }

    override fun equals(other: Any?): Boolean =
        other is MixedUnit &&
            charValue == other.charValue &&
            highByteValue == other.highByteValue

    override fun hashCode(): Int =
        31 * (charValue?.hashCode() ?: 0) + (highByteValue?.hashCode() ?: 0)

    override fun toString(): String =
        when {
            charValue != null -> "MixedUnit.Char(${charValue.codePoint})"
            highByteValue != null -> "MixedUnit.HighByte(${highByteValue.get()})"
            else -> "MixedUnit"
        }

    public companion object {
        public fun char(c: NonZeroChar): MixedUnit = MixedUnit(c, null)

        public fun highByte(byte: NonZeroU8): MixedUnit = MixedUnit(null, byte)

        public fun from(c: NonZeroChar): MixedUnit = char(c)

        public fun from(byte: NonZeroU8): MixedUnit =
            if (byte.get() <= 0x7Fu) {
                val nonZero =
                    NonZeroChar.fromCodePoint(byte.get().toInt())
                        ?: error("ASCII NonZeroU8 must be a valid NonZeroChar")
                char(nonZero)
            } else {
                highByte(byte)
            }

        public fun tryFrom(c: Char): MixedUnitEscapeOutcome = tryFromCodePoint(c.code)

        public fun tryFrom(codePoint: Int): MixedUnitEscapeOutcome = tryFromCodePoint(codePoint)

        public fun tryFromCodePoint(codePoint: Int): MixedUnitEscapeOutcome =
            NonZeroChar.fromCodePoint(codePoint)?.let { MixedUnitEscapeOutcome.ok(char(it)) }
                ?: MixedUnitEscapeOutcome.err(EscapeError.NulInCStr)

        public fun tryFrom(byte: UByte): MixedUnitEscapeOutcome =
            NonZeroU8.new(byte)?.let { MixedUnitEscapeOutcome.ok(from(it)) }
                ?: MixedUnitEscapeOutcome.err(EscapeError.NulInCStr)
    }
}
