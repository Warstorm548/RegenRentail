# ZoneRental - Features & Implementation Guide

## Core Features

### Rental System
- **Clickable rental signs** - Right-click to rent, shift-click to extend
- **Time-based rentals** - Configurable durations with automatic expiration
- **Extension system** - Multiple extensions with configurable limits
- **Automatic expiration** - Scheduled checks with warnings (24h, 12h, 6h, 1h)
- **Rental limits** - Configurable max rentals per player

### Economy Integration
- **Vault integration** - Universal economy support
- **Per-region pricing** - Customize price for each region
- **Permission-based pricing** - VIP discounts via permissions
- **Extension pricing** - Configurable extension cost multipliers
- **Full refund system** - Automatic refunds when admins reset rentals

### WorldGuard Integration
- **Automatic member management** - Players added/removed from regions
- **Region validation** - Ensures regions exist before operations
- **Multi-world support** - Works across different worlds
- **Permission-based access** - Fine-grained permission control

### WorldEdit Block Restoration
- **Automatic capture** - Region state saved when rental starts
- **Automatic restoration** - Blocks restored when rental expires
- **Entity support** - Optional entity restoration
- **Sponge Schematic format** - Industry-standard `.schem` files for reliable persistence
- **Schematic management** - Auto-delete schematics (configurable)
- **Storage location** - `plugins/ZoneRental/schematics/*.schem`

### Item Storage System
- **Container scanning** - Automatically scans expired rentals for items
- **GUI retrieval** - Players can retrieve items via `/zrretrieve`
- **Multi-page support** - Handles large item collections
- **Auto-cleanup** - Configurable item storage expiration
- **Supported containers** - Chests, barrels, shulkers, hoppers, furnaces, etc.

### Sign Management
- **Customizable formats** - 4-line sign templates
- **Dynamic updates** - Auto-update every 30 seconds
- **Status indicators** - Available/Rented/Expiring states
- **Sign protection** - Protected from breaking
- **Support block protection** - Blocks under/behind signs also protected
- **Color code support** - Full color and formatting codes

## Commands

### Admin Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/zr help` | Show help menu | `zonerental.admin` |
| `/zrreload` | Reload configuration | `zonerental.admin.reload` |
| `/zrcreatesign <region>` | Create rental sign | `zonerental.admin.createsign` |
| `/zrreset <region>` | Reset rental with full refund | `zonerental.admin.reset` |
| `/zrremove <region>` | Remove entire rental setup | `zonerental.admin.remove` |
| `/zrduration add\|remove\|set\|reset <region> <time>` | Modify rental duration | `zonerental.admin.duration` |
| `/zroverride <setting> <region\|group:name> <value>` | Set per-region/group overrides | `zonerental.admin.override` |
| `/zrgroup create\|edit\|delete\|list\|view` | Manage region groups | `zonerental.admin.group` |
| `/zrverify [region]` | Verify region configurations | `zonerental.admin.verify` |

### User Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/zrinfo <region>` | View rental information | `zonerental.info` |
| `/zrlist [player]` | List rentals | `zonerental.list` |
| `/zrextend <region>` | Extend rental | `zonerental.extend` |
| `/zrretrieve` | Retrieve stored items | `zonerental.retrieve` |
| `/zrmember add\|remove\|list <region> [player]` | Manage and view rental members | `zonerental.member` / `zonerental.members` |
| `/zrtp <region>` | Teleport to rented region | `zonerental.tp` |

## Key Features Explained

### 1. Full Refund System

When admins reset rentals using `/zrreset`, players receive 100% refund of all payments:
- Initial rental payment
- All extension payments

**Process:**
1. Admin uses `/zrreset <region>`
2. System calculates total paid (initial + extensions)
3. Full refund issued via Vault economy
4. Player notified (if online)
5. Admin receives detailed feedback
6. Action logged to server console

**Configuration:**
```yaml
messages:
  admin-reset-success: '&aSuccessfully reset rental for &e{region}&a. Player &e{player}&a has been refunded &e{amount}&a.'
  rental-reset-refund: '&aYour rental of &e{region}&a has been reset by an admin. You have been refunded &e{amount}&a.'
```

### 2. Region Removal System

The `/zrremove` command completely removes rental setup from regions:

**What it removes:**
- Active rental (with full refund)
- Rental sign from configuration
- Physical sign text
- WorldEdit schematics

**What remains:**
- WorldGuard region itself
- Sign block (can be manually broken)
- Region builds and modifications

**Use cases:**
- Repurposing rental regions
- Cleaning up unused rentals
- Server restructuring
- Removing test regions

**Comparison with `/zrreset`:**

| Feature | `/zrreset` | `/zrremove` |
|---------|-----------|-------------|
| Purpose | Reset active rental | Remove entire setup |
| Refunds player | ✅ Yes | ✅ Yes (if rented) |
| Removes sign config | ❌ No | ✅ Yes |
| Clears sign text | ❌ No | ✅ Yes |
| Deletes schematic | ❌ No | ✅ Yes |
| Can use again | ✅ Yes | ❌ No (requires new setup) |

