# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RegionRental is a Minecraft Paper/Spigot plugin (1.21+) that implements a complete WorldGuard region rental system with clickable signs, Vault economy integration, time-based rentals with automatic expiration, and WorldEdit-powered block restoration.

**Technology Stack:**
- Java 21+ (OpenJDK 21)
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
build/libs/RegionRental-1.0.0.jar
```

### Development Commands
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

## Version Management

This project follows **Semantic Versioning (SemVer)** with the format: `MAJOR.MINOR.PATCH`

### Versioning Scheme

At the end of each development session, the version number should be updated based on the type of changes made:

**1. Major Update (X.0.0)**
- Increments the first digit by 1
- Resets both MINOR and PATCH to 0
- Example: `1.2.3` → `2.0.0`
- Use for: Breaking changes, major feature overhauls, API changes

**2. Minor Update (x.X.0)**
- Increments the second digit by 1
- Resets PATCH to 0
- Example: `1.2.3` → `1.3.0`
- Use for: New features, significant enhancements, new commands

**3. Patch Update (x.x.X)**
- Increments the third digit by 1
- No resets
- Example: `1.2.3` → `1.2.4`
- Use for: Bug fixes, minor tweaks, documentation updates

### Files to Update

When updating the version, modify these files:
- `build.gradle.kts` - Line 7: `version = "X.X.X"`
- `src/main/resources/plugin.yml` - Line 2: `version: X.X.X`
- `README.md` - Line 5: `Version: X.X.X`
- `README.md` - Lines 129, 136: JAR filename references
- Output JAR: `build/libs/RegionRental-X.X.X.jar`

### Current Version
- **Version:** 1.2.1
- **Last Updated:** Container storage bug fix and shulker box enhancement

## Architecture Overview

### Main Plugin Class
**`RegionRental.java`** - Main plugin entry point extending JavaPlugin. Handles:
- Plugin lifecycle (onEnable/onDisable)
- Manager initialization and coordination
- Command registration with `/rr` prefix
- Scheduled tasks (expiration checking, sign updates, auto-save)
- Command alias management (attempts to register short aliases if no conflicts)
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
   - Stores region snapshots as serialized clipboards in `schematics/` folder
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

### Data Model

**`Rental`** - Data class representing a rental:
- Region name (unique identifier)
- Player UUID and name
- Start and end timestamps
- Extension count and total paid amount
- Warning tracking (prevents duplicate expiration warnings)

### Configuration System

Four separate configuration files for organization:
- **`config.yml`** - Main settings (economy, durations, extensions, messages, restoration settings)
  - Extension settings include: `refund-on-duration-reset` - Refunds extension costs when admin resets duration to default
  - **NOTE:** Per-region settings managed via `/rroverride` commands (stored in regions.yml)
- **`regions.yml`** - Per-region custom overrides (managed by RegionsConfig via commands)
  - Configured using `/rroverride` commands (NOT auto-populated)
  - Override settings: price, duration, max-extensions, extension-price, allow-extensions, extension-duration
  - Regions NOT in this file use default values from config.yml
  - Auto-verification system reports orphaned configs (configs without signs)
  - Migration: Old `regions:` section from config.yml automatically migrated
- **`signs.yml`** - Sign locations and support block data (managed by SignManager)
  - Stores sign coordinates
  - Stores support block coordinates, original type, and block data for restoration
- **`storage.yml`** - Stored items from expired rentals (managed by StorageConfig)
- **`rentals.yml`** - Active rental data including initialPrice field for tracking extension costs

Config managers: `ConfigManager`, `RegionsConfig`, `SignsConfig`, `StorageConfig`

### Command Structure

All commands use `/rr` prefix to avoid conflicts. The plugin attempts to register short aliases (e.g., `/reload`, `/info`) but falls back to prefixed versions if conflicts exist.

**Command classes in `commands/` package (12 total):**
- `RRCommand` - Main help command dispatcher
- `ReloadCommand` - Reload configuration
- `CreateSignCommand` - Create rental signs (signs use defaults until overrides set)
- `ResetCommand` - Reset rentals with full refund
- `RemoveCommand` - Remove RegionRental setup from regions (removes from regions.yml)
- `RetrieveCommand` - Retrieve stored items
- `InfoCommand` - View rental information
- `ListCommand` - List rentals
- `ExtendCommand` - Extend rentals
- `DurationCommand` - Modify rental duration (add/remove/set/reset)
- `RefundHistoryCommand` - View refund history for a rental
- `VerifyCommand` - Verify region configurations (reports defaults vs custom overrides)
- `OverrideCommand` - Set per-region custom settings **[NEW]**

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

### Scheduled Tasks
- **Expiration checker**: Every 1 minute (configurable via `expiration-check-interval`)
- **Sign updater**: Every 30 seconds (600 ticks)
- **Auto-save**: Every 5 minutes (6000 ticks)

### Concurrency
- Uses `ConcurrentHashMap` for rental storage to prevent race conditions
- All file I/O is synchronous (runs on main thread)
- Scheduled tasks run asynchronously where possible

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
double extensionCost = rental.getExtensionCost(); // Returns totalPaid - initialPrice
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
3. Add to `RegionRental.registerCommands()` method
4. Add to alias registration in `registerAliasesIfPossible()` if needed
5. Add permission node to `plugin.yml` under `permissions:`

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

### Prerequisites
- Paper/Spigot 1.21+ server
- Vault plugin installed
- WorldGuard 7.0.14+ installed
- WorldEdit 7.3.16+ installed
- Any economy plugin (EssentialsX, CMI, etc.)

### Quick Test Workflow
1. Build: `./gradlew build`
2. Copy JAR: `cp build/libs/RegionRental-1.0.0.jar /path/to/server/plugins/`
3. Start server
4. Create WorldGuard region: `/rg define testregion`
5. Create rental sign: `/rrcreatesign testregion`
6. Test renting: Right-click the sign
7. Test extension: Shift-click the sign
8. Test reset with refund: `/rrreset testregion`
9. Test region removal: `/rrremove testregion`

## Dependencies and API Usage

### Vault Economy
- Registration: `getServer().getServicesManager().getRegistration(Economy.class)`
- Balance check: `economy.getBalance(player)`
- Withdraw: `economy.withdrawPlayer(player, amount)`
- Deposit: `economy.depositPlayer(player, amount)`

### WorldGuard API
- Get region: `WorldGuard.getInstance().getPlatform().getRegionContainer().get(world).getRegion(name)`
- Add member: `region.getMembers().addPlayer(uuid)`
- Remove member: `region.getMembers().removePlayer(uuid)`

### WorldEdit API
- Create edit session: `WorldEdit.getInstance().newEditSession(world)`
- Create clipboard: `new BlockArrayClipboard(region)`
- Copy blocks: `new ForwardExtentCopy(editSession, region, clipboard, origin)`
- Paste blocks: `clipboard.createPaste(editSession).to(origin).build()`
- Complete operations: `Operations.complete(operation)`

### Paper API
- All Bukkit/Spigot APIs work on Paper
- Uses Paper-specific features where available
- Target API version: 1.21

## File Locations

### Plugin Data Directory
`plugins/RegionRental/`
- `config.yml` - Main configuration
- `regions.yml` - Per-region override settings (auto-populated)
- `signs.yml` - Sign locations
- `storage.yml` - Stored items
- `rentals.yml` - Active rentals
- `schematics/` - WorldEdit region snapshots (*.dat files)

### Source Structure
```
src/main/java/com/regionrental/
├── RegionRental.java           # Main plugin class
├── commands/                   # Command executors (12 classes)
│   ├── RRCommand.java
│   ├── ReloadCommand.java
│   ├── CreateSignCommand.java
│   ├── ResetCommand.java       # With full refund system
│   ├── RemoveCommand.java      # Complete region cleanup
│   ├── RetrieveCommand.java
│   ├── InfoCommand.java
│   ├── ListCommand.java
│   ├── ExtendCommand.java
│   ├── DurationCommand.java    # Includes add/remove/set/reset subcommands
│   ├── RefundHistoryCommand.java  # View refund transaction history
│   ├── VerifyCommand.java      # Verify region configs (shows defaults vs overrides)
│   └── OverrideCommand.java    # Set per-region custom settings (NEW)
├── config/                     # Config managers (4 classes)
│   ├── ConfigManager.java
│   ├── RegionsConfig.java      # Per-region settings manager (NEW)
│   ├── SignsConfig.java
│   └── StorageConfig.java
├── listeners/                  # Event listeners (1 class)
│   └── SignInteractListener.java
└── managers/                   # Business logic managers (7 classes)
    ├── Rental.java             # Data model
    ├── RentalManager.java      # Rental lifecycle
    ├── SignManager.java        # Sign management
    ├── StorageManager.java     # Item storage
    ├── ExpirationManager.java  # Expiration handling
    ├── WorldGuardManager.java  # WorldGuard integration
    └── WorldEditManager.java   # Block restoration (NEW)
