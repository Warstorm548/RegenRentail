# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RegionRental is a Minecraft Paper/Spigot plugin (1.21+) that implements a complete WorldGuard region rental system with clickable signs, Vault economy integration, time-based rentals with automatic expiration, and WorldEdit-powered block restoration.

**Technology Stack:**
- Java 21+ (OpenJDK 21)
- Kotlin 2.2.20 (JVM)
- Paper API 1.21.3
- Gradle 9.2.0 (Kotlin DSL)
- WorldGuard 7.0.14 (region management)
- WorldEdit 7.3.16 (block editing/restoration)
- Vault API (economy integration)
- LuckPerms API (optional permissions)

## Build Commands

### Building the Plugin
```bash
# Using the build script (recommended)
./build.sh

# Or directly with Gradle
./gradlew clean build

# Output location
build/libs/RegionRental-2.7.0.jar
```

### Development Commands
```bash
# Clean build artifacts
./gradlew clean

# Compile only (no packaging)
./gradlew compileJava      # Java only
./gradlew compileKotlin    # Kotlin only

# Run shadowJar task
./gradlew shadowJar

# Build without tests
./gradlew build -x test

# Generate IDE project files
./gradlew idea        # For IntelliJ IDEA
./gradlew eclipse     # For Eclipse
```

## Version Management

This project follows **Semantic Versioning (SemVer)**: `MAJOR.MINOR.PATCH`

When updating the version, modify these files:
- `build.gradle.kts` - Line 7: `version = "X.X.X"`
- `src/main/resources/plugin.yml` - Line 2: `version: X.X.X`
- `README.md` - Version references
- Output JAR: `build/libs/RegionRental-X.X.X.jar`

**Current Version:** 2.7.0

## Architecture Overview

### Main Plugin Class
**`RegionRental.java`** - Main plugin entry point extending JavaPlugin. Handles:
- Plugin lifecycle (onEnable/onDisable)
- Manager initialization and coordination
- Dynamic command registration with configurable prefix (defaults to `/rr`)
- Scheduled tasks (expiration checking, sign updates, auto-save)
- Dependencies checking (Vault, WorldGuard, WorldEdit, Economy)

### Core Manager Architecture

The plugin uses a manager pattern where each manager handles a specific domain:

1. **`RentalManager`** - Central rental lifecycle management
   - Stores all active rentals in `ConcurrentHashMap<String, Rental>`
   - Handles rental creation, extension, and expiration
   - Persists data to `rentals.yml`
   - Enforces rental limits per player
   - Integrates with WorldEditManager for block capture/restoration
   - **Key method:** `resetRentalWithRefund()` - Resets rental with full refund to player

2. **`SignManager`** - Sign creation and updating
   - Stores sign locations linked to regions in `signs.yml`
   - **Support block protection:** Automatically detects and stores the block a sign is attached to/placed on
   - Updates sign text to show rental status (available/rented/time remaining)
   - Automatic periodic sign updates every 30 seconds
   - **Key methods:**
     - `removeRegionSetup()` - Completely removes rental setup from region and restores support block
     - `getSupportBlockRegion()` - Checks if a block is a protected support block
     - `migrateSupportBlocks()` - Auto-migrates existing signs on plugin startup

3. **`WorldGuardManager`** - WorldGuard API integration
   - Adds/removes players from WorldGuard region members
   - Region validation and existence checking
   - Uses WorldGuard's native API for region manipulation

4. **`WorldEditManager`** - WorldEdit API integration for block restoration
   - Captures region state (blocks and entities) when rental is created
   - Stores region snapshots as Sponge schematics in `schematics/` folder (.schem format)
   - Restores region to original state when rental expires
   - Automatic cleanup of schematic files (configurable)
   - **Key methods:** `captureRegion()`, `restoreRegion()`, `deleteCapture()`

5. **`StorageManager`** - Item storage from expired rentals
   - Scans region for containers (chests, barrels, shulker boxes, etc.)
   - Stores items to `storage.yml` when rental expires
   - GUI-based retrieval system via `/rrretrieve`

6. **`ExpirationManager`** - Rental expiration handling
   - Runs every minute (configurable)
   - Sends warnings at 24h, 12h, 6h, 1h before expiration
   - Triggers rental expiration workflow

### Configuration System

Six configuration files in `plugins/RegionRental/`:
- `config.yml` - Main settings (economy, durations, extensions, messages)
- `regions.yml` - Per-region overrides (via `/rroverride`)
- `groups.yml` - Region group definitions (via `/rrgroup`)
- `signs.yml` - Sign locations and support blocks
- `storage.yml` - Stored items from expired rentals
- `rentals.yml` - Active rental data

Config managers: `ConfigManager`, `RegionsConfig`, `SignsConfig`, `StorageConfig`, `GroupsConfig`

### Command Structure

