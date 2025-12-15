# Pubky-Noise Integration for Bitkit Android

This document describes the integration of pubky-noise FFI bindings into Bitkit Android.

## Overview

Pubky-noise provides encrypted Noise protocol communication for peer-to-peer payments. The integration includes:

- **Kotlin FFI Bindings**: `com.pubky.noise.pubky_noise.kt` - Auto-generated UniFFI bindings
- **Native Libraries**: `libpubky_noise.so` files for each Android ABI
- **NoisePaymentService**: Service that uses `FfiNoiseManager` for encrypted payment communication

## Files Added

1. **FFI Bindings**: `app/src/main/java/com/pubky/noise/pubky_noise.kt`
   - Auto-generated from pubky-noise Rust crate
   - Provides `FfiNoiseManager`, `FfiMobileConfig`, and related types

2. **Updated Services**:
   - `NoisePaymentService.kt` - Now uses `FfiNoiseManager` for real Noise protocol communication
   - `KeyManager.kt` - Manages Ed25519 identity keys for Noise seed derivation
   - `PubkyRingIntegration.kt` - Derives X25519 keys using PaykitMobile FFI

## Building Native Libraries

The native libraries (`libpubky_noise.so`) need to be built from the pubky-noise repository:

```bash
cd /path/to/pubky-noise-main
./build-android.sh
```

This will generate:
- `platforms/android/src/main/jniLibs/arm64-v8a/libpubky_noise.so`
- `platforms/android/src/main/jniLibs/armeabi-v7a/libpubky_noise.so`
- `platforms/android/src/main/jniLibs/x86_64/libpubky_noise.so`

Copy these to Bitkit:
```bash
mkdir -p bitkit-android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}
cp pubky-noise-main/platforms/android/src/main/jniLibs/arm64-v8a/libpubky_noise.so bitkit-android/app/src/main/jniLibs/arm64-v8a/
cp pubky-noise-main/platforms/android/src/main/jniLibs/armeabi-v7a/libpubky_noise.so bitkit-android/app/src/main/jniLibs/armeabi-v7a/
cp pubky-noise-main/platforms/android/src/main/jniLibs/x86_64/libpubky_noise.so bitkit-android/app/src/main/jniLibs/x86_64/
```

## Dependencies

- **JNA**: Already included in Bitkit (`libs.jna`)
- **UniFFI**: Handled automatically by the bindings

## Usage

The `NoisePaymentService` is now fully functional:

```kotlin
// Initialize
noisePaymentService.initialize(paykitClient)

// Send payment request
val response = noisePaymentService.sendPaymentRequest(
    NoisePaymentRequest(
        payerPubkey = myPubkey,
        payeePubkey = recipientPubkey,
        methodId = "lightning",
        amount = "1000",
        currency = "sats"
    )
)
```

## Architecture

1. **Key Derivation**: Ed25519 identity seed → X25519 Noise keys (via PaykitMobile FFI)
2. **Noise Handshake**: 3-step IK handshake using `FfiNoiseManager`
3. **Encrypted Communication**: All payment messages encrypted end-to-end
4. **Directory Discovery**: Uses `DirectoryService` to find Noise endpoints

## Notes

- The native libraries must be built and copied before the app will run
- Server mode (receiving payments) is not yet implemented but the structure is ready
- State persistence for Noise sessions should be added for production use

