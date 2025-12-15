# Paykit Integration Native Libraries

This directory contains pre-built native libraries (.so) for Paykit and Pubky-Noise integration.

## Current Approach (Interim)

Libraries are currently copied from source builds:
- `libpaykit_mobile.so` - from `paykit-rs-master/paykit-mobile`
- `libpubky_noise.so` - from `pubky-noise-main`

## Future: GitHub Packages

Once these libraries are production-ready, they should be published to GitHub Packages following the pattern used for `bitkit-core` and `vss-rust-client-ffi`:

```kotlin
// settings.gradle.kts
maven {
    url = uri("https://maven.pkg.github.com/BitcoinErrorLog/paykit-rs")
    credentials { ... }
}

// build.gradle.kts
implementation("com.paykit:paykit-mobile:0.1.0")
```

This will eliminate the need for copied binaries and ensure automatic updates.

## Rebuilding Libraries

If you need to rebuild the libraries:

```bash
# PaykitMobile
cd paykit-rs-master/paykit-mobile
./build-android.sh --jniLibs

# PubkyNoise
cd pubky-noise-main
./build-android.sh
```

Then copy the generated .so files to the appropriate ABI directories here.

## Supported ABIs

- `arm64-v8a` - ARM 64-bit (most modern devices)
- `x86_64` - x86 64-bit (emulators)
- `armeabi-v7a` - ARM 32-bit (older devices, not currently built)

