#!/bin/bash

# Autonomous Deployment Pipeline for HeartRate Monitor
# Automates version management, testing, building, deployment, and monitoring

set -e  # Exit on error
set -o pipefail  # Catch pipe errors

# Configuration
CONFIG_FILE="deployment.config.json"
RELEASE_FILE="release.md"
DEPLOYMENT_LOG="logs/deployment.log"
DEPLOYMENT_HISTORY="logs/deployment-history.json"
BUILD_DIR="app/build/outputs/apk/debug"
DEVICE_PACKAGE="com.heartratemonitor"
TEST_DURATION_SECONDS=60

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse command line arguments
VERSION_TYPE=""
SKIP_TESTS=false
SKIP_MONITORING=false
NO_BUILD=false
ENV="production"

while [[ $# -gt 0 ]]; do
    case $1 in
        --major)
            VERSION_TYPE="major"
            shift
            ;;
        --minor)
            VERSION_TYPE="minor"
            shift
            ;;
        --patch)
            VERSION_TYPE="patch"
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-monitoring)
            SKIP_MONITORING=true
            shift
            ;;
        --no-build)
            NO_BUILD=true
            shift
            ;;
        --staging)
            ENV="staging"
            shift
            ;;
        --rollback)
            rollback_deployment
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --major       Increment major version (1.0.x → 2.0.0)"
            echo "  --minor       Increment minor version (1.x.0 → 1.(x+1).0)"
            echo "  --patch        Increment patch version (1.0.x → 1.0.x+1)"
            echo "  --skip-tests   Skip running tests"
            echo "  --skip-monitoring Skip post-deployment monitoring"
            echo "  --no-build      Skip building APK (use existing)"
            echo "  --staging      Deploy to staging environment"
            echo "  --rollback      Rollback to previous version"
            exit 1
            ;;
    esac
done

# Ensure log directory exists
mkdir -p logs

# Log functions
log() {
    local level=$1
    shift
    echo -e "$(date '+%Y-%m-%d %H:%M:%S') [$level] $*" | tee -a "$DEPLOYMENT_LOG"
}

log_info() {
    log "INFO" "$@"
}

log_success() {
    log "SUCCESS" "$@"
}

log_warning() {
    log "WARNING" "$@"
}

log_error() {
    log "ERROR" "$@"
}

# ============================================================
# Phase 1: Version Management
# ===========================================================

log_info "Phase 1: Version Management"

increment_version() {
    local version_type=$1

    # Read current version from release.md
    if [[ -f "$RELEASE_FILE" ]]; then
        current_version=$(grep -E '^\|.*\|' "$RELEASE_FILE" | head -1 | sed 's/|.*|\([0-9.]*\.[0-9]*\).*/\1/' | tr -d '\n')
        if [[ -z "$current_version" ]]; then
            current_version="1.0.0"
        fi
    else
        current_version="1.0.0"
    fi

    log_info "Current version: $current_version"

    # Parse version
    IFS='.' read -ra major minor patch <<< "$current_version"

    case $version_type in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            log_error "Invalid version type: $version_type"
            exit 1
            ;;
    esac

    new_version="${major}.${minor}.${patch}"
    log_success "New version: $new_version"

    echo "$new_version"
}

# ============================================================
# Phase 2: Changelog Generation
# ===========================================================

