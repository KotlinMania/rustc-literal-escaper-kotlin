package io.github.kotlinmania.rustcliteralescaper

import io.github.kotlinmania.rustcliteralescaper.compat.codePointToString
import io.github.kotlinmania.rustcliteralescaper.compat.codePointToUtf8Bytes
import io.github.kotlinmania.rustcliteralescaper.compat.isValidUnicodeScalar

/**
 * Outcome of unescaping or checking a single Unicode scalar unit.
 */
public class CharEscapeOutcome private constructor(
    private val scalarCodePoint: Int,
    private val escapeError: EscapeError?,
) {
    public val isOk: Boolean
        get() = escapeError == null

    public val isErr: Boolean
        get() = escapeError != null

    public fun codePointOrNull(): Int? = if (escapeError == null) scalarCodePoint else null

    public val isBmpChar: Boolean
        get() = escapeError == null && scalarCodePoint <= Char.MAX_VALUE.code

    public fun asStringOrNull(): String? =
        if (escapeError == null) codePointToString(scalarCodePoint) else null

    public fun toUtf8BytesOrNull(): ByteArray? =
        if (escapeError == null) codePointToUtf8Bytes(scalarCodePoint) else null

    public fun errorOrNull(): EscapeError? = escapeError

    public fun unwrapCodePoint(): Int =
        if (escapeError == null) {
            scalarCodePoint
        } else {
            throw IllegalStateException("Expected Unicode scalar, got $escapeError")
        }

    public fun unwrapChar(): Char {
        val cp = unwrapCodePoint()
        if (cp > Char.MAX_VALUE.code) {
            throw IllegalStateException("Supplementary Unicode scalar $cp does not fit in a single UTF-16 Char")
        }
        return cp.toChar()
    }

    public fun unwrapAsString(): String = codePointToString(unwrapCodePoint())

    public fun unwrapErr(): EscapeError =
        escapeError ?: throw IllegalStateException("Expected EscapeError, got scalar $scalarCodePoint")

    override fun equals(other: Any?): Boolean =
        other is CharEscapeOutcome &&
            scalarCodePoint == other.scalarCodePoint &&
            escapeError == other.escapeError

    override fun hashCode(): Int = 31 * scalarCodePoint + (escapeError?.hashCode() ?: 0)

    override fun toString(): String =
        if (escapeError == null) {
            "Ok(${codePointToString(scalarCodePoint)})"
        } else {
            "Err($escapeError)"
        }

    public companion object {
        public fun ok(c: Char): CharEscapeOutcome = okCodePoint(c.code)

        public fun ok(codePoint: Int): CharEscapeOutcome = okCodePoint(codePoint)

        public fun okCodePoint(codePoint: Int): CharEscapeOutcome {
            require(isValidUnicodeScalar(codePoint)) {
                "Invalid Unicode scalar value: $codePoint"
            }
            return CharEscapeOutcome(codePoint, null)
        }

        public fun err(error: EscapeError): CharEscapeOutcome = CharEscapeOutcome(-1, error)
    }
}

/**
 * Outcome of unescaping or checking a single byte unit.
 */
public class ByteEscapeOutcome private constructor(
    private val byteValue: Int,
    private val escapeError: EscapeError?,
) {
    public val isOk: Boolean
        get() = escapeError == null

    public val isErr: Boolean
        get() = escapeError != null

    public fun byteOrNull(): UByte? = if (escapeError == null) byteValue.toUByte() else null

    public fun errorOrNull(): EscapeError? = escapeError

    public fun unwrap(): UByte =
        if (escapeError == null) {
            byteValue.toUByte()
        } else {
            throw IllegalStateException("Expected byte value, got $escapeError")
        }

    public fun unwrapErr(): EscapeError =
        escapeError ?: throw IllegalStateException("Expected EscapeError, got byte $byteValue")

    override fun equals(other: Any?): Boolean =
        other is ByteEscapeOutcome &&
            byteValue == other.byteValue &&
            escapeError == other.escapeError

    override fun hashCode(): Int = 31 * byteValue + (escapeError?.hashCode() ?: 0)

    override fun toString(): String =
        if (escapeError == null) "Ok(${byteValue.toUByte()})" else "Err($escapeError)"

    public companion object {
        public fun ok(value: UByte): ByteEscapeOutcome = ByteEscapeOutcome(value.toInt(), null)

        public fun err(error: EscapeError): ByteEscapeOutcome = ByteEscapeOutcome(-1, error)
    }
}

/**
 * Outcome of unescaping a single C-string mixed unit.
 */
