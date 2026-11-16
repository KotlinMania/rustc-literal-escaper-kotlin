#if canImport(Testing)
import Testing
import RustcLiteralEscaper

@Suite("RustcLiteralEscaper Swift Export Tests")
struct RustcLiteralEscaperExportTests {
    @Test("Unescape BMP and supplementary Unicode character literals")
    func testUnescapeCharAndByte() {
        let ascii = unescapeChar(src: "a")
        #expect(ascii.isOk)
        #expect(ascii.unwrapCodePoint() == 97)

        let crab = unescapeChar(src: "🦀")
        #expect(crab.isOk)
        #expect(crab.unwrapCodePoint() == 0x1F980)
        #expect(crab.unwrapAsString() == "🦀")

        let escapedCat = unescapeChar(src: "\\u{1F63B}")
        #expect(escapedCat.isOk)
        #expect(escapedCat.unwrapCodePoint() == 0x1F63B)

        let badHex = unescapeChar(src: "\\xff")
        #expect(badHex.isErr)
        #expect(badHex.unwrapErr() == EscapeError.OutOfRangeHexEscape)
        #expect(badHex.unwrapErr().isFatal())

        let highByte = unescapeByte(src: "\\xff")
        #expect(highByte.isOk)
        #expect(highByte.unwrap() == 255)
    }

    @Test("Unescape string, C-string MixedUnit, raw literals, and checkForErrors")
    func testUnescapeStringsAndModes() {
        let strSteps = collectUnescapeStr(src: "hi\\n🦀")
        #expect(strSteps.count == 4)
        #expect(strSteps[0].range == ByteRange(start: 0, end: 1))
        #expect(strSteps[2].outcome.unwrapCodePoint() == 10)
        #expect(strSteps[3].range == ByteRange(start: 4, end: 8))
        #expect(strSteps[3].outcome.unwrapCodePoint() == 0x1F980)

        let cSteps = collectUnescapeCStr(src: "A\\xa5\\0")
        #expect(cSteps.count == 3)
        #expect(cSteps[0].outcome.unwrap().isChar)
        #expect(cSteps[1].outcome.unwrap().isHighByte)
        #expect(cSteps[1].outcome.unwrap().highByteOrNull()?.get() == 0xA5)
        #expect(cSteps[2].outcome.unwrapErr() == EscapeError.NulInCStr)

        let rawCSteps = collectCheckRawCStr(src: "ok\r")
        #expect(rawCSteps.count == 3)
        #expect(rawCSteps[2].outcome.unwrapErr() == EscapeError.BareCarriageReturnInRawString)

        #expect(Mode.CStr.inDoubleQuotes())
        #expect(Mode.CStr.prefixNoraw() == "c")
        #expect(!Mode.Byte.inDoubleQuotes())
        #expect(Mode.Byte.prefixNoraw() == "b")

        let warnSteps = collectCheckForErrors(src: "\\\n  \n  x", mode: Mode.Str)
        #expect(warnSteps.count == 1)
        #expect(warnSteps[0].error == EscapeError.MultipleSkippedLinesWarning)
        #expect(!warnSteps[0].error.isFatal())
    }
}
#elseif canImport(XCTest)
import XCTest
import RustcLiteralEscaper

final class RustcLiteralEscaperExportTests: XCTestCase {
    func testUnescapeCharAndByte() {
        let crab = unescapeChar(src: "🦀")
        XCTAssertTrue(crab.isOk)
        XCTAssertEqual(crab.unwrapCodePoint(), 0x1F980)
        XCTAssertEqual(unescapeByte(src: "\\xff").unwrap(), 255)
    }
}
#endif