### 3. Support Block Protection

Signs and their support blocks are protected from breaking:

**Protected blocks:**
- **Wall signs** - The block the sign is attached to
- **Standing signs** - The block below the sign

**Features:**
- Original block type and data saved
- Automatic migration of existing signs
- Restoration on `/zrremove`
- Admin bypass permission: `zonerental.admin.breaksign`

**Data storage:**
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

### 4. Duration Management

The `/zrduration` command provides flexible rental time management:

**Subcommands:**
- `add <region> <time>` - Add time to rental
- `remove <region> <time>` - Remove time from rental
- `set <region> <time>` - Set exact expiration time
- `reset <region>` - Reset to default duration

**Extension refund system:**
When using `reset`, optionally refund extension costs:
```yaml
extension:
  refund-on-duration-reset: true
```

This refunds only extension payments, not the initial rental cost.

### 5. Region Grouping System

Group multiple regions together for mass configuration management.

**Commands:**
| Command | Description |
|---------|-------------|
| `/zrgroup create <name> [regions]` | Create a new group |
| `/zrgroup edit <name> add [regions]` | Add regions to group |
| `/zrgroup edit <name> remove [regions]` | Remove regions from group |
| `/zrgroup delete <name> [confirm]` | Delete group (requires confirmation) |
| `/zrgroup list` | List all groups |
| `/zrgroup view <name>` | View group details |

**Region Format:**
- Same world as player: `shop1` or `world:shop1`
- Different world: `world_nether:shop1` (explicit prefix required)
- Console: Always requires `world:region` format
- Multiple regions: `shop1,shop2,world_nether:shop3` (comma-separated)

**Group Overrides:**
Use the `group:` prefix to set overrides for an entire group:
```bash
/zroverride price group:shop_group 1000
/zroverride duration group:stores 30
```

**Override Priority:**
1. Group override (highest priority)
2. Individual region override
3. Default values (from config.yml)

**Key Behaviors:**
- Regions can only be in one group at a time
- Adding regions to a group removes their individual overrides
- Deleting a group removes all group overrides
- All signs in a group update together when override is set

**Configuration:**
```yaml
# groups.yml
groups:
  shop_group:
    regions:
      - "world:shop1"
      - "world:shop2"

# regions.yml (group overrides)
groups:
  shop_group:
    price: 1000.0
    duration: 30
    max-extensions: 20
```

### 6. Multi-World Support

Rental regions work across multiple worlds (overworld, nether, end, custom worlds).

**Features:**
- Each region stores its world name
- Same region names can exist in different worlds
- All operations are world-aware

**Technical Details:**
- Composite keys: `worldName:regionName` (e.g., "world:shop1")
- Automatic migration from single-world format on first load
- Commands accept both `region` and `world:region` formats

**Example:**
```yaml
# rentals.yml
rentals:
  world:shop1:
    region-name: shop1
    world: world
  world_nether:shop1:  # Same name, different world
    region-name: shop1
    world: world_nether
```

### 7. Member Management

Rental owners can add other players as members to their rented regions.

**Commands:**
| Command | Description | Permission |
|---------|-------------|------------|
| `/zrmember add <region> <player>` | Add member to rental | `zonerental.member` |
| `/zrmember remove <region> <player>` | Remove member from rental | `zonerental.member` |
| `/zrmember list <region>` | List all members | `zonerental.members` |

**Features:**
- Members are added to WorldGuard region members
- Members can build and interact within the region
- Automatic cleanup when rental expires
- Configurable member limit

**Configuration:**
```yaml
members:
  enabled: true
  max-members: 5  # -1 for unlimited
```

**Restrictions:**
- Only the renter can add/remove members
- Members do NOT get item retrieval access
- Cannot add yourself as a member

### 8. Teleportation

Rental owners and members can teleport directly to their rented regions.

**Commands:**
| Command | Description | Permission |
|---------|-------------|------------|
| `/zrtp <region>` | Teleport to rented region | `zonerental.tp` |

**Features:**
- 3D safe location detection (searches forward, down, up)
- Configurable cooldown system
- Cross-world teleportation support
- Sound and particle effects

**Safe Location Algorithm:**
- Searches in front of sign based on sign facing direction
- Finds solid floor with 2-block head clearance
- Avoids dangerous blocks (lava, fire, cactus, etc.)

**Configuration:**
```yaml
teleport:
  enabled: true
  cooldown: 30  # seconds, 0 to disable
  forward-search-distance: 5
  floor-search-down: 20
  floor-search-up: 20
  cross-world-warning: true
  sound-enabled: true
  particle-enabled: true
```

## Configuration

### Main Settings

```yaml
# Economy settings
default-price: 100.0
extension-multiplier: 1.0
currency-format: "$%.2f"

# Duration settings
default-duration: 7  # days
max-extensions: 10
max-rentals-per-player: 3

# Expiration settings
expiration-check-interval: 1  # minutes
warning-times:
  - 1440  # 24 hours
  - 720   # 12 hours
  - 360   # 6 hours
  - 60    # 1 hour

# Block restoration
restoration:
  enabled: true
  auto-delete-schematics: true
  restore-entities: true
  restore-biomes: false

# Extension settings
extension:
  refund-on-duration-reset: true
```

