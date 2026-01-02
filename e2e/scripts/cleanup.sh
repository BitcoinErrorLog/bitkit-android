#!/bin/bash
# E2E Test Cleanup Script
# Resets test state after E2E tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse arguments
FULL_RESET=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --full)
            FULL_RESET=true
            shift
            ;;
        *)
            shift
            ;;
    esac
done

echo "=== E2E Test Cleanup ==="
echo "Full reset: $FULL_RESET"

# Check for running emulator
if ! adb devices | grep -q "emulator"; then
    echo "WARNING: No Android emulator running. Skipping device cleanup."
    exit 0
fi

echo ""
echo "Cleaning up emulator..."

# Remove test identity files from Download folder
echo "Removing test identity files..."
adb shell rm -f /sdcard/Download/test-primary.pkarr || true
adb shell rm -f /sdcard/Download/test-secondary.pkarr || true
echo "  Done"

if [ "$FULL_RESET" = true ]; then
    echo ""
    echo "Performing full app data reset..."
    
    # Clear Bitkit app data
    echo "Clearing Bitkit app data..."
    adb shell pm clear to.bitkit 2>/dev/null || echo "  Bitkit not installed"
    
    # Clear Ring app data
    echo "Clearing Pubky Ring app data..."
    adb shell pm clear to.pubky.ring 2>/dev/null || echo "  Pubky Ring not installed"
    
    echo "  Done"
fi

echo ""
echo "=== Cleanup complete ==="
echo ""
echo "Notes:"
echo "  - Profile and follows changes on the homeserver persist."
echo "  - Use --full flag to also clear app data (local caches, sessions)."
echo "  - To fully reset homeserver state, use the apps manually."
echo ""
echo "Usage:"
echo "  $0           # Remove test files only"
echo "  $0 --full    # Remove test files and clear app data"

