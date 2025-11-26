# RegionRental - Minecraft Plugin

**Complete WorldGuard Region Rental System with Clickable Signs**

Version: 1.3.5
Minecraft: Paper/Spigot 1.21+
Java: OpenJDK 21+
Build System: Gradle 9.2.0

## ✅ All Features Implemented

### Core Features
- ✅ **Clickable rental signs** - Right-click to rent, shift-click to extend
- ✅ **Vault economy** - Full money integration
- ✅ **WorldGuard regions** - Automatic member management
- ✅ **Time-based rentals** - Configurable durations
- ✅ **Extension system** - Extend with limits
- ✅ **Block restoration** - Auto-restore on expiry with WorldEdit
- ✅ **Item storage** - Items saved from containers
- ✅ **Item retrieval** - `/rrretrieve` command
- ✅ **Per-region configuration** - Command-based override system via `/rroverride`
- ✅ **Config verification** - Verify region configurations and defaults
- ✅ **Configurable pricing** - Per-region overrides or defaults
- ✅ **Configurable durations** - Flexible time settings
- ✅ **LuckPerms compatible** - Full permission support
- ✅ **Sign protection** - Can't be broken (includes support block protection)
- ✅ **Support block protection** - Blocks supporting signs are also protected
- ✅ **Auto expiration** - Checks every minute
- ✅ **Custom messages** - All configurable
- ✅ **Refund tracking** - Complete refund history per rental
- ✅ **EzChestShop integration** - Automatic shop removal on expiration
- ✅ **Admin commands** - `/rr reload` and more

### Additional Requirements Met
- ✅ **All commands begin with `/rr`** - No conflicts with other plugins
- ✅ **Comprehensive config** - 100+ configuration options
- ✅ **Separate config files** - 4 config files (config.yml, regions.yml, signs.yml, storage.yml)
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

## 📦 Project Structure

```
RegionRental/
├── build.gradle                     # Gradle build configuration
├── settings.gradle                  # Gradle settings
├── gradle.properties                # Gradle properties
├── build.sh                         # Build script
├── README.md                        # This file
├── CLAUDE.md                        # Developer documentation
└── src/main/
    ├── java/com/regionrental/
    │   ├── RegionRental.java        # Main plugin class
    │   ├── commands/                # All command handlers (13 classes)
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
    │   │   └── OverrideCommand.java       # NEW: Set per-region custom overrides
    │   ├── config/                  # Configuration managers (4 classes)
    │   │   ├── ConfigManager.java
    │   │   ├── RegionsConfig.java   # NEW: Per-region settings manager
    │   │   ├── SignsConfig.java
    │   │   └── StorageConfig.java
    │   ├── listeners/               # Event listeners (1 class)
    │   │   └── SignInteractListener.java
    │   └── managers/                # Core managers (8 classes)
    │       ├── Rental.java          # Data model
    │       ├── RentalManager.java
    │       ├── SignManager.java
    │       ├── StorageManager.java
    │       ├── ExpirationManager.java
    │       ├── WorldGuardManager.java
    │       ├── WorldEditManager.java
    │       └── EzChestShopManager.java  # EzChestShop integration
    └── resources/
        ├── plugin.yml               # Plugin metadata
        └── config.yml               # Default configuration
```

**Total: 26 Java classes + 2 resource files**

## 🔨 Build Instructions

### Prerequisites

1. **Java 21+** (OpenJDK recommended) - Required for compilation and runtime
2. **Gradle 9.2.0** - Included via Gradle Wrapper (no installation needed)

