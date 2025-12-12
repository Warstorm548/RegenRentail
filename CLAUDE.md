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
build/libs/RegionRental-2.3.0.jar
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
- **Version:** 2.5.1
- **Last Updated:** PR Review Fixes (cache invalidation bug, thread safety, cleaner save pattern)

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

Commands are registered dynamically at runtime using a configurable prefix (default: `/rr`). The prefix is determined during plugin startup:
- If the configured prefix has no conflicts, it's used
- If conflicts exist, falls back to `/rr`
- If `/rr` also conflicts, auto-generates `rr1`, `rr2`, etc.

**Important:** Only the determined active prefix is registered. There are no fallback registrations or short aliases.

**Command classes in `commands/` package (16 total):**
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
- `OverrideCommand` - Set per-region or per-group custom settings
- `GroupCommand` - Manage region groups for mass override operations
- `MemberCommand` - Add/remove members to/from rented regions
- `MembersCommand` - List members of a rented region

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
The plugin supports grouping multiple regions together for mass override operations:

**Core Concept:**
- Group regions across multiple worlds for unified configuration management
- Set overrides once for entire group instead of per-region
- Hybrid command interface: inline arguments or chat prompts (60s timeout)

**Group Commands:**
```bash
/rrgroup create <name> [regions]       # Create group (prompt if no regions)
/rrgroup edit <name> add [regions]     # Add regions to group
/rrgroup edit <name> remove [regions]  # Remove regions from group
/rrgroup delete <name> [confirm]       # Delete group (requires confirmation)
/rrgroup list                          # List all groups
/rrgroup view <name>                   # View group details
```

**Region Format:**
- Same world as player: `shop1` or `world:shop1` (both valid)
- Different world: `world_nether:shop1` (explicit prefix required)
- Console: Always requires `world:region` format
- Multiple regions: `shop1,shop2,world_nether:shop3` (comma-separated)

**Group Override System:**

The `group:` prefix parser resolves naming conflicts when a group and region share the same name:

**With `group:` prefix** (targets groups ONLY):
```bash
/rroverride price group:shop_group 1000
/rroverride duration group:stores 30
/rroverride list group:premium_zones
```

**Without prefix** (targets regions ONLY):
```bash
/rroverride price shop1 500           # Targets region "shop1"
/rroverride price world:shop1 500     # Explicit world prefix
```

**Override Lookup Priority:**
1. **Group override** (if region is in a group) → highest priority
2. **Individual region override** (from regions.yml)
3. **Default values** (from config.yml)

**Key Behaviors:**
- **Individual override cleanup**: Adding regions to a group automatically removes their individual overrides
- **Group deletion cleanup**: Deleting a group removes all group overrides from regions.yml
- **Exclusive membership**: Regions can only be in one group at a time
- **Duplicate prevention**: Cannot add region if already in another group
- **Sign updates**: All signs in group update together when group override is set

**Validation:**
- Group names: 2-30 characters, alphanumeric + underscore only
- Reserved names: "all", "none", "default" (blocked)
- Region existence: Validated against WorldGuard before adding
- World existence: Validated before accepting world: prefix

**Data Storage:**

**groups.yml** (managed by GroupsConfig):
```yaml
groups:
  shop_group:
    regions:
      - "world:shop1"
      - "world:shop2"
      - "world_nether:shop3"
```

**regions.yml** (extended with group section):
```yaml
regions:
  world:shop1:
    price: 500.0  # Individual overrides (only if NOT in group)

groups:
  shop_group:
    price: 1000.0           # Group overrides
    duration: 30
    max-extensions: 20
    extension-price: 250.0
    allow-extensions: true
    extension-duration: 7
```

**Configuration Managers:**
- **GroupsConfig.java** (~500 lines) - Manages groups.yml (CRUD operations, validation, membership)
- **GroupCommand.java** (~650 lines) - Command handler with hybrid input and chat listener integration
- **GroupChatListener.java** (~90 lines) - Intercepts chat for region input prompts