Commands use a configurable prefix (default: `/rr`), dynamically registered at startup.
17 command classes in `commands/` package. See [FEATURES.md](FEATURES.md#commands) for full command list.

## Key Implementation Details

### Sign Interaction System
Located in `SignInteractListener.java`:
- **Right-click**: Rent if available, show info if rented
- **Shift-click**: Extend rental (if owned and extension limit not reached)
- **Sign protection**: Signs cannot be broken unless player has `regionrental.admin.breaksign`
- **Support block protection**: Blocks that signs are attached to/placed on are also protected
  - Wall signs: The block the sign is attached to is protected
  - Standing signs: The block below the sign is protected
  - Original block type and data saved for restoration
  - Players cannot bypass sign protection by breaking support blocks

### Rental Lifecycle
1. **Creation**: Player right-clicks sign → economy check → **WorldEdit captures region state** → rental created → player added to region → sign updated
2. **Extension**: Player shift-clicks sign → extension limit check → rental extended → sign updated
3. **Expiration**: Expiration manager runs → player removed from region → items stored → **WorldEdit restores blocks** → rental deleted → sign updated to "AVAILABLE"

### Admin Reset vs Remove
- **`/rrreset <region>`** - Resets active rental with full refund, but keeps rental setup (sign, support block protection, schematic)
- **`/rrremove <region>`** - Completely removes rental setup (resets rental if active, restores support block, removes sign, deletes schematic)

### Region Grouping System
Regions can be grouped for mass configuration via `/rrgroup` commands.
Groups share overrides set via `/rroverride group:<name> ...`.
Override priority: Group → Individual Region → Default.
See [FEATURES.md](FEATURES.md#5-region-grouping-system) for detailed documentation.

### Scheduled Tasks
- **Expiration checker**: Every 1 minute (configurable via `expiration-check-interval`)
- **Sign updater**: Every 30 seconds (600 ticks)
- **Auto-save**: Every 5 minutes (6000 ticks)

### Concurrency
- Uses `ConcurrentHashMap` for rental storage to prevent race conditions
- All file I/O is synchronous (runs on main thread)
- Scheduled tasks run asynchronously where possible

### Multi-World Support
Rental regions work across multiple worlds using composite keys.

**Key Concepts:**
- Composite keys: `worldName:regionName` (e.g., "world:shop1")
- Same region names can exist in different worlds
- All methods are world-aware: `getRental(regionName, world)`
- Automatic migration from single-world format on first load

See [FEATURES.md](FEATURES.md#6-multi-world-support) for usage examples.

## Important Patterns and Conventions

### Accessing Managers
Always use the singleton instance:
```java
RegionRental plugin = RegionRental.getInstance();
RentalManager rentalManager = plugin.getRentalManager();
WorldEditManager worldEditManager = plugin.getWorldEditManager();
```

### Configuration Access
```java
ConfigManager config = plugin.getConfigManager();
String message = config.getMessage("rental-success");
double price = config.getRegionPrice("shop1"); // Falls back to default
boolean blockRestoration = config.isBlockRestoration();
```

### WorldGuard Integration
Never manipulate WorldGuard regions directly. Always use `WorldGuardManager`:
```java
worldGuardManager.addPlayerToRegion(regionName, playerUUID);
worldGuardManager.removePlayerFromRegion(regionName, playerUUID);
```

### WorldEdit Integration
Block restoration is handled through `WorldEditManager`:
```java
// Capture region state before renting
worldEditManager.captureRegion(regionName);

// Restore region to captured state
worldEditManager.restoreRegion(regionName);

// Check if a capture exists
boolean hasCapture = worldEditManager.hasCapture(regionName);

// Delete schematic
worldEditManager.deleteCapture(regionName);
```

### Refund System
When admins reset rentals, always use the refund method:
```java
// Full refund for complete rental reset
Map<String, Object> refundDetails = rentalManager.resetRentalWithRefund(regionName);

// Extension-only refund for duration reset (via /rrduration reset)
double extensionCost = rental.getExtensionCost(); // Kotlin property: totalPaid - initialPrice
```

### Message Formatting
All user-facing messages support placeholders:
- `{prefix}` - Plugin prefix from config
- `{region}` - Region name
- `{player}` - Player name
- `{price}` - Formatted price
- `{days}` - Rental duration
- `{time}` - Time remaining
- `{amount}` - Refund amount

Messages are retrieved via `ConfigManager.getMessage()` which handles color codes (`&` to `§`) and placeholder replacement.

## Common Development Tasks

### Adding a New Command
1. Create command class in `commands/` implementing `CommandExecutor`
2. Register in `plugin.yml` under `commands:` section
3. Add to `RegionRental.registerCommands()` method using `registerCommandWithPrefix()`
4. Add permission node to `plugin.yml` under `permissions:`

### Adding a New Kotlin Class
1. Create `.kt` file in `src/main/kotlin/com/regionrental/` (or appropriate subdirectory)
2. Kotlin classes can extend Java classes and implement Java interfaces
3. Java code can call Kotlin code seamlessly (full interoperability)
4. Use Kotlin idioms: data classes, extension functions, null safety, coroutines

### Kotlin Patterns in Use

**Rental Creation (Java calling Kotlin):**
```java
// Use factory methods instead of constructors
Rental rental = Rental.create(regionName, worldName, playerUUID, playerName, endDate, price);

// Loading from storage
Rental rental = Rental.fromStorage(regionName, worldName, playerUUID, playerName,
    startDate, endDate, extensionCount, totalPaid, initialPrice, totalRefunded,
    refundHistory, members);
```

**Kotlin Properties (accessed from Java as getters):**
```java
rental.getCompositeKey()     // Kotlin: rental.compositeKey
rental.isExpired()           // Kotlin: rental.isExpired
rental.getTimeRemaining()    // Kotlin: rental.timeRemaining
rental.getExtensionCost()    // Kotlin: rental.extensionCost
```

**Extension Functions:**
```kotlin
// StringExtensions.kt
"&aHello".color()  // Translates color codes
"Hello {name}!".withPlaceholders("name" to "Steve")

// PlayerExtensions.kt
val player = sender.asPlayerOrNull() ?: return
sender.requirePermission("admin.reset", "No permission!")

// TimeUtils.kt
val millis = 7.days + 3.hours  // Duration extensions
TimeUtils.formatDuration(millis)  // "7d 3h"
```

**Sealed Classes (OverrideCommand):**
```kotlin
sealed class OverrideSetting<T> {
    data object Price : OverrideSetting<Double>()
    data object Duration : OverrideSetting<Int>()
    // ... type-safe command handling
}
```

### Adding a New Config Option
1. Add default value to `src/main/resources/config.yml`
2. Add getter method to `ConfigManager.java`
3. Load in `ConfigManager.loadConfig()` method

### Modifying Rental Behavior
- Rental creation logic: `RentalManager.createRental()`
- Rental extension logic: `RentalManager.extendRental()`
- Expiration logic: `ExpirationManager.checkExpiredRentals()`
- Block capture: Called in `RentalManager.createRental()`
- Block restoration: Called in `RentalManager.expireRental()`

### Adding New Container Types for Storage
Add to `StorageManager.CONTAINER_TYPES` set (currently supports: chest, barrel, shulker_box, trapped_chest, hopper, dispenser, dropper, furnace, blast_furnace, smoker, brewing_stand)

## Testing the Plugin

**Prerequisites:** Paper 1.21+, Vault, WorldGuard 7.0.14+, WorldEdit 7.3.16+, economy plugin

**Quick Test:**
1. Build: `./gradlew build`
2. Copy JAR to `plugins/`
3. Create region: `/rg define testregion`
4. Create sign: `/rrcreatesign testregion`
5. Test: Right-click (rent), Shift-click (extend), `/rrreset` (refund)

See [In_Game_Testing_Checklist.md](In_Game_Testing_Checklist.md) for comprehensive testing checklist.

## File Locations

### Plugin Data Directory
`plugins/RegionRental/`
- `config.yml` - Main configuration
- `regions.yml` - Per-region override settings (auto-populated)
- `groups.yml` - Region group definitions
- `signs.yml` - Sign locations
- `storage.yml` - Stored items
- `rentals.yml` - Active rentals
- `schematics/` - WorldEdit region snapshots (*.dat files)

### Source Structure

**Java source:** `src/main/java/com/regionrental/`
- `RegionRental.java` - Main plugin class
- `commands/` - 16 command executors (Java)
- `config/` - 5 configuration managers
- `listeners/` - 2 event listeners
- `managers/` - 8 business logic managers (Java)
- `util/` - Utility classes

**Kotlin source:** `src/main/kotlin/com/regionrental/`
- `extensions/` - String, Location, Player, Collection extensions (4 files)
- `util/TimeUtils.kt` - Duration formatting, time parsing
- `models/` - RefundRecord, ParsedRegion, StorageGUISession, SupportBlockData (4 files)
- `config/` - RegionOverride, MessageFormatter (2 files)
- `commands/` - OverrideCommand.kt, DurationAction.kt (2 files)
- `managers/` - Rental.kt, ManagerExtensions.kt (2 files)

**Total: 32 Java classes + 15 Kotlin files**

## Version History

See [CHANGELOG.md](CHANGELOG.md) for detailed version history and release notes.

## Documentation Files

- `README.md` - User guide and feature list
- `CHANGELOG.md` - Version history and release notes
- `BUILDING.md` - Build instructions
- `CLAUDE.md` - Developer guidance (this file)
- `FEATURES.md` - Feature overview
- `In_Game_Testing_Checklist.md` - In-game testing checklist