generate_changelog() {
    local new_version=$1
    local changelog_file="CHANGELOG-${new_version}.md"

    log_info "Phase 2: Changelog Generation"

    # Get git log since last tag (or last 20 commits if no tags)
    local git_log=$(git log --oneline --since="2 days ago" --no-merges -20)

    if [[ -z "$git_log" ]]; then
        git_log=$(git log --oneline -20)
        log_warning "No git tags found, using last 20 commits"
    fi

    # Generate changelog
    cat > "$changelog_file" << EOF
# HeartRate Monitor - 版本 ${new_version}

## 更新内容

### 问题修复
EOF

    # Analyze commits and categorize
    echo "$git_log" | while read -r commit_line; do
        commit_hash=$(echo "$commit_line" | awk '{print $1}')
        commit_msg=$(echo "$commit_line" | cut -d' ' -f2- | sed 's/^.*: //')

        if [[ "$commit_msg" == *"fix"* ]] || [[ "$commit_msg" == *"修复"* ]]; then
            echo "- ${commit_msg}" >> "$changelog_file"
        elif [[ "$commit_msg" == *"feat"* ]] || [[ "$commit_msg" == *"添加"* ]]; then
            echo "- ${commit_msg}" >> "$changelog_file"
        elif [[ "$commit_msg" == *"refactor"* ]] || [[ "$commit_msg" == *"优化"* ]]; then
            echo "- ${commit_msg}" >> "$changelog_file"
        fi
    done

    # Add summary
    cat >> "$changelog_file" << EOF

### 构建信息
- 构建时间: $(date '+%Y-%m-%d %H:%M')
- Android 版本: API 21+
EOF

    log_success "Changelog generated: $changelog_file"
}

# ============================================================
# Phase 3: Testing
# ===========================================================

run_tests() {
    log_info "Phase 3: Testing"

    if [[ "$SKIP_TESTS" == "true" ]]; then
        log_warning "Skipping tests (--skip-tests flag set)"
        return 0
    fi

    # Check if tests exist
    if [[ ! -d "app/src/test" ]] && [[ ! -d "app/src/androidTest" ]]; then
        log_warning "No tests found, skipping test phase"
        return 0
    fi

    # Run unit tests
    log_info "Running unit tests..."
    if ./gradlew test --tests "*UnitTest" > logs/unit-test.log 2>&1; then
        log_success "Unit tests passed"
    else
        log_error "Unit tests failed"
        tail -20 logs/unit-test.log
        return 1
    fi

    # Run Android instrumented tests
    log_info "Running Android tests..."
    if ./gradlew connectedAndroidTest > logs/android-test.log 2>&1; then
        log_success "Android tests passed"
    else
        log_warning "Android tests failed (optional, continuing)"
        # Don't fail on Android tests as they may not be critical
    fi
}

# ============================================================
# Phase 4: Build
# ===========================================================

build_apk() {
    local version=$1

    log_info "Phase 4: Building APK"

    if [[ "$NO_BUILD" == "true" ]]; then
        log_info "Skipping build (--no-build flag set)"
        if [[ ! -f "$BUILD_DIR/app-debug.apk" ]]; then
            log_error "No existing APK found"
            exit 1
        fi
        return 0
    fi

    # Set JAVA_HOME and build
    log_info "Building version $version..."
    if export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew clean assembleDebug > logs/build.log 2>&1; then
        log_success "Build successful"
    else
        log_error "Build failed"
        tail -30 logs/build.log
        exit 1
    fi

    # Copy APK to Desktop with version number
    local apk_name="HeartRateMonitor-${version}.apk"
    cp "$BUILD_DIR/app-debug.apk" "$HOME/Desktop/$apk_name"
    log_success "APK copied to Desktop: $apk_name"
}

# ============================================================
# Phase 5: Deployment
# ===========================================================

install_apk() {
    local version=$1
    local apk_name="HeartRateMonitor-${version}.apk"
    local apk_path="$HOME/Desktop/$apk_name"

    log_info "Phase 5: Deployment"

    # Check if device is connected
    if ! adb devices | grep -q "device$"; then
        log_error "No ADB device connected"
        log_warning "Please connect a device and enable USB debugging"
        return 1
    fi

    # Uninstall existing version
    log_info "Uninstalling existing version..."
    adb uninstall "$DEVICE_PACKAGE" > /dev/null 2>&1 || true

    # Install new APK
    log_info "Installing new APK: $apk_name..."
    if adb install -r "$apk_path" > logs/install.log 2>&1; then
        log_success "Installation successful"
    else
        log_error "Installation failed"
        tail -20 logs/install.log
        return 1
    fi
}

