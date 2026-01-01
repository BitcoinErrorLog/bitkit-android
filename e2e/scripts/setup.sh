#!/bin/bash
# E2E Test Setup Script
# Prepares the Android emulator for E2E testing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CREDENTIALS_DIR="${CREDENTIALS_DIR:-/Users/john/vibes-dev/credentials}"

echo "=== E2E Test Setup ==="
echo "Project root: $PROJECT_ROOT"
echo "Credentials: $CREDENTIALS_DIR"

# Check for adb
if ! command -v adb &> /dev/null; then
    echo "ERROR: adb not found. Please install Android SDK platform-tools."
    exit 1
fi

# Check for running emulator
if ! adb devices | grep -q "emulator"; then
    echo "ERROR: No Android emulator running. Please start an emulator first."
    exit 1
fi

echo ""
echo "=== Pushing test identity files to emulator ==="

# Push primary identity for Android tests
PRIMARY_PKARR="$CREDENTIALS_DIR/android-ai-tester-backup-2026-01-01_12-18-17.pkarr"
if [ -f "$PRIMARY_PKARR" ]; then
    adb push "$PRIMARY_PKARR" /sdcard/Download/test-primary.pkarr
    echo "✓ Pushed primary identity: test-primary.pkarr"
else
    echo "ERROR: Primary identity file not found: $PRIMARY_PKARR"
    exit 1
fi

# Push secondary identity for discovery tests
SECONDARY_PKARR="$CREDENTIALS_DIR/ios-ai-tester-backup-2026-01-01_12-16-19.pkarr"
if [ -f "$SECONDARY_PKARR" ]; then
    adb push "$SECONDARY_PKARR" /sdcard/Download/test-secondary.pkarr
    echo "✓ Pushed secondary identity: test-secondary.pkarr"
else
    echo "ERROR: Secondary identity file not found: $SECONDARY_PKARR"
    exit 1
fi

echo ""
echo "=== Verifying APKs are installed ==="

# Check Bitkit is installed
if adb shell pm list packages | grep -q "to.bitkit"; then
    echo "✓ Bitkit installed"
else
    echo "WARNING: Bitkit not installed. Install with: ./gradlew installDevDebug"
fi

# Check Ring is installed
if adb shell pm list packages | grep -q "to.pubky.ring"; then
    echo "✓ Pubky Ring installed"
else
    echo "WARNING: Pubky Ring not installed. Please install the Ring APK."
fi

echo ""
echo "=== Setup complete ==="
echo ""
echo "Next steps:"
echo "1. Run identity import: maestro test e2e/flows/01-import-identity.yaml"
echo "2. Run full test suite: maestro test e2e/flows/"

