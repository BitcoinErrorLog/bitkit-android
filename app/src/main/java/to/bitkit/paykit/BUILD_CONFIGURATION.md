# Bitkit Android - Paykit Integration Build Configuration

This guide explains how to configure the Bitkit Android Gradle project to integrate PaykitMobile.

## Prerequisites

- Android Studio Arctic Fox or later
- Android NDK (for native libraries)
- PaykitMobile jniLibs built (see `paykit-rs/paykit-mobile/BUILD.md`)
- Kotlin bindings generated

## Step 1: Configure NDK

Add to `local.properties`:
```properties
ndk.dir=/Users/YOU/Library/Android/sdk/ndk/25.2.9519653
```

## Step 2: Add jniLibs

1. Build the native libraries:
   ```bash
   cd paykit-rs/paykit-mobile
   ./build-android.sh
   ```

2. Copy jniLibs to project:
   ```bash
   cp -r paykit-rs/paykit-mobile/jniLibs/* \
     bitkit-android/app/src/main/jniLibs/
   ```

3. Verify structure:
   ```
   app/src/main/jniLibs/
   ├── arm64-v8a/
   │   └── libpaykit_mobile.so
   ├── armeabi-v7a/
   │   └── libpaykit_mobile.so
   ├── x86_64/
   │   └── libpaykit_mobile.so
   └── x86/
       └── libpaykit_mobile.so
   ```

## Step 3: Add Kotlin Bindings

1. Copy generated Kotlin bindings:
   ```bash
   cp paykit-rs/paykit-mobile/kotlin/generated/com/paykit/mobile/paykit_mobile.kt \
     bitkit-android/app/src/main/java/com/paykit/mobile/
   ```

2. Verify import in PaykitManager:
   ```kotlin
   import com.paykit.mobile.*
   ```

## Step 4: Configure build.gradle.kts

Add NDK configuration:

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}
```

## Step 5: Configure ProGuard (if enabled)

Add to `proguard-rules.pro`:

```proguard
# Keep PaykitMobile FFI classes
-keep class com.paykit.mobile.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
```

## Step 6: Verify Integration

1. Sync Gradle files
2. Build project
3. Run on device/emulator
4. Verify PaykitManager initializes

## Troubleshooting

### Library Not Found
- Check jniLibs directory structure
- Verify ABI filters match available architectures

### UnsatisfiedLinkError
- Verify library name is correct: `libpaykit_mobile.so`
- Check that library exists for target architecture

### Import Errors
- Verify Kotlin bindings are in correct package
- Check package name: `com.paykit.mobile`

## Verification Checklist

- [ ] NDK configured
- [ ] jniLibs copied to project
- [ ] Kotlin bindings added
- [ ] build.gradle.kts updated
- [ ] ProGuard rules added (if using)
- [ ] Project builds successfully
- [ ] PaykitManager initializes without errors
- [ ] Tests pass