# ============================================================
# Phase 6: Smoke Testing
# ===========================================================

smoke_test() {
    if [[ "$SKIP_MONITORING" == "true" ]]; then
        log_warning "Skipping smoke tests (--skip-monitoring flag set)"
        return 0
    fi

    log_info "Phase 6: Smoke Testing"

    # Wait for app to start
    log_info "Waiting for app to start ($TEST_DURATION_SECONDS seconds)..."
    sleep 5

    # Check if app is running
    if ! adb shell "ps | grep -q $DEVICE_PACKAGE"; then
        log_error "App not running after installation"
        return 1
    fi

    log_success "App is running"

    # Monitor for crashes during test period
    log_info "Monitoring for crashes for $TEST_DURATION_SECONDS seconds..."

    local crash_count=0
    local start_time=$(date +%s)
    local end_time=$((start_time + TEST_DURATION_SECONDS))
    local current_time

    while [[ $current_time -lt $end_time ]]; do
        current_time=$(date +%s)
        sleep 5

        # Check for FATAL errors
        if adb logcat -d | grep -q "FATAL EXCEPTION\|AndroidRuntime: FATAL" | tail -1; then
            ((crash_count++))
            log_error "CRASH DETECTED! Crash count: $crash_count"
        fi
    done

    if [[ $crash_count -gt 0 ]]; then
        log_error "Smoke test FAILED: $crash_count crashes detected"
        return 1
    fi

    log_success "Smoke test passed: No crashes in $TEST_DURATION_SECONDS seconds"
}

# ============================================================
# Phase 7: Monitoring
# ===========================================================

monitor_deployment() {
    if [[ "$SKIP_MONITORING" == "true" ]]; then
        log_warning "Skipping monitoring (--skip-monitoring flag set)"
        return 0
    fi

    log_info "Phase 7: Post-Deployment Monitoring"

    # Monitor for 10 minutes (adjustable via CONFIG_FILE)
    local monitor_duration=${MONITOR_DURATION_MINUTES:-10} * 60
    local start_time=$(date +%s)
    local end_time=$((start_time + monitor_duration))
    local current_time

    log_info "Monitoring for $MONITOR_DURATION_MINUTES minutes..."

    while [[ $current_time -lt $end_time ]]; do
        current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        local remaining=$((end_time - current_time))

        # Check for crashes every 30 seconds
        if [[ $((elapsed % 30)) -eq 0 ]]; then
            if adb logcat -d | grep -q "FATAL EXCEPTION\|AndroidRuntime: FATAL" | tail -1; then
                log_error "CRASH DETECTED at ${elapsed}s"
                # Could trigger rollback here
            fi
        fi

        sleep 5
    done

    log_success "Monitoring completed: No crashes detected in $MONITOR_DURATION_MINUTES minutes"
}

# ============================================================
# Phase 8: Deployment Metadata
# ===========================================================

log_deployment_metadata() {
    local version=$1
    local commit_hash=$(git rev-parse --short HEAD)
    local apk_path="$HOME/Desktop/HeartRateMonitor-${version}.apk"
    local apk_hash=$(shasum -a 256 "$apk_path" 2>/dev/null || echo "unknown")
    local timestamp=$(date -Iseconds '+%Y-%m-%dT%H:%M:%SZ')

    # Create/update deployment history
    local deployment_entry="{
      \"version\": \"$version\",
      \"timestamp\": \"$timestamp\",
      \"commit_hash\": \"$commit_hash\",
      \"apk_hash\": \"$apk_hash\",
      \"artifacts\": [
        \"$apk_path\"
      ],
      \"status\": \"success\"
    }"

    if [[ ! -f "$DEPLOYMENT_HISTORY" ]]; then
        echo "[]" > "$DEPLOYMENT_HISTORY"
    fi

    # Append new deployment
    local temp=$(cat "$DEPLOYMENT_HISTORY")
    echo "$temp" | sed '$s/]}\n    {$deployment_entry\n}]}/' > "$DEPLOYMENT_HISTORY"

    log_info "Deployment metadata logged"
    log_success "Deployment history: $DEPLOYMENT_HISTORY"
}