```

## Recent Features

### Command Consolidation - Duration Reset
- **Removed `RetimeCommand`** - Functionality merged into `DurationCommand`
- **New `/rrduration reset <region>` subcommand** - Resets rental duration to default
- **Extension refund system** - Optionally refunds extension costs when resetting duration
- **Simplified interface** - No longer requires player name, only region name
- **Config option:** `extension.refund-on-duration-reset` - Enables/disables extension refunds (default: true)
- **Rental tracking** - Added `initialPrice` field to `Rental` class to track extension costs separately

**Technical Implementation:**
- `Rental.java`: Added `initialPrice` field and `getExtensionCost()` method
- `RentalManager.java`: Updated save/load to include `initialPrice`
- `DurationCommand.java`: Added `resetDuration()` method with optional refund logic
- `ConfigManager.java`: Added `isRefundOnDurationReset()` getter
- `plugin.yml`: Removed `rrretime` command, deprecated `regionrental.admin.retime` permission
- Backward compatible with existing rental data (defaults `initialPrice` to `totalPaid`)

**Command comparison:**
- Old: `/rrretime <player> <region> [days]` - Required player name, custom days
- New: `/rrduration reset <region>` - Only region name, uses default duration, optional refund

### Per-Region Configuration System (regions.yml)
- **Separate config file** for per-region custom overrides
- **Command-based configuration** using `/rroverride` commands
- **Defaults for all regions** - regions NOT in regions.yml use config.yml defaults
- **Migration system** automatically moves old `regions:` data from config.yml
- **Verification via `/rrverify`** shows which regions use defaults vs custom settings
- **Auto-removal** from regions.yml when `/rrremove` is used

**Available per-region overrides:**
- `price` - Rental price (overrides default)
- `duration` - Rental duration in days
- `max-extensions` - Maximum extensions allowed
- `extension-price` - Price per extension day (auto-calculated if 0)
- `allow-extensions` - Enable/disable extensions for region
- `extension-duration` - Extension duration in days

**Override Commands:**
```bash
/rroverride price <region> <amount>             # Set custom rental price
/rroverride duration <region> <days>            # Set custom duration
/rroverride maxextensions <region> <count>      # Set max extensions
/rroverride extensionprice <region> <amount>    # Set extension price (0 for auto)
/rroverride allowextensions <region> true|false # Enable/disable extensions
/rroverride extensionduration <region> <days>   # Set extension duration
/rroverride remove <region>                     # Remove all overrides (use defaults)
/rroverride list [region]                       # View overrides for region or all regions
```

**Technical Implementation:**
- `OverrideCommand.java`: New command for setting region overrides
- `RegionsConfig.java`: Config manager with getter/setter methods for overrides
- `ConfigManager.java`: Queries RegionsConfig first, falls back to defaults
- `CreateSignCommand.java`: Does NOT auto-populate (signs use defaults)
- `RemoveCommand.java`: Removes region from regions.yml on setup removal
- `VerifyCommand.java`: Reports regions using defaults vs custom overrides
- `RegionRental.java`: Initializes RegionsConfig, runs verification on startup/reload
- Migration runs once on first startup if config.yml has `regions:` section

**Config options (in config.yml):**
```yaml
regions-config:
  auto-verify-regions: true       # Auto-verify on startup/reload (reports orphaned configs)
  enable-verify-command: true     # Enable /rrverify command
