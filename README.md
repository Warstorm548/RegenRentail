# RegionRental - Minecraft Plugin

**Complete WorldGuard Region Rental System with Clickable Signs**

Version: 2.8.0
Minecraft: Paper/Spigot 1.21+
Languages: Java 21, Kotlin 2.2.20

## ✅ Features

### Core Features
- ✅ **Multi-world support** - Rental regions work across multiple worlds (overworld, nether, end)
- ✅ **Clickable rental signs** - Right-click to rent, shift-click to extend
- ✅ **Vault economy** - Full money integration
- ✅ **WorldGuard regions** - Automatic member management
- ✅ **Time-based rentals** - Configurable durations
- ✅ **Extension system** - Extend rentals with configurable limits
- ✅ **Block restoration** - Auto-restore on expiry with WorldEdit (world-aware)
- ✅ **Item storage** - Items saved from containers
- ✅ **Item retrieval** - `/rrretrieve` command
- ✅ **Per-region configuration** - Command-based override system via `/rroverride`
- ✅ **Region grouping** - Group regions for mass override operations via `/rrgroup`
- ✅ **Member management** - Add/remove members to rentals via `/rrmember`
- ✅ **Teleportation** - Teleport to rented regions via `/rrtp`
- ✅ **Duration management** - Add, remove, set, or reset rental time via `/rrduration`
- ✅ **Config verification** - Verify region configurations and defaults
- ✅ **Configurable pricing** - Per-region overrides or defaults
- ✅ **Configurable durations** - Flexible time settings
- ✅ **LuckPerms compatible** - Full permission support
- ✅ **Sign & support block protection** - Signs and their supporting blocks are protected from breaking
- ✅ **Auto expiration** - Automatically checks for expired rentals
- ✅ **Custom messages** - All configurable
- ✅ **Refund tracking** - Complete refund history per rental
- ✅ **EzChestShop integration** - Automatic shop removal on expiration
- ✅ **Admin commands** - `/rrreload` and more

## 🔨 Building from Source

For build instructions and project structure, see [BUILDING.md](BUILDING.md).

## 🚀 Installation

1. **Copy the JAR to your server:**
```bash
cp build/libs/RegionRental-2.8.0.jar /path/to/server/plugins/
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

1. **Navigate to the world** where you want to create the rental region (overworld, nether, end, etc.)

2. Create a WorldGuard region:
```
/rg define shop1
```

3. Place a sign and look at it

4. Create the rental sign:
```
/rrcreatesign shop1
```
**Note:** The sign will be created for the region in your current world. You can have regions with the same name in different worlds.

### Renting a Region

- **Right-click** the sign to rent
- **Shift + Right-click** to extend your rental

### Commands

All commands start with `/rr` to avoid conflicts:

**User Commands:**
- `/rr help` - Show help menu
- `/rrinfo <region>` - View rental information
- `/rrlist [player]` - List active rentals (across all worlds)
- `/rrextend <region>` - Extend a rental
- `/rrretrieve` - Get stored items from expired rentals
- `/rrmember add <region> <player>` - Add a member to your rented region
- `/rrmember remove <region> <player>` - Remove a member from your rented region
- `/rrmembers <region>` - List members of your rented region
- `/rrtp <region>` - Teleport to your rented region (works for owners and members)

**Admin Commands:**
- `/rrreload` - Reload configuration
- `/rrcreatesign <region>` - Create a rental sign in your current world
- `/rrreset <region>` - Reset a rental with full refund
- `/rrduration <add|remove|set|reset> <region> [time] [--charge]` - Modify rental duration
  - `add` - Add time to rental (use `--charge` flag to charge the player)
  - `remove` - Remove time from rental (refunds proportionally if configured)
  - `set` - Set absolute duration
  - `reset` - Reset to default duration (refunds extensions if configured)
  - Time formats: `2d 3h 30m` or `2 days 3 hours 30 minutes`
- `/rroverride <subcommand> [args]` - Set per-region or group custom settings
  - `price <target> <amount>` - Set custom rental price
  - `duration <target> <days>` - Set custom duration
  - `maxextensions <target> <count>` - Set max extensions
  - `extensionprice <target> <amount>` - Set extension price
  - `allowextensions <target> true|false` - Enable/disable extensions
  - `extensionduration <target> <days>` - Set extension duration
  - `remove <target>` - Remove all overrides (use defaults)
  - `list [target]` - View overrides for target or all
  - Target: Use `group:name` for groups, or `region` / `world:region` for regions
- `/rrremove <region>` - Remove RegionRental setup from a region
- `/rrrefundhistory <region>` - View refund history for a rental
- `/rrverify` - Verify region configurations across all worlds
- `/rrgroup create <name> [regions]` - Create a region group
- `/rrgroup edit <name> add/remove [regions]` - Modify group membership
- `/rrgroup delete <name>` - Delete a region group
- `/rrgroup list` - List all region groups
- `/rrgroup view <name>` - View details of a region group

**World-Aware Commands:**
Most commands accept both formats for the `<region>` argument:
- Simple: `/rrinfo shop1` - Uses player's current world
- Explicit: `/rrinfo world_nether:shop1` - Targets specific world

Console users must always use explicit `world:region` format.

## ⚙️ Configuration

The plugin creates six configuration files:

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

### `groups.yml` - Region Groups
Stores region group definitions for mass override operations:
- Group name to region list mappings
- Managed via `/rrgroup` commands

**Example groups.yml:**
```yaml
groups:
  shop_group:
    regions:
      - "world:shop1"
      - "world:shop2"
      - "world_nether:shop3"