### Build the Plugin

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
build/libs/RegionRental-1.3.5.jar
```

## 🚀 Installation

1. **Copy the JAR to your server:**
```bash
cp build/libs/RegionRental-1.3.5.jar /path/to/server/plugins/
```

2. **Install required dependencies:**
   - Vault
   - WorldGuard 7.0.14+
   - WorldEdit 7.3.16+
   - Any economy plugin (EssentialsX, CMI, etc.)
   - (Optional) LuckPerms for advanced permissions
   - (Optional) EzChestShop for automatic shop removal on rental expiration

3. **Restart your server**

4. **Configure the plugin:**
   - Edit `plugins/RegionRental/config.yml`
   - Set your prices, durations, messages, etc.

## 🎮 Usage

### Creating a Rental Sign

1. Create a WorldGuard region:
```
/rg define shop1
```

2. Place a sign and look at it

3. Create the rental sign:
```
/rrcreatesign shop1
```

### Renting a Region

- **Right-click** the sign to rent
- **Shift + Right-click** to extend your rental

### Commands

All commands start with `/rr` to avoid conflicts:

**User Commands:**
- `/rr help` - Show help menu
- `/rr info <region>` - View rental information
- `/rr list [player]` - List active rentals
- `/rrextend <region>` - Extend a rental
- `/rrretrieve` - Get stored items from expired rentals

**Admin Commands:**
- `/rrreload` - Reload configuration
- `/rrcreatesign <region>` - Create a rental sign (uses defaults until overrides set)
- `/rrreset <region>` - Reset a rental (with full refund)
- `/rrduration <add|remove|set|reset> <region> [<time>]` - Modify rental duration
  - `add` - Add time to rental
  - `remove` - Remove time from rental
  - `set` - Set absolute duration
  - `reset` - Reset to default duration (refunds extensions if configured)
- `/rroverride <subcommand> [args]` - Set per-region custom rental settings
  - `price <region> <amount>` - Set custom rental price
  - `duration <region> <days>` - Set custom duration
  - `maxextensions <region> <count>` - Set max extensions
  - `extensionprice <region> <amount>` - Set extension price
  - `allowextensions <region> true|false` - Enable/disable extensions
  - `extensionduration <region> <days>` - Set extension duration
  - `remove <region>` - Remove all overrides (use defaults)
  - `list [region]` - View overrides for region or all regions
- `/rrremove <region>` - Remove RegionRental setup from a region
- `/rrrefundhistory <region>` - View complete refund transaction history for a rental
- `/rrverify` - Verify region configurations (shows defaults vs custom overrides)

## ⚙️ Configuration

The plugin creates four separate configuration files:

### `config.yml` - Main Configuration
- General settings (prefix, debug mode)
- Economy settings (prices, currency format)
- Duration settings (default days, extension days)
- Extension settings (extension duration, price multiplier, max extensions, refund on reset)
- Sign formats (customizable 4-line formats)
- Storage settings (container types, auto-cleanup)
- Block restoration settings (WorldEdit integration)
- Integration settings (EzChestShop removal, notifications)
- Messages (100% customizable with placeholders)
- Permission-based pricing
- Regions-config settings (auto-verify, verify command)

### `regions.yml` - Per-Region Configuration
Command-based per-region override system:
- **Configured via `/rroverride` commands** - No manual file editing required
- **Defaults for all regions** - Regions NOT in this file use config.yml defaults
- **Auto-verified** on startup/reload to report orphaned configs
- Override any setting per region: price, duration, max-extensions, extension-price, etc.
- Supports partial overrides (only specify what you want to change)
- Migration from old config.yml format happens automatically

**Workflow:**
1. Create rental sign: `/rrcreatesign shop1` (uses defaults)
2. Set custom price: `/rroverride price shop1 500.0`
3. Sign automatically updates to show new price
4. View overrides: `/rroverride list shop1`
5. Remove overrides: `/rroverride remove shop1` (reverts to defaults)

**Example regions.yml:**
```yaml
regions:
  shop1:
    price: 500.0
    duration: 14
    max-extensions: 20
  shop2:
    price: 300.0  # Only override price, rest uses defaults