### Scheduled Tasks
- **Expiration checker**: Every 1 minute (configurable via `expiration-check-interval`)
- **Sign updater**: Every 30 seconds (600 ticks)
- **Auto-save**: Every 5 minutes (6000 ticks)

### Concurrency
- Uses `ConcurrentHashMap` for rental storage to prevent race conditions
- All file I/O is synchronous (runs on main thread)
- Scheduled tasks run asynchronously where possible

### Multi-World Support
The plugin now supports rental regions across multiple worlds:

**Key Features:**
- Each rental region stores its world name (e.g., "world", "world_nether", "world_the_end")
- Region names can be identical across different worlds (e.g., "shop1" in "world" AND "shop1" in "world_nether")
- All operations are world-aware (rentals, WorldEdit capture/restore, WorldGuard membership, etc.)
- Automatic data migration from single-world to multi-world format on first load

**Technical Implementation:**
- **Composite Keys**: Rentals stored using `worldName:regionName` format (e.g., "world:shop1")
- **Rental Class**: Added `worldName` field to track which world each rental belongs to
- **RentalManager**: Uses composite keys in ConcurrentHashMap for O(1) lookups
- **WorldEditManager**: Fixed critical bug - now uses actual world instead of hardcoded first world
- **WorldGuardManager**: Added world-specific methods for direct world lookups (no more inefficient loops)
- **Data Storage**: `rentals.yml` stores world name for each rental with automatic migration

**Example YAML Structure:**
```yaml
rentals:
  world:shop1:  # Composite key: worldName:regionName
    region-name: shop1
    world: world
    player-uuid: "..."
    # ... other rental data
  world_nether:shop1:  # Same region name, different world
    region-name: shop1
    world: world_nether
    player-uuid: "..."
```

**Migration:**
- Existing rentals automatically default to first world (usually "world")
- Migration logged on first startup
- Data re-saved in new format immediately
- Fully backward compatible - no manual intervention required

**Usage in Code:**
```java
// NEW: World-aware methods (recommended)
rentalManager.createRental(regionName, world, player, days, price);
rentalManager.getRental(regionName, world);
worldEditManager.captureRegion(regionName, world);
worldEditManager.restoreRegion(regionName, world);
worldGuardManager.addPlayerToRegion(regionName, world, playerUUID);

// OLD: Deprecated methods (still work via backward compatibility)
rentalManager.createRental(regionName, player, days, price); // Uses player's world
rentalManager.getRental(regionName); // Searches all worlds, returns first match
```

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
3. Add to `RegionRental.registerCommands()` method using `registerCommandWithPrefix()`
4. Add permission node to `plugin.yml` under `permissions:`

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
2. Copy JAR: `cp build/libs/RegionRental-2.3.0.jar /path/to/server/plugins/`
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
├── commands/                   # Command executors (16 classes)
│   ├── RRCommand.java          # Main help command dispatcher
│   ├── ReloadCommand.java      # Reload configuration
│   ├── CreateSignCommand.java  # Create rental signs
│   ├── ResetCommand.java       # Reset rentals with full refund
│   ├── RemoveCommand.java      # Complete region cleanup
│   ├── RetrieveCommand.java    # Retrieve stored items
│   ├── InfoCommand.java        # View rental information
│   ├── ListCommand.java        # List all rentals
│   ├── ExtendCommand.java      # Extend rental duration
│   ├── DurationCommand.java    # Modify rental duration (add/remove/set/reset)
│   ├── RefundHistoryCommand.java  # View refund transaction history
│   ├── VerifyCommand.java      # Verify region configs (defaults vs overrides)
│   ├── OverrideCommand.java    # Set per-region custom settings
│   ├── GroupCommand.java       # Manage region groups
│   ├── MemberCommand.java      # Add/remove members
│   └── MembersCommand.java     # List members
├── config/                     # Config managers (5 classes)
│   ├── ConfigManager.java      # Main configuration manager
│   ├── RegionsConfig.java      # Per-region settings manager
│   ├── SignsConfig.java        # Sign locations and support blocks
│   ├── StorageConfig.java      # Item storage configuration
│   └── GroupsConfig.java       # Region groups manager
├── listeners/                  # Event listeners (2 classes)
│   ├── SignInteractListener.java  # Sign interaction and protection
│   └── GroupChatListener.java     # Chat prompts for group commands
├── managers/                   # Business logic managers (8 classes)
│   ├── Rental.java             # Rental data model
│   ├── RentalManager.java      # Rental lifecycle management
│   ├── SignManager.java        # Sign creation and updates
│   ├── StorageManager.java     # Item storage from expired rentals
│   ├── ExpirationManager.java  # Rental expiration handling
│   ├── EzChestShopManager.java # EzChestShop integration (optional)
│   ├── WorldGuardManager.java  # WorldGuard region integration
│   └── WorldEditManager.java   # Block capture and restoration
└── util/                       # Utility classes (1 class)
    └── WorldRegionParser.java  # Composite key parsing (world:region)
