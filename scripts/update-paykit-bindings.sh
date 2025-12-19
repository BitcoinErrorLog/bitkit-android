#!/bin/bash
#
# Update Paykit Mobile bindings from paykit-rs-master
#
# This script rebuilds the paykit-mobile library and copies the bindings
# to bitkit-android to fix UniFFI contract version mismatches.
#
# Usage:
#   ./scripts/update-paykit-bindings.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BITKIT_ANDROID="$SCRIPT_DIR/.."
PAYKIT_RS="/Users/john/Library/Mobile Documents/com~apple~CloudDocs/vibes/paykit-rs-master"

echo "========================================"
echo "Updating Paykit Mobile Bindings"
echo "========================================"
echo ""

# Check if paykit-rs exists
if [ ! -d "$PAYKIT_RS" ]; then
    echo "Error: paykit-rs-master not found at $PAYKIT_RS"
    exit 1
fi

# Navigate to paykit-rs
cd "$PAYKIT_RS"

# Step 1: Build the library
echo "Step 1: Building paykit-mobile..."
cargo build --release -p paykit-mobile

# Step 2: Generate bindings
echo ""
echo "Step 2: Generating Kotlin bindings..."

LIB_PATH="target/release/libpaykit_mobile.dylib"
if [ ! -f "$LIB_PATH" ]; then
    echo "Error: Library not found at $LIB_PATH"
    exit 1
fi

# Check for uniffi-bindgen
if command -v uniffi-bindgen &> /dev/null; then
    echo "Using uniffi-bindgen to generate Kotlin bindings..."
    mkdir -p paykit-mobile/kotlin/generated/uniffi/paykit_mobile
    uniffi-bindgen generate --library "$LIB_PATH" -l kotlin -o paykit-mobile/kotlin/generated
elif cargo run --bin generate-bindings --features bindgen-cli -- --library "$LIB_PATH" -l kotlin -o paykit-mobile/kotlin/generated 2>/dev/null; then
    echo "Using built-in generator..."
else
    echo "Warning: uniffi-bindgen not available. Using existing generated bindings."
fi

# Step 3: Copy bindings to bitkit-android
echo ""
echo "Step 3: Copying bindings to bitkit-android..."

BINDINGS_SRC="$PAYKIT_RS/paykit-mobile/kotlin/generated"
BINDINGS_DST="$BITKIT_ANDROID/app/src/main/java"

if [ -d "$BINDINGS_SRC" ]; then
    # Find and copy the generated Kotlin file
    find "$BINDINGS_SRC" -name "*.kt" -exec cp {} "$BINDINGS_DST/uniffi/paykit_mobile/" \;
    echo "Copied Kotlin bindings"
else
    echo "Warning: No generated bindings found at $BINDINGS_SRC"
fi

# Step 4: Copy library for local testing (optional)
echo ""
echo "Step 4: Checking for Android libraries..."

ANDROID_TARGETS=(
    "aarch64-linux-android:arm64-v8a"
    "x86_64-linux-android:x86_64"
)

JNILIBS_DST="$BITKIT_ANDROID/app/src/main/jniLibs"

for target_mapping in "${ANDROID_TARGETS[@]}"; do
    IFS=':' read -r target abi <<< "$target_mapping"
    LIB_PATH="$PAYKIT_RS/target/$target/release/libpaykit_mobile.so"
    if [ -f "$LIB_PATH" ]; then
        mkdir -p "$JNILIBS_DST/$abi"
        cp "$LIB_PATH" "$JNILIBS_DST/$abi/"
        echo "Copied $target library to jniLibs/$abi"
    fi
done

echo ""
echo "========================================"
echo "Done!"
echo "========================================"
echo ""
echo "If you still see UniFFI version mismatch errors:"
echo "1. Run: cd $PAYKIT_RS && ./paykit-mobile/build-android.sh"
echo "2. Run this script again"
echo "3. Clean and rebuild: ./gradlew clean assembleDevDebug"
echo ""