```

**Verification behavior:**
- **On startup/reload** (if auto-verify enabled): Reports orphaned configs (configs without signs)
- **Regions without configs**: NOT an issue - they use default values from config.yml
- **Orphaned configs**: Configs exist but no sign (can be cleaned up with `/rroverride remove`)
- **Manual check**: `/rrverify` shows detailed breakdown of defaults vs custom overrides

**Workflow:**
1. Create rental sign: `/rrcreatesign testregion` (uses defaults from config.yml)
2. Set custom price: `/rroverride price testregion 500.0`
3. Sign automatically updates to show new price
4. View overrides: `/rroverride list testregion`
5. Remove overrides: `/rroverride remove testregion` (reverts to defaults)

**Data format (regions.yml):**
```yaml
regions:
  shop1:
    price: 500.0
    duration: 14
    max-extensions: 20
    extension-price: 250.0
    allow-extensions: true
    extension-duration: 7

  shop2:
    price: 300.0  # Only overriding price - other settings use defaults
```

**Benefits:**
- Clean separation of region-specific settings
- In-game configuration via commands (no manual file editing)
- Clear defaults - regions not listed use config.yml values
- Verification system prevents orphaned configs
- Signs automatically update when overrides are set

### Support Block Protection (Sign Protection Enhancement)
- **Automatically detects support blocks** when creating rental signs
- Stores original block type and BlockData in `signs.yml` under `support-block` section
- **Protects wall signs**: Block the sign is attached to cannot be broken
- **Protects standing signs**: Block below the sign cannot be broken
- **Restores on removal**: `/rrremove` restores the support block to its original state
- **Migration system**: Existing signs are automatically migrated on plugin startup
- Prevents players from bypassing sign protection by breaking support blocks
- Admin permission `regionrental.admin.breaksign` allows breaking both signs and support blocks

**Technical Implementation:**
- `SignsConfig.java`: 6 new methods for support block data storage
- `SignManager.java`: `getSupportBlock()` detects wall/standing sign support blocks
- `SignManager.java`: `migrateSupportBlocks()` auto-migrates existing signs
- `SignInteractListener.java`: Enhanced `onBlockBreak()` to check support blocks
- New config message: `sign-support-protected`

**Data Format in signs.yml:**
```yaml
signs:
  region_name:
    world: world
    x: 100
    y: 64
    z: 200
    support-block:
      x: 100
      y: 63
      z: 200
      original-type: STONE
      original-data: "minecraft:stone"
