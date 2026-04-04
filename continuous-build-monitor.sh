#!/bin/bash

# Continuous Build Monitor
# Watches for build failures, analyzes errors, and suggests fixes

BUILD_LOG="build-output.log"
MAX_ATTEMPTS=5
ATTEMPT=1

echo "🔍 Continuous Build Monitoring System"
echo "=================================="

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    echo ""
    echo "=== Build Attempt $ATTEMPT ==="

    # Build and capture output
    export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew assembleDebug 2>&1 | tee $BUILD_LOG

    # Check if build succeeded
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ Build successful!"
        echo "All issues resolved."
        break
    fi

    # Analyze errors
    echo ""
    echo "🔍 Analyzing errors..."

    ERROR_COUNT=0

    if grep -q "Unresolved reference" $BUILD_LOG; then
        echo "   ⚠️  Variable scope issue detected"
        echo "   → Use /review-variable-scope for detailed guidance"
        echo "   → Common fix: Pass state as parameter + callback setter"
        ((ERROR_COUNT++))
    fi

    if grep -q "SettingsActivity" $BUILD_LOG && grep -q "crash\|exit\|Hilt" $BUILD_LOG; then
        echo "   ⚠️  Hilt/Dependency Injection issue detected"
        echo "   → Add @AndroidEntryPoint annotation"
        echo "   → Use by viewModels() to get ViewModel"
        ((ERROR_COUNT++))
    fi

    if grep -q "toColorString\|Color\|alpha" $BUILD_LOG; then
        echo "   ⚠️  Color format issue detected"
        echo "   → Check Color.kt - include alpha channel (#FFRRGGBB)"
        ((ERROR_COUNT++))
    fi

    if grep -q "clickable\|Modifier\|then" $BUILD_LOG; then
        echo "   ⚠️  Modifier order issue detected"
        echo "   → Move .clickable() to end of modifier chain"
        ((ERROR_COUNT++))
    fi

    if grep -q "Hilt\|KAPT" $BUILD_LOG && grep -q "UP-TO-DATE" $BUILD_LOG; then
        echo "   ⚠️  Build cache issue detected"
        echo "   → Running clean build..."
        ./gradlew clean
        ((ERROR_COUNT++))
    fi

    if [ $ERROR_COUNT -eq 0 ]; then
        echo "   ❓ Unknown error pattern detected"
        echo "   → Manual analysis required"
        echo ""
        echo "Last 20 lines of build log:"
        tail -20 $BUILD_LOG
        break
    fi

    ((ATTEMPT++))
done

if [ $ATTEMPT -gt $MAX_ATTEMPTS ]; then
    echo ""
    echo "❌ Max attempts ($MAX_ATTEMPTS) reached."
    echo "Manual intervention required."
    exit 1
fi

echo ""
echo "📋 Summary:"
echo "   Total attempts: $ATTEMPT"
echo "   Errors resolved: $((ATTEMPT - 1))"

exit 0