### Per-Region Overrides

```yaml
regions:
  shop1:
    price: 500.0
    duration: 14
    max-extensions: 20
  plot1:
    price: 200.0
    duration: 30
```

### Permission-Based Pricing

```yaml
permission-prices:
  'zonerental.vip': 50.0      # 50% off
  'zonerental.premium': 75.0   # 25% off
```

### Sign Formats

```yaml
sign-format:
  available:
    - "&a&l[RENT]"
    - "&e{region}"
    - "&a{price}"
    - "&7Right-click"
  rented:
    - "&c&l[RENTED]"
    - "&e{region}"
    - "&7by {player}"
    - "&7{time} left"
  expiring:
    - "&6&l[EXPIRING]"
    - "&e{region}"
    - "&7by {player}"
    - "&c{time} left"
```

### Message Placeholders

All messages support these placeholders:
- `{prefix}` - Plugin prefix
- `{region}` - Region name
- `{player}` - Player name
- `{price}` - Formatted price
- `{days}` - Rental duration
- `{time}` - Time remaining
- `{amount}` - Refund amount

## Technical Architecture

### Manager Classes

1. **RentalManager** - Central rental lifecycle management
   - Stores active rentals in `ConcurrentHashMap`
   - Handles creation, extension, expiration
   - Persists to `rentals.yml`
   - Enforces rental limits
   - Key method: `resetRentalWithRefund()`

2. **SignManager** - Sign creation and updates
   - Stores sign locations in `signs.yml`
   - Support block protection
   - Auto-updates every 30 seconds
   - Key methods: `removeRegionSetup()`, `getSupportBlockRegion()`

3. **WorldGuardManager** - WorldGuard integration
   - Adds/removes players from regions
   - Region validation
   - Uses WorldGuard native API

4. **WorldEditManager** - Block restoration
   - Captures region state on rental
   - Stores snapshots in `schematics/`
   - Restores on expiration
   - Key methods: `captureRegion()`, `restoreRegion()`

5. **StorageManager** - Item storage
   - Scans containers on expiration
   - Stores to `storage.yml`
   - GUI retrieval system

6. **ExpirationManager** - Expiration handling
   - Runs every minute (configurable)
   - Sends warnings before expiration
   - Triggers expiration workflow

### Data Model

**Rental class:**
- Region name (unique ID)
- Player UUID and name
- Start and end timestamps
- Extension count
- Total paid amount
- Initial price (for extension cost tracking)

### File Structure

```
plugins/ZoneRental/
├── config.yml          # Main configuration
├── regions.yml         # Per-region overrides
├── groups.yml          # Region group definitions
├── signs.yml           # Sign locations and support blocks
├── storage.yml         # Stored items
├── rentals.yml         # Active rentals
└── schematics/         # WorldEdit snapshots
    └── *.dat          # Region backups
```

## Build System

**Technology:**
- Gradle 9.2.0 (Kotlin DSL)
- Java 21
- Kotlin 2.2.20
- Paper API 1.21.3

**Build commands:**
```bash
./gradlew clean build       # Build plugin
./gradlew compileJava       # Compile only
./gradlew shadowJar         # Create shadow JAR
```

**Output:**
```
build/libs/ZoneRental-3.0.4.jar
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Paper API | 1.21.3 | Server platform |
| Kotlin | 2.2.20 | JVM language support |
| WorldGuard | 7.0.14+ | Region management |
| WorldEdit | 7.3.16+ | Block restoration |
| Vault | 1.7+ | Economy integration |
| LuckPerms | 5.4+ | Permissions (optional) |

## Scheduled Tasks

- **Expiration checker** - Every 1 minute (configurable)
- **Sign updater** - Every 30 seconds
- **Auto-save** - Every 5 minutes

## Testing Checklist

See [In_Game_Testing_Checklist.md](In_Game_Testing_Checklist.md) for comprehensive in-game testing checklist covering:
- Core rental system
- Sign and support block protection
- Economy and refunds
- Block restoration
- Item storage
- Member management
- Teleportation
- Region grouping
- Override system
- Multi-world support
- Admin commands
- Edge cases

## Known Limitations

- Support block detection requires sign to be properly attached during `/zrcreatesign`
- Container scanning is synchronous (may cause lag on very large regions)
- Extension limit is global (not per-region configurable)
- Schematic serialization uses Java serialization

## Version History

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

**v3.0.4** - Current
- Complete Kotlin migration for all commands
- Project renamed from RegionRental to ZoneRental
- Sealed classes for type-safe command handling
- Extension functions for cleaner APIs
- Performance optimizations (dirty tracking, lazy loading, O(1) lookups)
- Region grouping system for mass configuration
- Multi-world support for rental regions
- Member management for rented regions
- Teleportation to rented regions
- Full refund system
- Support block protection
- WorldEdit block restoration

---

**For more information:**
- Architecture details: See `CLAUDE.md`
- User guide: See `README.md`