```

### `signs.yml` - Sign Storage
Automatically stores all rental sign locations and support block data:
- Sign coordinates (world, x, y, z)
- Support block coordinates and original type
- Original block data for proper restoration

### `storage.yml` - Item Storage
Automatically stores items from expired rentals

### `rentals.yml` - Active Rentals (Runtime)
Automatically created and managed at runtime:
- Stores all active rental data
- Includes rental start/end timestamps
- Tracks extension count and total paid amount
- Includes `initialPrice` field for tracking extension costs separately
- Auto-saved every 5 minutes

## 📁 Plugin Data Directory

The plugin creates the following directory structure at runtime:

```
plugins/RegionRental/
├── config.yml          # Main configuration
├── regions.yml         # Per-region custom overrides (managed via commands)
├── signs.yml           # Sign locations and support block data
├── storage.yml         # Stored items from expired rentals
├── rentals.yml         # Active rental data (runtime)
└── schematics/         # WorldEdit region snapshots (*.dat files)
    ├── region1.dat
    ├── region2.dat
    └── ...
```

**Notes:**
- `schematics/` folder is automatically created when first rental is made
- Schematic files can be auto-deleted after restoration (configurable)
- `rentals.yml` is auto-saved every 5 minutes and on plugin disable
- `regions.yml` is managed via `/rroverride` commands (no manual editing needed)
- Regions not in `regions.yml` use default values from `config.yml`

## 🔒 Permissions

**User Permissions (default: true):**
- `regionrental.rent` - Rent regions
- `regionrental.extend` - Extend rentals
- `regionrental.retrieve` - Retrieve stored items
- `regionrental.info` - View rental info
- `regionrental.list` - List your rentals

**Admin Permissions (default: op):**
- `regionrental.admin.*` - All admin permissions
- `regionrental.admin.reload` - Reload the plugin
- `regionrental.admin.createsign` - Create rental signs
- `regionrental.admin.reset` - Reset rentals (with full refund)
- `regionrental.admin.duration` - Modify rental duration (add/remove/set/reset)
- `regionrental.admin.override` - Set per-region custom rental settings
- `regionrental.admin.remove` - Remove RegionRental setup from regions
- `regionrental.admin.refundhistory` - View refund transaction history
- `regionrental.admin.verify` - Verify region configurations
- `regionrental.admin.bypass` - Bypass rental restrictions
- `regionrental.admin.breaksign` - Break rental signs and support blocks
- `regionrental.admin.list.others` - List other players' rentals
- `regionrental.admin.retime` - (Deprecated) Merged into duration command

## 🎯 Features in Detail

### Complete Rental Lifecycle

The plugin manages the entire rental lifecycle automatically:

1. **Setup Phase**:
   - Admin creates WorldGuard region with `/rg define <region>`
   - Admin places a sign and creates rental sign with `/rrcreatesign <region>`
   - Sign uses default settings from `config.yml`
   - Admin optionally sets custom overrides with `/rroverride` commands
   - Support block (if any) is detected and protected

2. **Rental Phase**:
   - Player right-clicks sign to rent (economy check performed)
   - WorldEdit captures region state (blocks and entities)
   - Player added to WorldGuard region members
   - Rental data saved to `rentals.yml`
   - Sign updated to show "RENTED" status

3. **Extension Phase** (Optional):
   - Player shift-clicks sign to extend rental
   - Extension limit and price checked
   - Rental extended and data updated
   - Extension costs tracked separately for refunds

4. **Expiration Phase**:
   - Warning notifications sent (24h, 12h, 6h, 1h before expiry)
   - On expiration: Player removed from region
   - Items from containers stored in `storage.yml`
   - EzChestShop shops removed from region (if enabled)
   - WorldEdit restores region to original state
   - Sign updated to show "AVAILABLE"
   - Player retrieves items with `/rrretrieve`

5. **Admin Management**:
   - `/rrreset <region>` - Resets rental with full refund to player
   - `/rrduration reset <region>` - Resets duration, optionally refunds extensions
   - `/rroverride <subcommand> <region> <value>` - Set custom rental settings per region
   - `/rrremove <region>` - Complete removal (refund, restore support block, delete schematic)
   - `/rrverify` - Verify configurations and show defaults vs custom overrides

### Sign Interaction
- **Right-click**: Rent if available, show info if rented
- **Shift-click**: Extend your rental (with limits)
- Signs update automatically every 30 seconds
- Signs are protected from breaking (configurable)
- **Support blocks are protected**: Players cannot break the block a sign is attached to or placed on
  - For wall signs: The block the sign is attached to is protected
  - For standing signs: The block below the sign is protected
  - Original block state is saved and restored when rental setup is removed
  - Automatic migration for existing signs on plugin startup

### Item Storage System
When a rental expires:
1. All containers in the region are scanned
2. Items are stored in `storage.yml`
3. Player is notified (if online)
4. Player uses `/rrretrieve` to get items back

### Expiration System
- Checks every minute for expired rentals (configurable via `expiration-check-interval`)
- Sends warnings at 24h, 12h, 6h, and 1h before expiration
- Automatically removes player from WorldGuard region
- Stores items if configured
- Restores region to original state via WorldEdit
- Updates sign to show "AVAILABLE"

### Automated Tasks
The plugin runs several automated background tasks:
- **Expiration checker**: Every 1 minute (configurable) - Checks for expired rentals
- **Sign updater**: Every 30 seconds (600 ticks) - Updates all rental signs with current status
- **Auto-save**: Every 5 minutes (6000 ticks) - Saves rental data to rentals.yml
- **Auto-verification**: On startup/reload (if enabled) - Verifies regions.yml integrity

### Economy Integration
- Uses Vault for universal economy support
- Configurable prices per region
- Permission-based pricing (VIP discounts)
- Extension price multiplier
- Automatic refunds on failed operations
- **Full refunds on admin resets** - Players receive 100% refund when admin uses `/rrreset`
- **Extension refunds on duration reset** - Optional refund of extension costs when admin uses `/rrduration reset` (configurable via `extension.refund-on-duration-reset`)

### Block Restoration (WorldEdit)
- Automatically captures region state when rental starts
- Restores blocks and entities when rental expires
- Schematics stored in `plugins/RegionRental/schematics/`
- Configurable auto-delete of schematics after restoration
- Optional entity and biome restoration

### EzChestShop Integration
RegionRental seamlessly integrates with the EzChestShop plugin to automatically remove chest shops when rentals expire:

**Features:**
- **Automatic Detection**: Detects EzChestShop at runtime (no compile-time dependency required)
- **Smart Removal**: Automatically removes all chest shops in expired rental regions
- **Hologram Cleanup**: Ensures shop holograms are completely removed (no visual glitches)
- **Timing**: Shops removed AFTER items are scanned/stored but BEFORE WorldEdit restoration
- **Optional Notifications**: Configurable player notifications when shops are removed
- **Conditional Restoration**: Only restores chest blocks if WorldEdit restoration is disabled

**How It Works:**
1. When a rental expires, RegionRental scans the region for chest-type containers
2. For each chest, checks if it has an EzChestShop shop using reflection API
3. If shop detected: Clears chest inventory (items already saved by StorageManager)
4. Physically breaks the chest block, triggering EzChestShop's cleanup event
5. EzChestShop automatically removes the shop data and holograms
6. If WorldEdit is disabled: Waits 3 ticks and restores the chest block
7. If WorldEdit is enabled: Leaves as AIR, WorldEdit restores from schematic

**Configuration:**
```yaml
integration:
  ezchestshop:
    enabled: true                                   # Enable/disable integration
    notify-on-removal: true                         # Notify player when shops removed
    removal-message: '&eChest shops in &6{region}&e have been removed due to rental expiration.'