```

### Block Restoration (WorldEdit Integration)
- Automatically captures region state when rental is created
- Restores blocks and entities when rental expires
- Schematics stored in `plugins/RegionRental/schematics/`
- Configurable auto-delete of schematics after restoration

### Full Refund System
- Players receive 100% refund (initial payment + all extensions) when admin resets rental
- Admins see detailed refund information
- Players receive notification if online
- All refunds logged to server console

### Region Removal Command
- `/rrremove <region>` completely removes rental setup
- Resets active rentals with full refund
- Removes signs from configuration
- Deletes WorldEdit schematics
- Useful for repurposing regions

## Known Limitations

- No multi-world support (uses first world found for region)
- Signs must be manually placed before creating rental sign
- Container scanning is synchronous (may cause lag on very large regions)
- Extension limit is global (not per-region configurable)
- Schematic serialization uses Java serialization (could be enhanced to Sponge format)
- Support block detection requires sign to be properly attached when using `/rrcreatesign`

## Documentation Files

- `README.md` - User guide and feature list
- `BUILD_VERIFICATION.md` - Build and deployment verification
- `REFUND_IMPLEMENTATION.md` - Details on refund system
- `REGION_REMOVAL.md` - Complete guide to region removal feature
- `FEATURE_SUMMARY.md` - Comprehensive feature overview
- `IMPLEMENTATION_SUMMARY.md` - Latest implementation details
- ask me if i would like to update the verion number at the end of each editing session provide the options major update witch will corelate to the frist number in a 3 digit verion code, minor update for the secound number and 3rd number will be patchs. the number will go up by one with each version update if a minor update is done reset the 3rd number to 0 if its a major update so treat the same as minor update but instead reset the minor and patchs number to zero.