```

## Recent Features

### Version 2.5.1 - PR Review Fixes (Latest)
Patch release addressing GitHub Copilot PR review comments:

**Bug Fixes:**
- **Cache Invalidation** - Fixed `invalidateGroupCache()` never being called after group modifications
  - Added calls to: processCreateGroup, handleDelete, processAddRegions, processRemoveRegions
  - Tab completion now immediately reflects group changes

**Thread Safety Improvements:**
- **WorldEditManager** - Made collections thread-safe for potential concurrent access
  - `knownSchematics`: Changed from HashSet to ConcurrentHashMap.newKeySet()
  - `clipboardCache`: Wrapped LinkedHashMap with Collections.synchronizedMap()

**Code Quality:**
- **Cleaner Save Pattern** - Refactored save methods across 5 files
  - Moved `isDirty = false` outside try block with early return on failure
  - More explicit success-only flag reset
  - Files: StorageConfig, RegionsConfig, SignsConfig, GroupsConfig, RentalManager

**Technical Details:**
- Files modified: 7 files
- ~43 lines changed (+31/-12)
- Backward compatible

---

### Version 2.5.0 - Performance Optimization
Minor update with comprehensive performance optimizations:

**Optimizations:**
- **Dirty Tracking** - Saves only happen when data changes (80-90% disk I/O reduction)
- **Lazy Schematic Loading** - Schematics loaded on-demand with LRU cache (major RAM reduction)
- **O(1) Support Block Lookups** - HashMap index instead of O(n) scan on block breaks
- **O(1) Player/Member Lookups** - Secondary indexes for rental queries
- **Memory Leak Fixes** - GUI sessions cleaned on disconnect, cooldown cleanup task
- **EnumSet for Container Types** - O(1) contains() instead of O(n)
- **Tab Completion Caching** - 5-second TTL cache for group names

**New Configuration:**
```yaml
restoration:
  schematic-cache-size: 20  # Max schematics in memory
```

**Technical Details:**
- Files modified: 10+ files across managers, configs, and commands
- ~500 lines of optimization code
- Backward compatible with existing data

---

### Version 2.4.1 - Teleportation Safe Location Bug Fix
Patch release fixing critical teleportation bugs and implementing enhanced 3D search algorithm:

**Bug Fixes:**
- **Wall Sign Teleportation** - Fixed "no safe location found" error for wall-mounted signs
  - Original algorithm only searched upward from sign level
  - New 3D search algorithm searches forward, down, and up
  - Correctly handles signs embedded in walls

- **Standing Sign Direction** - Fixed standing signs using player's facing instead of sign rotation
  - Standing signs now use their own rotation direction
  - Both wall signs and standing signs use consistent logic
  - Teleportation works correctly regardless of player orientation

**Enhanced 3D Search Algorithm:**
- **Forward Search** - Searches 1-20 blocks in front of sign (configurable, default: 5)
  - Tests multiple positions instead of just one
  - Handles obstructions immediately in front of sign

- **Downward Floor Search** - Searches up to 20 blocks down to find floor (configurable)
  - Correctly handles signs at any height
  - Finds solid ground below player spawn position

- **Upward Floor Search** - Searches up to 20 blocks up if no floor below (configurable)
  - Handles floating platforms and elevated structures
  - Fallback when downward search finds nothing

**New Configuration Settings:**
```yaml
teleport:
  forward-search-distance: 5   # How many blocks forward to search (max: 20)
  floor-search-down: 20        # How many blocks down to search for floor (max: 20)
  floor-search-up: 20          # How many blocks up to search for floor