public class MixedUnitEscapeOutcome private constructor(
    private val mixedUnit: MixedUnit?,
    private val escapeError: EscapeError?,
) {
    public val isOk: Boolean
        get() = escapeError == null

    public val isErr: Boolean
        get() = escapeError != null

    public fun unitOrNull(): MixedUnit? = mixedUnit

    public fun errorOrNull(): EscapeError? = escapeError

    public fun unwrap(): MixedUnit =
        mixedUnit ?: throw IllegalStateException("Expected MixedUnit, got $escapeError")

    public fun unwrapErr(): EscapeError =
        escapeError ?: throw IllegalStateException("Expected EscapeError, got $mixedUnit")

    override fun equals(other: Any?): Boolean =
        other is MixedUnitEscapeOutcome &&
            mixedUnit == other.mixedUnit &&
            escapeError == other.escapeError

    override fun hashCode(): Int = 31 * (mixedUnit?.hashCode() ?: 0) + (escapeError?.hashCode() ?: 0)

    override fun toString(): String =
        if (escapeError == null) "Ok($mixedUnit)" else "Err($escapeError)"

    public companion object {
        public fun ok(unit: MixedUnit): MixedUnitEscapeOutcome = MixedUnitEscapeOutcome(unit, null)

        public fun err(error: EscapeError): MixedUnitEscapeOutcome = MixedUnitEscapeOutcome(null, error)
    }
}

/**
 * Outcome of checking a single raw C-string non-zero character unit.
 */
public class NonZeroCharEscapeOutcome private constructor(
    private val nonZeroChar: NonZeroChar?,
    private val escapeError: EscapeError?,
) {
    public val isOk: Boolean
        get() = escapeError == null

    public val isErr: Boolean
        get() = escapeError != null

    public fun charOrNull(): NonZeroChar? = nonZeroChar

    public fun errorOrNull(): EscapeError? = escapeError

    public fun unwrap(): NonZeroChar =
        nonZeroChar ?: throw IllegalStateException("Expected NonZeroChar, got $escapeError")

    public fun unwrapErr(): EscapeError =
        escapeError ?: throw IllegalStateException("Expected EscapeError, got $nonZeroChar")

    override fun equals(other: Any?): Boolean =
        other is NonZeroCharEscapeOutcome &&
            nonZeroChar == other.nonZeroChar &&
            escapeError == other.escapeError

    override fun hashCode(): Int = 31 * (nonZeroChar?.hashCode() ?: 0) + (escapeError?.hashCode() ?: 0)

    override fun toString(): String =
        if (escapeError == null) "Ok($nonZeroChar)" else "Err($escapeError)"

    public companion object {
        public fun ok(value: NonZeroChar): NonZeroCharEscapeOutcome = NonZeroCharEscapeOutcome(value, null)

        public fun err(error: EscapeError): NonZeroCharEscapeOutcome = NonZeroCharEscapeOutcome(null, error)
    }
}

public fun interface CharEscapeCallback {
    public fun onUnit(
        range: ByteRange,
        outcome: CharEscapeOutcome,
    )
}

public fun interface ByteEscapeCallback {
    public fun onUnit(
        range: ByteRange,
        outcome: ByteEscapeOutcome,
    )
}

public fun interface MixedUnitEscapeCallback {
    public fun onUnit(
        range: ByteRange,
        outcome: MixedUnitEscapeOutcome,
    )
}

public fun interface NonZeroCharEscapeCallback {
    public fun onUnit(
        range: ByteRange,
        outcome: NonZeroCharEscapeOutcome,
    )
}

public fun interface EscapeErrorCallback {
    public fun onError(
        range: ByteRange,
        error: EscapeError,
    )
}

public class CharEscapeStep(
    public val range: ByteRange,
    public val outcome: CharEscapeOutcome,
) {
    override fun equals(other: Any?): Boolean =
        other is CharEscapeStep && range == other.range && outcome == other.outcome

    override fun hashCode(): Int = 31 * range.hashCode() + outcome.hashCode()

    override fun toString(): String = "($range, $outcome)"
}

public class ByteEscapeStep(
    public val range: ByteRange,
    public val outcome: ByteEscapeOutcome,
) {
    override fun equals(other: Any?): Boolean =
        other is ByteEscapeStep && range == other.range && outcome == other.outcome

    override fun hashCode(): Int = 31 * range.hashCode() + outcome.hashCode()

    override fun toString(): String = "($range, $outcome)"
}

public class MixedUnitEscapeStep(
    public val range: ByteRange,
    public val outcome: MixedUnitEscapeOutcome,
) {
    override fun equals(other: Any?): Boolean =
        other is MixedUnitEscapeStep && range == other.range && outcome == other.outcome

    override fun hashCode(): Int = 31 * range.hashCode() + outcome.hashCode()

    override fun toString(): String = "($range, $outcome)"
}

public class NonZeroCharEscapeStep(
    public val range: ByteRange,
    public val outcome: NonZeroCharEscapeOutcome,
) {
    override fun equals(other: Any?): Boolean =
        other is NonZeroCharEscapeStep && range == other.range && outcome == other.outcome

    override fun hashCode(): Int = 31 * range.hashCode() + outcome.hashCode()

    override fun toString(): String = "($range, $outcome)"
}

public class EscapeErrorStep(
    public val range: ByteRange,
    public val error: EscapeError,
) {
    override fun equals(other: Any?): Boolean =
        other is EscapeErrorStep && range == other.range && error == other.error

    override fun hashCode(): Int = 31 * range.hashCode() + error.hashCode()

    override fun toString(): String = "($range, $error)"
}