```

**Technical Details:**
- Uses reflection to access EzChestShop's internal API (compatible with version 1.9.2+)
- No compile-time dependency - works even if EzChestShop is not installed
- Block break/replace approach ensures reliable cleanup without API version issues
- Supports chest, trapped chest, and barrel shop types

### Region Removal
- Use `/rrremove <region>` to completely remove rental setup
- Automatically resets active rentals with full refund
- Removes signs from configuration
- Deletes WorldEdit schematics
- **Restores support blocks** to their original state (type and orientation)
- Useful for repurposing regions or server restructuring

### Support Block Protection
- **Automatic Detection**: When creating a rental sign, the plugin automatically detects and protects the block it's attached to or placed on
- **Data Storage**: Original block type and orientation are saved in `signs.yml` for later restoration
- **Protection**: Players cannot break support blocks without the `regionrental.admin.breaksign` permission
- **Restoration**: When using `/rrremove`, the original block is restored before the sign is removed
- **Migration**: Existing signs are automatically migrated to include support block protection on plugin startup
- **Prevents Bypass**: Players can no longer bypass sign protection by breaking the supporting block

### Per-Region Configuration System
- **Command-Based Configuration**: All per-region settings managed via `/rroverride` commands
- **Defaults First**: Creating a rental sign with `/rrcreatesign` uses config.yml defaults
- **In-Game Management**: Set custom overrides using simple commands (no file editing)
- **Auto-Verification**: On startup/reload, reports orphaned configs (configs without signs)
- **Automatic Migration**: Old `regions:` section from config.yml migrated automatically on first startup
- **Partial Overrides**: Only override the settings you want to change, rest use defaults
- **Manual Verification**: Use `/rrverify` to check defaults vs custom overrides
- **Auto-Cleanup**: Regions removed via `/rrremove` are also removed from regions.yml
- **Immediate Updates**: Signs automatically update when overrides are changed

**Available Per-Region Overrides:**
- `price` - Custom rental price
- `duration` - Custom rental duration in days
- `max-extensions` - Maximum number of extensions allowed
- `extension-price` - Price per extension day
- `allow-extensions` - Enable/disable extensions for this region
- `extension-duration` - Extension duration in days

**Workflow Example:**
```bash
# Create sign (uses defaults)
/rrcreatesign shop1

