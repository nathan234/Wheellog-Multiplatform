#!/bin/bash

# Build script for WheelLogCore KMP framework
# This script is called by Xcode as a build phase

set -e

# Navigate to project root (parent of iosApp)
cd "$SRCROOT/.."

# Determine which architecture to build for
if [ "$PLATFORM_NAME" = "iphonesimulator" ]; then
    if [ "$NATIVE_ARCH" = "arm64" ] || [ "$ARCHS" = "arm64" ]; then
        GRADLE_TASK=":core:linkReleaseFrameworkIosSimulatorArm64"
        FRAMEWORK_DIR="iosSimulatorArm64"
    else
        GRADLE_TASK=":core:linkReleaseFrameworkIosX64"
        FRAMEWORK_DIR="iosX64"
    fi
else
    GRADLE_TASK=":core:linkReleaseFrameworkIosArm64"
    FRAMEWORK_DIR="iosArm64"
fi

echo "Building KMP framework with task: $GRADLE_TASK"

# Build the framework
./gradlew $GRADLE_TASK --no-daemon

echo "KMP framework built successfully: core/build/bin/$FRAMEWORK_DIR/releaseFramework/WheelLogCore.framework"
