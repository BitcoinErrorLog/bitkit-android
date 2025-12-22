#!/bin/bash
# Test Pubky Ring Integration - Android
#
# This script runs the Pubky Ring integration tests.
# Usage: ./scripts/test-pubky-ring-integration.sh [--unit-only] [--deeplink-only] [--flow-only]
#
# Prerequisites:
# - Android SDK installed
# - Emulator running (for instrumented tests)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RUN_UNIT=true
RUN_DEEPLINK=true
RUN_UIFLOW=true

# Parse arguments
for arg in "$@"; do
    case $arg in
        --unit-only)
            RUN_DEEPLINK=false
            RUN_UIFLOW=false
            ;;
        --deeplink-only)
            RUN_UNIT=false
            RUN_UIFLOW=false
            ;;
        --flow-only)
            RUN_UNIT=false
            RUN_DEEPLINK=false
            ;;
        --instrumented-only)
            RUN_UNIT=false
            ;;
    esac
done

echo "=== Pubky Ring Integration Tests (Android) ==="
echo ""

cd "$PROJECT_DIR"

# Check if emulator is running for instrumented tests
check_emulator() {
    if ! adb devices | grep -q "device$"; then
        echo "⚠️  Warning: No device/emulator detected."
        echo "   Please ensure an emulator is running for instrumented tests."
        echo ""
        return 1
    fi
    return 0
}

if [ "$RUN_UNIT" = true ]; then
    echo "1. Running PubkyRingBridge unit tests..."
    ./gradlew testDevDebugUnitTest \
        --tests "to.bitkit.paykit.services.PubkyRingBridgeTest" \
        2>&1 | tail -40
    echo ""
fi

if [ "$RUN_DEEPLINK" = true ]; then
    if check_emulator; then
        echo "2. Running deep link instrumented tests..."
        ./gradlew connectedDevDebugAndroidTest \
            --tests "to.bitkit.paykit.PubkyRingDeepLinkTest" \
            2>&1 | tail -40
        echo ""
    fi
fi

if [ "$RUN_UIFLOW" = true ]; then
    if check_emulator; then
        echo "3. Running UI flow tests (taps on actual UI)..."
        ./gradlew connectedDevDebugAndroidTest \
            --tests "to.bitkit.paykit.PubkyRingUIFlowTest" \
            2>&1 | tail -60
        echo ""
    fi
fi

echo "=== Tests Complete ==="
echo ""
echo "To run specific tests:"
echo "  --unit-only      Run only unit tests (fast, no emulator needed)"
echo "  --deeplink-only  Run only deep link tests"
echo "  --flow-only      Run only UI flow tests (actual taps on UI)"

