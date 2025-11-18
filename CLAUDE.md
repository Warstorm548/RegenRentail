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
  - **NOTE:** Per-region settings have been moved to `regions.yml` (see below)
- **`regions.yml`** - Per-region override settings (managed by RegionsConfig) **[NEW]**
  - Automatically populated when rental signs are created
  - Override settings: price, duration, max-extensions, extension-price, allow-extensions, extension-duration
  - Auto-verification system ensures all regions with signs have config entries
  - Migration: Old `regions:` section from config.yml automatically migrated
- **`signs.yml`** - Sign locations and support block data (managed by SignManager)
  - Stores sign coordinates
  - Stores support block coordinates, original type, and block data for restoration
- **`storage.yml`** - Stored items from expired rentals (managed by StorageConfig)
- **`rentals.yml`** - Active rental data including initialPrice field for tracking extension costs

Config managers: `ConfigManager`, `RegionsConfig`, `SignsConfig`, `StorageConfig`

### Command Structure

All commands use `/rr` prefix to avoid conflicts. The plugin attempts to register short aliases (e.g., `/reload`, `/info`) but falls back to prefixed versions if conflicts exist.

**Command classes in `commands/` package (11 total):**
- `RRCommand` - Main help command dispatcher
- `ReloadCommand` - Reload configuration
- `CreateSignCommand` - Create rental signs (auto-populates regions.yml)
- `ResetCommand` - Reset rentals with full refund
- `RemoveCommand` - Remove RegionRental setup from regions (removes from regions.yml)
- `RetrieveCommand` - Retrieve stored items
- `InfoCommand` - View rental information
- `ListCommand` - List rentals
- `ExtendCommand` - Extend rentals
- `DurationCommand` - Modify rental duration (add/remove/set/reset)
- `RefundHistoryCommand` - View refund history for a rental
- `VerifyCommand` - Verify and repair region configurations **[NEW]**

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
├── commands/                   # Command executors (11 classes)
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
│   └── VerifyCommand.java      # Verify and repair region configs (NEW)
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
- **Separate config file** for per-region settings, automatically managed
- **Auto-population** when rental signs are created via `/rrcreatesign`
- **Auto-verification** ensures all regions with signs have config entries
- **Migration system** automatically moves old `regions:` data from config.yml
- **Template-based entries** with commented examples for easy customization
- **Partial overrides** - only specify settings you want to change
- **Manual verification** via `/rrverify` command
- **Auto-removal** from regions.yml when `/rrremove` is used

**Available per-region overrides:**
- `price` - Rental price (overrides default)
- `duration` - Rental duration in days
- `max-extensions` - Maximum extensions allowed
- `extension-price` - Price per extension day (auto-calculated if 0)
- `allow-extensions` - Enable/disable extensions for region
- `extension-duration` - Extension duration in days

**Technical Implementation:**
- `RegionsConfig.java`: New config manager for regions.yml (CRUD, verification, migration)
- `ConfigManager.java`: Updated to query RegionsConfig first, fallback to defaults
- `CreateSignCommand.java`: Auto-populates regions.yml on sign creation
- `RemoveCommand.java`: Removes region from regions.yml on setup removal
- `VerifyCommand.java`: New command for manual verification and repair
- `RegionRental.java`: Initializes RegionsConfig, runs verification on startup/reload
- Migration runs once on first startup if config.yml has `regions:` section

**Config options (in config.yml):**
```yaml
regions-config:
  auto-verify-regions: true       # Auto-verify on startup/reload
  enable-verify-command: true     # Enable /rrverify command
```

**Verification behavior:**
- **On startup/reload** (if auto-verify enabled): Checks all regions with signs have config entries
- **Missing configs**: Automatically added with commented template
- **Orphaned configs**: Logged in debug mode (configs without signs)
- **Manual check**: `/rrverify` shows detailed report and repairs issues

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
    # Only override what you need - rest uses defaults
    price: 300.0
```

**Benefits:**
- Clean separation of region-specific settings
- Self-documenting with templates
- Prevents config drift (verification system)
- Easy bulk management of region settings
- No manual tracking needed

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