# Set custom price
/rroverride price shop1 500.0

# Set custom duration
/rroverride duration shop1 14

# View all overrides for region
/rroverride list shop1

# Remove all overrides (revert to defaults)
/rroverride remove shop1
```

### Refund History Tracking
- **Complete Transaction History**: Every refund is tracked with timestamp, amount, and reason
- **Admin Command**: Use `/rrrefundhistory <region>` to view all refunds for a rental
- **Automatic Tracking**: Refunds from resets, duration changes, and removals are all logged
- **Detailed Information**: Shows refund date, amount, and reason for each transaction
- **Prevents Double-Refunds**: System tracks what has been refunded to prevent duplicate payments

## 🆕 Recent Updates

### Version 1.3.3 - EzChestShop Integration

#### EzChestShop Plugin Support
- **Automatic shop removal** on rental expiration for seamless cleanup
- **Runtime detection** using reflection-based API (no compile-time dependency required)
- **Smart cleanup approach** using block break/replace for reliable shop and hologram removal
- **Conditional restoration** - Only restores chest blocks if WorldEdit restoration is disabled
- **Configurable notifications** - Optional player notifications when shops are removed
- **Perfect timing** - Shops removed after storage scan but before WorldEdit restoration
- **Compatible with EzChestShop 1.9.2+** with automatic version detection

#### Technical Implementation
- **Reflection API** for accessing EzChestShop's internal ShopContainer class
- **Block break/replace pattern** triggers EzChestShop's BlockBreakEvent for automatic cleanup
- **No hologram glitches** - EzChestShop's event handler removes all holograms automatically
- **Inventory protection** - Clears chest inventory before breaking (items already saved)
- **3-tick delay** for chest restoration when WorldEdit is disabled
- **Configuration options** in config.yml under `integration.ezchestshop`

#### Configuration Options
```yaml
integration:
  ezchestshop:
    enabled: true                    # Enable/disable integration
    notify-on-removal: true          # Notify player when shops removed
    removal-message: '&eChest shops in &6{region}&e have been removed.'
