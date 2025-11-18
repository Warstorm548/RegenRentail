# RegionRental - Features & Implementation Guide

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
- **Schematic management** - Auto-delete schematics (configurable)
- **Storage location** - `plugins/RegionRental/schematics/`

### Item Storage System
- **Container scanning** - Automatically scans expired rentals for items
- **GUI retrieval** - Players can retrieve items via `/rrretrieve`
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
| `/rr help` | Show help menu | `regionrental.admin` |
| `/rrreload` | Reload configuration | `regionrental.admin.reload` |
| `/rrcreatesign <region>` | Create rental sign | `regionrental.admin.createsign` |
| `/rrreset <region>` | Reset rental with full refund | `regionrental.admin.reset` |
| `/rrremove <region>` | Remove entire rental setup | `regionrental.admin.remove` |
| `/rrduration add\|remove\|set\|reset <region> <time>` | Modify rental duration | `regionrental.admin.duration` |

### User Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/rrinfo <region>` | View rental information | `regionrental.info` |
| `/rrlist [player]` | List rentals | `regionrental.list` |
| `/rrextend <region>` | Extend rental | `regionrental.extend` |
| `/rrretrieve` | Retrieve stored items | `regionrental.retrieve` |

## Key Features Explained

### 1. Full Refund System

When admins reset rentals using `/rrreset`, players receive 100% refund of all payments:
- Initial rental payment
- All extension payments

**Process:**
1. Admin uses `/rrreset <region>`
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

The `/rrremove` command completely removes rental setup from regions:

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

**Comparison with `/rrreset`:**

| Feature | `/rrreset` | `/rrremove` |
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
- Restoration on `/rrremove`
- Admin bypass permission: `regionrental.admin.breaksign`

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

The `/rrduration` command provides flexible rental time management:

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
  'regionrental.vip': 50.0      # 50% off
  'regionrental.premium': 75.0   # 25% off
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
plugins/RegionRental/
├── config.yml          # Main configuration
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
- Paper API 1.21.3

**Build commands:**
```bash
./gradlew clean build       # Build plugin
./gradlew compileJava       # Compile only
./gradlew shadowJar         # Create shadow JAR
```

**Output:**
```
build/libs/RegionRental-1.0.0.jar
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Paper API | 1.21.3 | Server platform |
| WorldGuard | 7.0.14+ | Region management |
| WorldEdit | 7.3.16+ | Block restoration |
| Vault | 1.7+ | Economy integration |
| LuckPerms | 5.4+ | Permissions (optional) |

## Scheduled Tasks

- **Expiration checker** - Every 1 minute (configurable)
- **Sign updater** - Every 30 seconds
- **Auto-save** - Every 5 minutes

## Testing Checklist

### Rental System
- [ ] Create rental sign
- [ ] Rent region (right-click)
- [ ] Extend rental (shift-click)
- [ ] Rental expiration
- [ ] Multiple rentals per player
- [ ] Rental limits enforced

### Admin Commands
- [ ] Reset rental with refund (`/rrreset`)
- [ ] Remove region setup (`/rrremove`)
- [ ] Reload configuration (`/rrreload`)
- [ ] Modify rental duration (`/rrduration`)
- [ ] View rental info (`/rrinfo`)

### Block Restoration
- [ ] Region captured on rent
- [ ] Blocks restored on expiry
- [ ] Schematic deletion works
- [ ] Entity restoration (if enabled)

### Economy
- [ ] Payment on rental
- [ ] Payment on extension
- [ ] Refund on admin reset
- [ ] Refund on region removal
- [ ] Balance checking works

### Item Storage
- [ ] Items stored on expiry
- [ ] Item retrieval GUI works
- [ ] Multiple pages supported
- [ ] Auto-cleanup works

## Known Limitations

- Support block detection requires sign to be properly attached during `/rrcreatesign`
- Container scanning is synchronous (may cause lag on very large regions)
- Extension limit is global (not per-region configurable)
- Schematic serialization uses Java serialization

## Version History

**v1.0.0** - Current
- Full refund system
- Region removal command
- Support block protection
- WorldEdit block restoration
- Duration management commands
- Extension refund on reset
- Gradle build system migration

---

**For more information:**
- Architecture details: See `CLAUDE.md`
- User guide: See `README.md`