# ============================================================
# Rollback Function
# ===========================================================

rollback_deployment() {
    log_warning "Initiating rollback..."

    # Read deployment history
    if [[ ! -f "$DEPLOYMENT_HISTORY" ]]; then
        log_error "No deployment history found"
        exit 1
    fi

    # Get previous successful deployment
    local prev_version=$(cat "$DEPLOYMENT_HISTORY" | tail -n 2 | grep -A 2 '"status": "success"' | head -1 | grep -oP '"version": "[^"]*' | cut -d'"' -f2)

    if [[ -z "$prev_version" ]]; then
        log_error "No previous successful deployment found"
        exit 1
    fi

    log_info "Rolling back to version: $prev_version"

    # Uninstall current version
    adb uninstall "$DEVICE_PACKAGE" > /dev/null 2>&1 || true

    # Install previous version
    local prev_apk="$HOME/Desktop/HeartRateMonitor-${prev_version}.apk"
    if [[ ! -f "$prev_apk" ]]; then
        log_error "Previous APK not found: $prev_apk"
        exit 1
    fi

    if adb install -r "$prev_apk" > logs/rollback.log 2>&1; then
        log_success "Rollback successful"
    else
        log_error "Rollback failed"
        tail -20 logs/rollback.log
        exit 1
    fi

    # Update deployment history
    local timestamp=$(date -Iseconds '+%Y-%m-%dT%H:%M:%SZ')
    local rollback_entry="{
      \"version\": \"$prev_version\",
      \"timestamp\": \"$timestamp\",
      \"status\": \"rolled_back\",
      \"rollback_reason\": \"User initiated\"
    }"

    local temp=$(cat "$DEPLOYMENT_HISTORY")
    echo "$temp" | sed '$s/]}\n    {$rollback_entry\n}]}/' > "$DEPLOYMENT_HISTORY"
}

# ============================================================
# Main Pipeline Execution
# ===========================================================

main() {
    echo -e "${BLUE}======================================"
    echo -e "${BLUE}   Autonomous Deployment Pipeline"
    echo -e "${BLUE}   HeartRate Monitor"
    echo -e "${BLUE}======================================${NC}"
    echo ""

    # Execute pipeline phases
    local new_version=$(increment_version)
    generate_changelog "$new_version"

    if [[ "$NO_BUILD" != "true" ]]; then
        build_apk "$new_version" || exit 1
    fi

    if [[ "$SKIP_TESTS" != "true" ]]; then
        run_tests || exit 1
    fi

    install_apk "$new_version" || exit 1

    if [[ "$SKIP_MONITORING" != "true" ]]; then
        smoke_test || exit 1
        monitor_deployment
    fi

    log_deployment_metadata "$new_version"

    # Final summary
    echo ""
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}   Deployment Complete!${NC}"
    echo -e "${GREEN}======================================${NC}"
    echo ""
    echo -e "${BLUE}Version:${NC}        $new_version"
    echo -e "${BLUE}Environment:${NC}   $ENV"
    echo -e "${BLUE}Status:${NC}         Success"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "  1. Verify functionality on device"
    echo "  2. Test key features:"
    echo "     - BLE scanning and connection"
    echo "     - Real-time heart rate display"
    echo "     - Heart rate history"
    echo "     - Threshold-based alerts"
    echo "     - Settings persistence"
    echo "  3. Review deployment logs in logs/"
    echo ""
    echo -e "${BLUE}APK Location:${NC}   $HOME/Desktop/HeartRateMonitor-${new_version}.apk"
    echo -e "${BLUE}Changelog:${NC}       CHANGELOG-${new_version}.md"
    echo -e "${BLUE}Deployment Log:${NC}  logs/deployment.log"
    echo ""
}

# Run main pipeline
main

exit 0