```

### Version 1.1.0 - Command-Based Override System

#### Region Override Command System
- **New command** `/rroverride` for setting per-region custom rental settings
- **8 subcommands**: price, duration, maxextensions, extensionprice, allowextensions, extensionduration, remove, list
- **In-game configuration** - No manual file editing required
- **Defaults-first approach** - Regions not in regions.yml use config.yml defaults
- **Immediate updates** - Signs automatically update when overrides are changed
- **Tab completion** - Full tab completion support for all subcommands and region names
- **WorldGuard validation** - Prevents setting overrides for non-existent regions

#### Updated Verification System
- **Enhanced `/rrverify`** now shows breakdown of defaults vs custom overrides
- **Reports orphaned configs** - Configs without signs (can be cleaned up)
- **No longer auto-repairs** - Only reports status, admins manage via commands

#### Removed Auto-Population
- **Simplified workflow** - `/rrcreatesign` no longer auto-populates regions.yml
- **Cleaner files** - regions.yml only contains intentional overrides
- **Better clarity** - Clear which regions use defaults vs custom settings

### Version 1.0.0 - Previous Features

#### Per-Region Configuration System (regions.yml)
- **Config file** `regions.yml` for managing per-region override settings
- **Migration system** automatically moves old `regions:` data from config.yml
- **Partial overrides** - Only specify settings you want to change

#### Command Consolidation - Duration Management
- **Removed** `RetimeCommand` - functionality merged into `DurationCommand`
- **Enhanced** `/rrduration` command now supports:
  - `add` - Add time to rental
  - `remove` - Remove time from rental
  - `set` - Set absolute duration
  - `reset` - Reset to default duration with optional extension refund
- **Extension refund system** - Optionally refunds extension costs when resetting duration
- **Config option** `extension.refund-on-duration-reset` controls refund behavior
- **Simplified interface** - No longer requires player name, only region name

#### Refund History Tracking
- **New command** `/rrrefundhistory <region>` to view complete refund transaction history
- **Automatic tracking** of all refunds (resets, duration changes, removals)
- **Detailed logging** with timestamps, amounts, and reasons
- **Double-refund prevention** system tracks what has been refunded

#### Enhanced Support Block Protection
- **Auto-migration** of existing signs to include support block protection
- **Improved data storage** for original block type and orientation
- **Restoration system** properly restores support blocks when using `/rrremove`

## 🐛 Troubleshooting

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

### Runtime Issues

**"Vault not found"**
- Install Vault plugin

**"WorldGuard not found"**
- Install WorldGuard 7.0.14+

**"WorldEdit not found"**
- Install WorldEdit 7.3.16+

**"No economy system"**
- Install an economy plugin (EssentialsX, CMI, etc.)

## ⚠️ Known Limitations

- **Multi-world support**: Currently uses the first world found for region lookups
- **Manual sign placement**: Signs must be manually placed before creating rental sign with `/rrcreatesign`
- **Container scanning**: Synchronous operation that may cause minor lag on very large regions
- **Support block detection**: Requires sign to be properly attached when using `/rrcreatesign`

These limitations are noted for transparency and may be addressed in future updates based on user feedback.

## 📚 Documentation Files

This project includes comprehensive documentation:
- **README.md** (this file) - User guide and feature overview
- **CLAUDE.md** - Developer documentation and technical details
- **BUILD_VERIFICATION.md** - Build and deployment verification
- **REFUND_IMPLEMENTATION.md** - Details on refund system
- **REGION_REMOVAL.md** - Complete guide to region removal feature
- **FEATURE_SUMMARY.md** - Comprehensive feature overview
- **IMPLEMENTATION_SUMMARY.md** - Latest implementation details

## 📝 License

This plugin is provided for use on Minecraft servers.

## 🎉 Credits

- Built for Paper/Spigot 1.21+
- Uses WorldGuard API for region management
- Uses WorldEdit API for block restoration
- Uses Vault API for economy integration
- Compatible with LuckPerms permission system
- Built with Gradle 9.2.0 build system

---

**Enjoy your complete rental system with per-region configuration, automatic verification, and comprehensive refund tracking!**

For technical details and development information, see [CLAUDE.md](CLAUDE.md).