```

**Technical Implementation:**
- Files modified: `TpCommand.java`, `ConfigManager.java`, `config.yml`
- Added `getSignFacing()` - Extracts facing direction for both sign types
- Added `findFloorLocation()` - Searches up or down for solid floor
- Added `centerLocation()` - Centers player spawn position
- Refactored `findSafeLocation()` - Implements 3D search algorithm
- ~120 lines of code changes
- Backward compatible with existing configurations

**Benefits:**
- Teleportation works reliably for complex building geometries
- Handles wall signs, standing signs, multi-story buildings
- Configurable search distances for performance tuning
- No more "no safe location found" for valid spawn points

---

### Version 2.4.0 - Teleportation System
Minor update adding teleportation feature for rental owners and members:

**New Features:**
- **Teleport Command** - `/rrtp <region>` allows teleporting to rented regions
  - Supports both explicit (`world:region`) and implicit (`region`) formats
  - Works for rental owners and members
  - Prevents non-members from teleporting to others' rentals

- **Safe Location Detection** - Intelligent algorithm finds safe teleport spots
  - Starts 1 block in front of sign (based on sign's facing direction)
  - Searches upward only (configurable max: 20 blocks)
  - Validates floor solidity, head clearance, and dangerous blocks
  - Shows error if no safe location found within search limit

- **Cooldown System** - Prevents spam teleporting
  - Configurable cooldown (default: 30 seconds)
  - Admin bypass permission available
  - Per-player cooldown tracking
  - Automatic cleanup on plugin reload

- **Cross-World Support** - Seamless teleportation between worlds
  - Optional warning message when teleporting to different world
  - World-aware region parsing
  - Works across overworld, nether, end, and custom worlds

- **Effects & Feedback** - Enhanced user experience
  - Enderman teleport sound effect (configurable)
  - Portal particle effects (configurable)
  - Success/error messages with region information

- **Configurable Settings** - Full control via config.yml
  - `teleport.enabled` - Enable/disable feature
  - `teleport.max-search-distance` - Upward search limit (default: 20)
  - `teleport.cooldown` - Cooldown in seconds (0 to disable)
  - `teleport.cross-world-warning` - Show/hide cross-world warning
  - `teleport.sound-enabled` - Enable/disable sound effects
  - `teleport.particle-enabled` - Enable/disable particle effects

**Technical Implementation:**
- Files created: `TeleportCooldownManager.java` (~100 lines), `TpCommand.java` (~350 lines)
- Files modified: `RentalManager.java`, `ConfigManager.java`, `config.yml`, `RegionRental.java`, `plugin.yml`
- New permission: `regionrental.tp` (default: true)
- ~550 lines of new code across 7 files
- Safe location algorithm with dangerous block detection
- Tab completion for owned and member rentals

**Command Examples:**
```bash
/rrtp shop1                  # Teleport to shop1 in current world
/rrtp world:shop1            # Teleport to shop1 in specific world
/rrtp world_nether:shop2     # Teleport to shop2 in nether
```

**Configuration:**
```yaml
teleport:
  enabled: true
  max-search-distance: 20
  cooldown: 30
  cross-world-warning: true
  sound-enabled: true
  particle-enabled: true
