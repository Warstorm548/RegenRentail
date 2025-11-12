# Build Fix Summary - Dependency Conflict Resolution

## Problem Identified

The build was failing due to **dependency version conflicts** between:
- Paper API 1.21.3
- WorldGuard 7.0.14
- WorldEdit 7.3.16

### Specific Conflicts
1. **Guava**: Paper wanted 32.1.2-jre, WorldGuard required strictly 33.3.1-jre, WorldEdit required strictly 32.1.3-jre
2. **Gson**: Paper wanted 2.10.1, WorldGuard required strictly 2.11.0, WorldEdit required strictly 2.10.1
3. **FastUtil**: Paper wanted 8.5.6, WorldGuard required strictly 8.5.15, WorldEdit required strictly 8.5.12
4. **Log4j**: WorldGuard required strictly 2.24.1, WorldEdit required strictly 2.22.1

## Solutions Applied

### 1. Updated `build.gradle.kts`

**Added Dependency Resolution Strategy:**
```kotlin
configurations.all {
    resolutionStrategy {
        // Force specific versions to resolve conflicts
        force(
            "com.google.guava:guava:33.3.1-jre",           // Use WorldGuard's version
            "com.google.code.gson:gson:2.11.0",            // Use WorldGuard's version
            "it.unimi.dsi:fastutil:8.5.15",                // Use WorldGuard's version
            "org.apache.logging.log4j:log4j-bom:2.24.1"    // Use WorldGuard's version
        )

        // Prefer modules from Paper when conflicts arise
        preferProjectModules()
    }
}
```

**Excluded Conflicting Dependencies:**
Added exclusions to WorldGuard and WorldEdit dependencies to prevent them from pulling in conflicting versions:
```kotlin
compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.14") {
    exclude(group = "com.google.guava")
    exclude(group = "com.google.code.gson")
    exclude(group = "it.unimi.dsi")
    exclude(group = "org.apache.logging.log4j")
}
```

### 2. Updated WorldEdit Version
- Changed from **7.3.6** to **7.3.16** (latest stable, compatible with WorldGuard 7.0.14)

### 3. Updated Gradle Version
- Changed from **8.5** to **8.11.1** (latest stable)

### 4. Updated Documentation
- **CLAUDE.md**: Updated technology stack versions
- **README.md**: Updated dependency versions in multiple locations

## What Was Changed

| File | Change |
|------|--------|
| `build.gradle.kts` | Added resolution strategy, exclusions, updated WorldEdit to 7.3.16 |
| `CLAUDE.md` | Updated Gradle to 8.11.1, WorldEdit to 7.3.16 |
| `README.md` | Updated WorldEdit to 7.3.16 in multiple locations |

## Next Steps to Build

### On Windows PowerShell:

1. **Navigate to project directory:**
   ```powershell
   cd C:\Users\jlwat\OpenCloud\Warstorm\RegenRentail
   ```

2. **Clean previous build artifacts:**
   ```powershell
   .\gradlew clean
   ```

3. **Build the project:**
   ```powershell
   .\gradlew build
   ```

4. **Find your JAR:**
   ```
   build\libs\RegionRental-1.0.0.jar
   ```

### Expected Output:
```
BUILD SUCCESSFUL in Xs
X actionable tasks: X executed
```

## Why This Fix Works

### The Strategy
1. **Force Resolution**: We tell Gradle to use specific versions that satisfy the strictest requirements (WorldGuard's versions are typically the most recent)

2. **Exclude Transitive Dependencies**: By excluding conflicting dependencies from WorldGuard and WorldEdit, we prevent them from pulling in incompatible versions

3. **Runtime Dependency**: Since we're using `compileOnly`, these dependencies are provided by the server at runtime. The server (Paper) will have the correct versions loaded

4. **Version Compatibility**: WorldEdit 7.3.16 is designed to work with WorldGuard 7.0.14, ensuring API compatibility

## Technical Explanation

### Why Conflicts Occurred
- **Transitive Dependencies**: When you add WorldGuard or WorldEdit, they bring their own dependencies (Guava, Gson, etc.)
- **Strict Constraints**: EngineHub (WorldGuard/WorldEdit) uses `strictly` version constraints to ensure they get specific versions
- **Paper Conflicts**: Paper also requires specific versions of these same libraries

### How the Fix Resolves It
1. **Force Strategy**: Overrides all version requirements with a single version choice
2. **Exclusions**: Prevents dependencies from bringing incompatible versions
3. **compileOnly**: At compile time, we only check API compatibility. At runtime, Paper provides the actual implementations

## Verification Checklist

After building, verify:
- [ ] Build completes successfully
- [ ] JAR file created in `build/libs/`
- [ ] No dependency resolution warnings
- [ ] File size is reasonable (~50-100 KB)

## Runtime Compatibility

The plugin will work correctly because:
1. **Paper provides** Guava, Gson, FastUtil, and Log4j at runtime
2. **WorldGuard/WorldEdit** are installed as separate plugins on the server
3. **API Compatibility**: We only compile against the APIs, not the implementations
4. **Version Requirements**: WorldGuard 7.0.14 and WorldEdit 7.3.16 are compatible with Paper 1.21.3

## Troubleshooting

If build still fails:

### Option 1: Check Gradle Wrapper
```powershell
.\gradlew --version
```
Should show Gradle 8.11.1

### Option 2: Clear Gradle Cache
```powershell
.\gradlew clean --refresh-dependencies
```

### Option 3: Check Java Version
```powershell
java -version
```
Should show Java 21 or higher

### Option 4: Verbose Build
```powershell
.\gradlew build --info
```
Shows detailed dependency resolution

## Additional Notes

- **No Code Changes Required**: The Java code remains unchanged; only build configuration was modified
- **Backward Compatible**: Plugin will work with existing configs and data files
- **Server Requirements**: Server must have WorldGuard 7.0.14+ and WorldEdit 7.3.16+ installed

## Success Criteria

Build is successful when you see:
```
BUILD SUCCESSFUL in Xs
X actionable tasks: X executed
```

And the file exists:
```
build\libs\RegionRental-1.0.0.jar
```

---

**Date Fixed**: 2025-01-11
**Gradle Version**: 8.11.1
**WorldEdit Version**: 7.3.16
**Status**: ✅ Ready to Build
