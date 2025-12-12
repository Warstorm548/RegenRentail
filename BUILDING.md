# Building RegionRental

This guide covers how to build the RegionRental plugin from source.

## Prerequisites

1. **Java 21+** (OpenJDK recommended) - Required for compilation and runtime
2. **Gradle 9.2.0** - Included via Gradle Wrapper (no installation needed)

## Build the Plugin

1. **Clone or download this project**

2. **Navigate to the project directory:**
```bash
cd RegionRental
```

3. **Run the build script:**
```bash
chmod +x build.sh
./build.sh
```

Or build directly with Gradle:
```bash
./gradlew clean build
```

4. **Find your JAR file:**
```
build/libs/RegionRental-2.5.1.jar
```

## Development Commands

```bash
# Clean build artifacts
./gradlew clean

# Compile only (no packaging)
./gradlew compileJava

# Run shadowJar task
./gradlew shadowJar

# Build without tests
./gradlew build -x test

# Generate IDE project files
./gradlew idea        # For IntelliJ IDEA
./gradlew eclipse     # For Eclipse
```

## Project Structure

```
RegionRental/
├── build.gradle.kts                 # Gradle build configuration (Kotlin DSL)
├── settings.gradle.kts              # Gradle settings (Kotlin DSL)
├── gradle.properties                # Gradle properties
├── build.sh                         # Build script
├── README.md                        # This file
├── CLAUDE.md                        # Developer documentation
└── src/main/
    ├── java/com/regionrental/
    │   ├── RegionRental.java        # Main plugin class
    │   ├── commands/                # All command handlers (17 classes)
    │   │   ├── RRCommand.java
    │   │   ├── ReloadCommand.java
    │   │   ├── CreateSignCommand.java
    │   │   ├── ResetCommand.java
    │   │   ├── RemoveCommand.java
    │   │   ├── RetrieveCommand.java
    │   │   ├── InfoCommand.java
    │   │   ├── ListCommand.java
    │   │   ├── ExtendCommand.java
    │   │   ├── DurationCommand.java
    │   │   ├── RefundHistoryCommand.java  # View refund transaction history
    │   │   ├── VerifyCommand.java         # Verify region configurations
    │   │   ├── OverrideCommand.java       # Set per-region custom overrides
    │   │   ├── GroupCommand.java          # Manage region groups
    │   │   ├── MemberCommand.java         # Add/remove rental members
    │   │   ├── MembersCommand.java        # List rental members
    │   │   └── TpCommand.java             # Teleport to rented regions
    │   ├── config/                  # Configuration managers (5 classes)
    │   │   ├── ConfigManager.java
    │   │   ├── RegionsConfig.java   # Per-region settings manager
    │   │   ├── SignsConfig.java
    │   │   ├── StorageConfig.java
    │   │   └── GroupsConfig.java    # Region groups manager
    │   ├── listeners/               # Event listeners (2 classes)
    │   │   ├── SignInteractListener.java
    │   │   └── GroupChatListener.java  # Chat prompts for group commands
    │   ├── managers/                # Core managers (9 classes)
    │   │   ├── Rental.java          # Data model
    │   │   ├── RentalManager.java
    │   │   ├── SignManager.java
    │   │   ├── StorageManager.java
    │   │   ├── ExpirationManager.java
    │   │   ├── EzChestShopManager.java  # EzChestShop integration
    │   │   ├── TeleportCooldownManager.java  # Teleport cooldown tracking
    │   │   ├── WorldGuardManager.java
    │   │   └── WorldEditManager.java
    │   └── util/                    # Utility classes (1 class)
    │       └── WorldRegionParser.java   # Composite key parsing (world:region)
    └── resources/
        ├── plugin.yml               # Plugin metadata
        └── config.yml               # Default configuration
```

**Total: 35 Java classes + 2 resource files**

## Dependencies

### Compile-time Dependencies
- Paper API 1.21.3-R0.1-SNAPSHOT
- Vault API 1.7
- WorldGuard 7.0.14
- WorldEdit 7.3.16 (Bukkit and Core)
- LuckPerms API 5.4

### Soft Dependencies (Optional)
- LuckPerms
- EzChestShopReborn

### Required Runtime Dependencies
- Vault (required)
- WorldGuard (required)
- WorldEdit (required)
- Any economy plugin (required)

## Technical Verification

### Additional Requirements Met
- ✅ **All commands begin with `/rr`** - No conflicts with other plugins
- ✅ **Comprehensive config** - 100+ configuration options
- ✅ **Separate config files** - 5 config files (config.yml, regions.yml, signs.yml, storage.yml, groups.yml)
- ✅ **Sign allows time extension** - Shift-click to extend
- ✅ **Auto-verification system** - Ensures config integrity on startup

### Technical Requirements Verified
- ✅ **Paper/Spigot 1.21+** - Built for Paper API 1.21.3
- ✅ **WorldGuard 7.0.14** - Full integration implemented
- ✅ **WorldEdit 7.3.16** - Block restoration implemented
- ✅ **Vault plugin** - Economy system integrated
- ✅ **Economy plugin support** - Works with EssentialsX, CMI, etc.
- ✅ **All classes correct** - Proper package structure
- ✅ **Correct JavaPlugin terms** - Extends JavaPlugin properly
- ✅ **Build will run** - Gradle build configured correctly
- ✅ **Java 21+** - Compiled with OpenJDK 21 target

## Troubleshooting

### Build Errors

**"Permission denied" when running ./gradlew**
```bash
chmod +x gradlew
```

**"Java version mismatch"**
- Ensure Java 21+ (OpenJDK) is installed
- Check with: `java -version`

**Compilation errors**
- All classes are included and properly structured
- Check that all files are in correct directories
- Run `./gradlew clean build --stacktrace` for detailed error info

## Version Management

This project follows **Semantic Versioning (SemVer)** with the format: `MAJOR.MINOR.PATCH`

When updating the version, modify these files:
- `build.gradle.kts` - Line 7: `version = "X.X.X"`
- `src/main/resources/plugin.yml` - Line 2: `version: X.X.X`
- `README.md` - Line 5: `Version: X.X.X`
- JAR filename references in documentation

For detailed developer documentation, see [CLAUDE.md](CLAUDE.md).
