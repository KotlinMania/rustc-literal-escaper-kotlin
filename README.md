# rustc-literal-escaper-kotlin

Kotlin Multiplatform port of [rust-lang/literal-escaper](https://github.com/rust-lang/literal-escaper) (`rustc-literal-escaper` 0.0.8).

Utilities for validating raw and escaped character, byte, string, byte-string, and C-string literals and turning escape sequences into the values they represent. Consumed by [`proc-macro-kotlin`](https://github.com/KotlinMania/proc-macro-kotlin).

```kotlin
import io.github.kotlinmania.rustcliteralescaper.EscapeError
import io.github.kotlinmania.rustcliteralescaper.MixedUnit
import io.github.kotlinmania.rustcliteralescaper.Mode
import io.github.kotlinmania.rustcliteralescaper.checkForErrors
import io.github.kotlinmania.rustcliteralescaper.checkRawByteStr
import io.github.kotlinmania.rustcliteralescaper.checkRawCStr
import io.github.kotlinmania.rustcliteralescaper.checkRawStr
import io.github.kotlinmania.rustcliteralescaper.unescapeByte
import io.github.kotlinmania.rustcliteralescaper.unescapeByteStr
import io.github.kotlinmania.rustcliteralescaper.unescapeCStr
import io.github.kotlinmania.rustcliteralescaper.unescapeChar
import io.github.kotlinmania.rustcliteralescaper.unescapeStr
```

## Targets

Kotlin Multiplatform: macOS ARM64; Linux x64/ARM64; Windows x64;
iOS, tvOS, and watchOS ARM64 device and simulator targets; Android Native
ARM64/x64; JS and Wasm-JS (browser + Node); Wasm-WASI; Android; and JVM.

## Build

Swift Export and its test harness require macOS 15 or newer. The Swift
workflow uses the template's macOS 26 runner for its Xcode SDK.

```bash
./gradlew build
./gradlew test
```

## Licenses

Upstream `rustc-literal-escaper` is `Apache-2.0 OR MIT`. The same dual license
applies to this port. License texts are mirrored from upstream as
[`LICENSE-APACHE`](./LICENSE-APACHE) and [`LICENSE-MIT`](./LICENSE-MIT).

Upstream README is preserved verbatim as [`README-UPSTREAM.md`](./README-UPSTREAM.md).

## Installation

```kotlin
// build.gradle.kts
commonMain.dependencies {
    implementation("io.github.kotlinmania:rustc-literal-escaper-kotlin:0.1.0")
}
```
