#!/bin/bash
# Convenient shell wrapper for mock SCB/KTB LINE notifications
# Usage: ./mock_notification.sh [AMOUNT] [BANK] [ACCOUNT] [OPTIONS]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="$SCRIPT_DIR/mock_scb_line_notification.py"

# Check if Python script exists
if [ ! -f "$PYTHON_SCRIPT" ]; then
    echo "ERROR: mock_scb_line_notification.py not found in $SCRIPT_DIR"
    exit 1
fi

# Parse arguments
AMOUNT="${1:-}"
BANK="${2:-SCB}"
ACCOUNT="${3:-}"
SHIFT_COUNT=3

# Shift positional arguments if provided
shift $SHIFT_COUNT 2>/dev/null || true

# Show help
show_help() {
    cat << 'EOF'
Mock SCB/KTB LINE Bank Notifications

USAGE:
    ./mock_notification.sh [AMOUNT] [BANK] [ACCOUNT] [OPTIONS]

ARGUMENTS:
    AMOUNT      Payment amount in THB (required) - e.g., 1000, 50000.50
    BANK        Bank name - SCB or KTB (default: SCB)
    ACCOUNT     Account number - e.g., X-7985 for SCB, XX7157 for KTB

OPTIONS:
    -d, --device DEVICE_ID    ADB device ID (auto-detect by default)
    -v, --verbose             Verbose output
    -q, --quiet               Minimal output
    --dry-run                 Show what would be sent without sending
    -h, --help                Show this help message

EXAMPLES:
    # Send 1,000 THB SCB payment
    ./mock_notification.sh 1000

    # Send 50,000 THB to specific account
    ./mock_notification.sh 50000 SCB X-7985

    # Send KTB payment with verbose output
    ./mock_notification.sh 5000 KTB XX7157 --verbose

    # Dry run to preview notification
    ./mock_notification.sh 1000 SCB X-7985 --dry-run

    # Send to specific device
    ./mock_notification.sh 1000 SCB X-7985 -d emulator-5554

BATCH OPERATIONS:
    # Send multiple amounts
    for amt in 100 500 1000 5000; do
        ./mock_notification.sh $amt SCB X-7985 --quiet
        sleep 2
    done

    # Use batch script for more control
    ./batch_mock_notifications.py --count 10 --bank SCB

EOF
}

# Check if help requested
if [ "$AMOUNT" = "-h" ] || [ "$AMOUNT" = "--help" ]; then
    show_help
    exit 0
fi

# Validate amount provided
if [ -z "$AMOUNT" ]; then
    echo "ERROR: Missing amount argument"
    echo ""
    show_help
    exit 1
fi

# Set default account based on bank
if [ -z "$ACCOUNT" ]; then
    if [ "$BANK" = "KTB" ]; then
        ACCOUNT="XX7157"
    else
        ACCOUNT="X-7985"
    fi
fi

# Build Python command
PYTHON_CMD=(
    python3 "$PYTHON_SCRIPT"
    --amount "$AMOUNT"
    --bank "$BANK"
    --account "$ACCOUNT"
    "$@"
)

# Execute
exec "${PYTHON_CMD[@]}"

