# 32-bit Native Library Deployment Guide

## Overview

This document provides guidance for building and deploying 32-bit ARM and x86 native libraries for the Bitkit Android app's Paykit and Pubky SDK integrations.

## Current Status

As of the current build, the following native libraries are available:

### Complete ABIs
- ✅ `arm64-v8a` (64-bit ARM) - **Primary target**
- ✅ `x86_64` (64-bit x86) - **Emulator support**

### Incomplete ABIs
- ⚠️ `armeabi-v7a` (32-bit ARM) - Missing `libpubky_noise.so`
- ⚠️ `x86` (32-bit x86) - Missing `libpubky_noise.so`

## Building 32-bit Native Libraries

### Prerequisites

1. **Rust Toolchain** with 32-bit targets:
   ```bash
   rustup target add armv7-linux-androideabi
   rustup target add i686-linux-android
   ```

2. **Android NDK** (version r23c or later recommended)
   - Ensure `ANDROID_NDK_HOME` environment variable is set

3. **cargo-ndk** tool:
   ```bash
   cargo install cargo-ndk
   ```

### Build Steps for pubky-noise

Navigate to the `pubky-noise` project root:

```bash
cd /path/to/pubky-noise
```

#### Build for 32-bit ARM (armeabi-v7a)

```bash
cargo ndk \
  --target armv7-linux-androideabi \
  --android-platform 23 \
  --output-dir platforms/android/src/main/jniLibs \
  build --release
```

The resulting library will be placed in:
```
platforms/android/src/main/jniLibs/armeabi-v7a/libpubky_noise.so
```

#### Build for 32-bit x86

```bash
cargo ndk \
  --target i686-linux-android \
  --android-platform 23 \
  --output-dir platforms/android/src/main/jniLibs \
  build --release
```

The resulting library will be placed in:
```
platforms/android/src/main/jniLibs/x86/libpubky_noise.so
```

### Build Steps for Other FFI Libraries

If other FFI dependencies (`paykit-mobile`, `pubkycore`) also require 32-bit support, follow similar steps in their respective repositories.

## Integration into Bitkit

### Copy Libraries

After building, copy the 32-bit `.so` files to the appropriate ABI directories:

```bash
# From pubky-noise repository
cp platforms/android/src/main/jniLibs/armeabi-v7a/libpubky_noise.so \
   /path/to/bitkit-android/app/src/main/jniLibs/armeabi-v7a/

cp platforms/android/src/main/jniLibs/x86/libpubky_noise.so \
   /path/to/bitkit-android/app/src/main/jniLibs/x86/
```

### Verify ABI Splits

Ensure `build.gradle.kts` is configured to include all ABIs:

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }
    
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
        }
    }
}
```

### Test on 32-bit Devices

1. **Install on a 32-bit ARM device** (rare, mostly older devices):
   ```bash
   ./gradlew installDevDebug
   ```

2. **Test on 32-bit x86 emulator**:
   - Create an x86 (not x86_64) AVD in Android Studio
   - Run the app and verify Paykit/Pubky functionality

## Production Considerations

### APK Size

Including all ABIs increases APK size. Consider:

1. **App Bundle (Recommended)**:
   ```bash
   ./gradlew bundleDevRelease
   ```
   Google Play automatically delivers only the necessary ABI for each device.

2. **Per-ABI APKs** (if not using App Bundles):
   ```kotlin
   splits {
       abi {
           isEnable = true
           isUniversalApk = false
       }
   }
   ```

### Device Support

- **32-bit ARM**: Only needed for devices with < 2GB RAM or pre-2015 hardware
- **32-bit x86**: Mostly legacy tablets and emulators

**Recommendation**: For most production apps targeting modern devices (Android 6.0+), 64-bit ABIs (`arm64-v8a`, `x86_64`) are sufficient. Only include 32-bit ABIs if your analytics show significant traffic from 32-bit devices.

### Google Play Requirements

As of August 2019, Google Play requires 64-bit support for all apps. 32-bit ABIs are optional but can expand device compatibility.

## Troubleshooting

### "UnsatisfiedLinkError" on 32-bit Devices

**Cause**: Missing or incompatible 32-bit `.so` files.

**Solution**:
1. Verify `.so` files exist in `app/src/main/jniLibs/armeabi-v7a/` and `app/src/main/jniLibs/x86/`
2. Check file sizes match expected builds (not symlinks or corrupted files)
3. Run `./gradlew clean` and rebuild

### Build Fails with "linker not found"

**Cause**: Missing Android NDK toolchain for target.

**Solution**:
```bash
# Ensure NDK is installed
sdkmanager --install "ndk;25.2.9519653"

# Verify toolchain paths
ls $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/*/bin/armv7a-linux-androideabi*-clang
```

### Rust Cross-Compilation Errors

**Cause**: Missing system libraries or incompatible Rust version.

**Solution**:
1. Update Rust: `rustup update`
2. Install build dependencies:
   ```bash
   # Ubuntu/Debian
   sudo apt-get install gcc-multilib g++-multilib
   ```
3. Clean build artifacts: `cargo clean`

## Continuous Integration

### GitHub Actions Example

Add a workflow step to build 32-bit libraries:

```yaml
- name: Build 32-bit Native Libraries
  run: |
    cd pubky-noise
    cargo ndk --target armv7-linux-androideabi --android-platform 23 \
      --output-dir platforms/android/src/main/jniLibs build --release
    cargo ndk --target i686-linux-android --android-platform 23 \
      --output-dir platforms/android/src/main/jniLibs build --release
    
    cp -r platforms/android/src/main/jniLibs/* \
      $GITHUB_WORKSPACE/bitkit-android/app/src/main/jniLibs/
```

## References

- [Rust Android Toolchain](https://mozilla.github.io/firefox-browser-architecture/experiments/2017-09-21-rust-on-android.html)
- [cargo-ndk Documentation](https://github.com/bbqsrc/cargo-ndk)
- [Android ABI Management](https://developer.android.com/ndk/guides/abis)
- [Google Play 64-bit Requirement](https://developer.android.com/distribute/best-practices/develop/64-bit)

## Maintenance

**Last Updated**: 2025-12-22  
**Maintainer**: Bitkit Development Team  
**Next Review**: When upgrading Rust, NDK, or adding new FFI dependencies

