#!/bin/bash

# RegionRental Build Script

echo "========================================="
echo "Building RegionRental Plugin v1.0.0"
echo "========================================="

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build the plugin
echo "Building plugin..."
./gradlew build

# Check if build was successful
if [ -f "build/libs/RegionRental-1.0.0.jar" ]; then
    echo "========================================="
    echo "Build SUCCESS!"
    echo "Plugin JAR: build/libs/RegionRental-1.0.0.jar"
    echo "========================================="
    ls -lh build/libs/RegionRental-1.0.0.jar
else
    echo "========================================="
    echo "Build FAILED!"
    echo "Check the error messages above."
    echo "========================================="
    exit 1
fi