```

**Safety Features:**
- Avoids dangerous blocks (lava, fire, cactus, magma, wither rose, campfire, etc.)
- Requires solid floor block
- Validates 2-block height clearance (feet + head)
- Detects wall sign facing direction vs standing signs
- Searches upward only (prevents underground teleports)

---

### Version 2.3.0 - Member Management System
Minor update adding complete member management for rented regions:

**New Features:**
- **Member Management Commands** - Renters can add/remove players as members
  - `/rrmember add <region> <username>` - Add member to rented region
  - `/rrmember remove <region> <username>` - Remove member from rented region
  - `/rrmembers <region>` - List all members of a region
  - World-aware region format support (e.g., `world:region`)

- **WorldGuard Integration** - Members added to WorldGuard region members
  - Members can build and manage the rented area
  - Automatic cleanup on rental expiration
  - Members removed from WorldGuard when rental expires

- **Configurable Limits** - Server admins have full control
  - `members.max-members: 5` - Set member limit (default: 5)
  - `-1` for unlimited members
  - `members.enabled: true` - Enable/disable feature

- **Access Control** - Proper security and restrictions
  - Only renter can add/remove members
  - Members do NOT get retrieval storage access
  - Cannot add yourself as a member
  - Duplicate prevention and limit enforcement

- **Persistence** - Member data saved to rentals.yml
  - Backward compatible with existing rentals
  - Automatic member cleanup on expiration
  - Member list tracked per rental

**Technical Implementation:**
- Files created: `MemberCommand.java`, `MembersCommand.java`
- Files modified: `Rental.java`, `RentalManager.java`, `ConfigManager.java`, `config.yml`, `plugin.yml`, `RegionRental.java`
- New permissions: `regionrental.member`, `regionrental.members`
- ~400+ lines of new code across 7 files

**Command Examples:**
```bash
/rrmember add shop1 PlayerName          # Add member to region in current world
/rrmember add world:shop1 PlayerName    # Add member to region in specific world
/rrmember remove shop1 PlayerName       # Remove member
/rrmembers shop1                        # List all members
```

**Configuration:**
```yaml
members:
  enabled: true
  max-members: 5  # -1 for unlimited
```

---

### Version 2.2.1 - Sign Update Bug Fix
Patch release fixing missing sign updates when regions are removed from groups:

**Bug Fixes:**
- **Sign Update Edge Cases** - Signs now update immediately when group membership changes
  - Fixed: Signs not updating when regions are removed from groups (`/rrgroup edit <name> remove`)
  - Fixed: Signs not updating when groups are deleted (`/rrgroup delete <name>`)
  - Fixed: Signs not updating when regions are added to groups with existing overrides
  - Fixed: Signs not updating when creating groups (consistency)

**Technical Details:**
- Modified `GroupCommand.java` - Added `bulkMarkSignsDirty()` calls in 4 methods:
  - `handleDelete()` - Retrieves group regions before deletion, marks signs dirty
  - `processRemoveRegions()` - Marks removed region signs dirty
  - `processAddRegions()` - Marks added region signs dirty
  - `processCreateGroup()` - Marks created group region signs dirty
- Signs update within 30 seconds (next update cycle) instead of showing stale data
- No performance impact (dirty tracking uses efficient HashSet operations)
- Ensures override lookup priority: Group → Region → Default

---

### Version 2.2.0 - Region Grouping System
Minor update adding comprehensive region grouping and mass override management:

**New Features:**
- **Region Grouping System** - Group multiple regions for unified configuration
  - `/rrgroup create/edit/delete/list/view` commands
  - Multi-world support, hybrid command interface (args OR chat prompts)
  - Exclusive membership, automatic validation
- **Mass Override Operations** - Set overrides once for entire group
  - `group:` prefix parser (`/rroverride price group:shops 1000`)
  - Override priority: Group → Region → Default
  - Bulk sign updates, automatic cleanup
- **Enhanced Tab Completion** - Smart suggestions for groups, regions, world prefixes

**Technical Implementation:**
- Files created: GroupsConfig.java, GroupCommand.java, GroupChatListener.java
- Files modified: RegionRental.java, OverrideCommand.java, SignManager.java, RegionsConfig.java
- ~2000+ lines across 5 implementation phases
- Data files: groups.yml (new), regions.yml (extended)

## Documentation Files

- `README.md` - User guide and feature list
- `CHANGELOG.md` - Version history and release notes
- `BUILDING.md` - Build instructions and project structure
- `CLAUDE.md` - Developer documentation and technical details
- `FEATURES.md` - Comprehensive feature overview
- `REFUND_SYSTEM_IMPLEMENTATION_PROGRESS.md` - Details on refund system implementation