```

### `rentals.yml` - Active Rentals (Runtime)
Automatically created and managed at runtime:
- Stores all active rental data with world information
- Uses `worldName:regionName` format for unique identification
- Includes rental start/end timestamps
- Tracks extension count and total paid amount
- Includes `initialPrice` field for tracking extension costs separately
- Includes `world` field for multi-world support
- Auto-saved every 5 minutes
- Automatic migration from old format on first load

## 📁 Plugin Data Directory

The plugin creates the following directory structure at runtime:

```
plugins/RegionRental/
├── config.yml          # Main configuration
├── regions.yml         # Per-region custom overrides (managed via commands)
├── groups.yml          # Region group definitions (managed via commands)
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
- `regionrental.member` - Manage members in rented regions
- `regionrental.members` - View members of rented regions
- `regionrental.tp` - Teleport to rented regions

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
- `regionrental.admin.bypass` - Bypass rental restrictions (includes teleport cooldown)
- `regionrental.admin.breaksign` - Break rental signs and support blocks
- `regionrental.admin.list.others` - List other players' rentals
- `regionrental.admin.group` - Manage region groups

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
- **Sign updater**: Every 30 seconds - Updates all rental signs with current status
- **Auto-save**: Every 5 minutes - Saves data only when changes are detected
- **Cooldown cleanup**: Every 10 minutes - Cleans expired teleport cooldowns
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
When a rental expires, the plugin automatically detects and removes any EzChestShop shops in the region. Shop holograms are cleaned up and chest contents are preserved in item storage for retrieval.

**Configuration:**
```yaml
integration:
  ezchestshop:
    enabled: true                                   # Enable/disable integration
    notify-on-removal: true                         # Notify player when shops removed
    removal-message: '&eChest shops in &6{region}&e have been removed due to rental expiration.'
```

**Compatibility:**
- Works with EzChestShop 1.9.2+
- Automatically detected at runtime (no configuration needed)
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

### Multi-World Support
RegionRental now fully supports rental regions across multiple worlds, enabling admins to set up rental shops in different dimensions:

**Key Features:**
- **World-Aware Rentals**: Each rental region tracks which world it belongs to (e.g., "world", "world_nether", "world_the_end")
- **Unique Per-World**: Same region name can exist independently in different worlds (e.g., "shop1" in overworld AND "shop1" in nether)
- **Automatic World Detection**: Commands and sign interactions automatically use the player's current world
- **Correct WorldEdit Operations**: Block capture and restoration now happen in the correct world (fixes critical bug)
- **Optimized Performance**: Direct world-specific lookups eliminate inefficient world scanning
- **Automatic Migration**: Existing rentals automatically assigned to first world on upgrade with zero downtime

**How It Works:**
- **Composite Keys**: Rentals stored using `worldName:regionName` format internally (e.g., "world:shop1")
- **World-Aware Commands**: All commands operate in the player's current world automatically
- **WorldEdit Integration**: Captures and restores blocks in the correct world for each rental
- **WorldGuard Integration**: Manages region membership per-world efficiently

**Data Migration:**
- Old rentals.yml data automatically migrated on first startup
- Existing rentals default to first world (usually "world")
- Migration logged clearly in console
- Data immediately re-saved in new format
- **100% backward compatible** - no manual intervention required

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

**Usage Examples:**
```bash
# In overworld
/rrcreatesign shop1           # Creates sign for shop1 in overworld
/rrinfo shop1                  # Shows info for shop1 in overworld

# In nether (same region name)
/rrcreatesign shop1           # Creates sign for shop1 in nether
/rrinfo shop1                  # Shows info for shop1 in nether
```

**Technical Benefits:**
- ✅ No more world collisions or data corruption
- ✅ WorldEdit captures/restores in correct world
- ✅ Direct world lookups for better performance
- ✅ Backward compatible with existing installations
- ✅ Fully automatic migration

## 🆕 Changelog

For version history and release notes, see [CHANGELOG.md](CHANGELOG.md).

## 🐛 Troubleshooting

**"Vault not found"**
- Install Vault plugin

**"WorldGuard not found"**
- Install WorldGuard 7.0.14+

**"WorldEdit not found"**
- Install WorldEdit 7.3.16+

**"No economy system"**
- Install an economy plugin (EssentialsX, CMI, etc.)

For build-related issues, see [BUILDING.md](BUILDING.md).

## ⚠️ Known Limitations

- **Manual sign placement**: Signs must be manually placed before creating rental sign with `/rrcreatesign`
- **Container scanning**: Synchronous operation that may cause minor lag on very large regions
- **Support block detection**: Requires sign to be properly attached when using `/rrcreatesign`

These limitations are noted for transparency and may be addressed in future updates based on user feedback.

## 📚 Documentation Files

This project includes comprehensive documentation:
- **README.md** (this file) - User guide and feature overview
- **CHANGELOG.md** - Version history and release notes
- **BUILDING.md** - Build instructions and project structure
- **CLAUDE.md** - Developer documentation and technical details
- **FEATURES.md** - Comprehensive feature overview
- **REFUND_SYSTEM_IMPLEMENTATION_PROGRESS.md** - Details on refund system implementation

## 📝 License

This plugin is provided for use on Minecraft servers.

## 🎉 Credits

- Built for Paper/Spigot 1.21+
- Uses WorldGuard API for region management
- Uses WorldEdit API for block restoration
- Uses Vault API for economy integration
- Compatible with LuckPerms permission system

---

**Enjoy your complete rental system with per-region configuration, automatic verification, and comprehensive refund tracking!**

For technical details and development information, see [CLAUDE.md](CLAUDE.md).
