#!/bin/bash
# E2E Test Runner Script
# Executes Maestro test flows in order

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLOWS_DIR="$(cd "$SCRIPT_DIR/../flows" && pwd)"

echo "=== E2E Test Runner ==="
echo "Flows directory: $FLOWS_DIR"

# Check for Maestro
if ! command -v maestro &> /dev/null; then
    echo "ERROR: Maestro not found. Install with: curl -Ls https://get.maestro.mobile.dev | bash"
    exit 1
fi

# Check for running emulator
if ! adb devices | grep -q "emulator"; then
    echo "ERROR: No Android emulator running. Please start an emulator first."
    exit 1
fi

# Run setup first
echo ""
echo "=== Running setup ==="
"$SCRIPT_DIR/setup.sh"

echo ""
echo "=== Running Maestro tests ==="

# Track results
PASSED=0
FAILED=0
SKIPPED=0

run_flow() {
    local flow="$1"
    local name=$(basename "$flow" .yaml)
    
    echo ""
    echo "--- Running: $name ---"
    
    if maestro test "$flow"; then
        echo "✓ PASSED: $name"
        ((PASSED++))
    else
        echo "✗ FAILED: $name"
        ((FAILED++))
        # Don't exit on failure, continue with other tests
    fi
}

# Run flows in order
for flow in "$FLOWS_DIR"/*.yaml; do
    if [ -f "$flow" ]; then
        run_flow "$flow"
    fi
done

echo ""
echo "=== Test Results ==="
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo ""

if [ $FAILED -gt 0 ]; then
    echo "Some tests failed!"
    exit 1
fi

echo "All tests passed!"

