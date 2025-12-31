# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# =============================================================================
# Paykit/Pubky FFI ProGuard Rules
# =============================================================================

# Paykit Mobile FFI (UniFFI generated)
-keep class uniffi.paykit_mobile.** { *; }
-keep interface uniffi.paykit_mobile.** { *; }

# Pubky Core FFI (UniFFI generated)
-keep class uniffi.pubkycore.** { *; }
-keep interface uniffi.pubkycore.** { *; }

# Pubky Noise FFI (UniFFI generated)
-keep class com.pubky.noise.** { *; }
-keep interface com.pubky.noise.** { *; }

# UniFFI callback interfaces - keep all implementations
-keep class * implements uniffi.paykit_mobile.** { *; }
-keep class * implements uniffi.pubkycore.** { *; }
-keep class * implements com.pubky.noise.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep FFI data classes for serialization
-keepclassmembers class uniffi.paykit_mobile.** { *; }
-keepclassmembers class uniffi.pubkycore.** { *; }
-keepclassmembers class com.pubky.noise.** { *; }

# Keep JNA classes used by UniFFI
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# Keep Kotlin serialization for Paykit models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class to.bitkit.paykit.**$$serializer { *; }
-keepclassmembers class to.bitkit.paykit.** {
    *** Companion;
}
-keepclasseswithmembers class to.bitkit.paykit.** {
    kotlinx.serialization.KSerializer serializer(...);
}