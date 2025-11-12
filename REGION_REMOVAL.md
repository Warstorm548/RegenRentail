# Region Removal Feature

## Overview

The `/rrremove` command allows administrators to completely remove RegionRental setup from a region when it's no longer needed or needs to be repurposed. This is different from `/rrreset` which only resets an active rental.

## Command Usage

```
/rrremove <region>
```

**Aliases:** `/remove` (if no conflicts)

**Permission:** `regionrental.admin.remove`

## What It Does

When you execute `/rrremove <region>`, the command performs the following actions:

### 1. Check for Active Rentals
If the region currently has an active rental, it will:
- Reset the rental (removes player from region)
- Issue a **full refund** to the player
- Notify the player (if online) about the reset and refund
- Store items from containers (if enabled)
- Restore blocks with WorldEdit (if enabled)

### 2. Remove Rental Sign
- Clears all lines on the physical sign
- Removes sign location from `signs.yml`
- Sign block remains in world (can be manually broken if desired)

### 3. Delete WorldEdit Schematic
If a WorldEdit schematic exists for the region:
- Deletes the schematic file from `plugins/RegionRental/schematics/`
- Frees up disk space

### 4. Provide Feedback
Admin receives a detailed message showing:
- Confirmation of removal
- Whether sign was removed
- Whether schematic was deleted
- Whether active rental was reset with refund

## Example Output

### When Removing Region with Active Rental

```
[RegionRental] Active rental found. Player Steve has been refunded $500.00
[RegionRental] RegionRental setup completely removed from shop1:
  ✓ Rental sign removed
  ✓ WorldEdit schematic deleted
  ✓ Active rental reset with refund
```

### When Removing Region without Active Rental

```
[RegionRental] RegionRental setup completely removed from shop2:
  ✓ Rental sign removed
  ✓ WorldEdit schematic deleted
```

### Server Log Entry

```
[INFO] Admin Notch removed RegionRental setup from region: shop1
```

## Use Cases

### 1. Repurposing a Region
When you want to convert a rental region into something else:
```
/rrremove oldshop
```
The region can now be used for other purposes without rental functionality.

### 2. Removing Unused Rentals
Clean up regions that are no longer being rented:
```
/rrremove unused_region
```

### 3. Cleaning Up Test Regions
After testing rental functionality:
```
/rrremove test_region
```

### 4. Server Restructuring
When reorganizing your server layout:
```
/rrremove market_stall_1
/rrremove market_stall_2
/rrremove market_stall_3
```

## Comparison with Other Commands

| Command | Purpose | Refunds Player | Removes Sign | Deletes Schematic |
|---------|---------|----------------|--------------|-------------------|
| `/rrreset` | Reset active rental | ✅ Full refund | ❌ No | ❌ No |
| `/rrremove` | Remove rental setup | ✅ Full refund (if rented) | ✅ Yes | ✅ Yes |

## What Remains After Removal

After using `/rrremove`, the following remain unchanged:
- ✅ The WorldGuard region itself (still exists)
- ✅ The sign block in the world (can be manually broken)
- ✅ Any builds or modifications in the region
- ✅ Region boundaries and flags

## What Is Removed

- ❌ Rental sign configuration in `signs.yml`
- ❌ WorldEdit schematic file
- ❌ Active rental (if any)
- ❌ Sign text (cleared to blank lines)

## Permissions

**Admin Permission:**
```yaml
regionrental.admin.remove: true  # Required to use /rrremove
```

Included in `regionrental.admin.*` permission group (default: op)

## Safety Features

1. **Automatic Refund**: Players are always refunded if their rental is reset
2. **Player Notification**: Online players are notified about the reset and refund
3. **Audit Logging**: All removal actions are logged to server console
4. **Region Check**: Verifies WorldGuard region exists before attempting removal
5. **Comprehensive Feedback**: Admin receives detailed information about what was removed

## Configuration

The removal message can be customized in `config.yml`:

```yaml
messages:
  region-removed: '&aRegionRental setup completely removed from &e{region}&a:'
```

## Best Practices

### Before Removing
1. Check if region is actively rented: `/rrinfo <region>`
2. Notify affected players if possible
3. Consider backing up data if needed

### After Removing
1. Manually break the sign block if desired
2. Reconfigure the WorldGuard region for new purpose
3. Update any server documentation or maps

## Troubleshooting

**"Region not found"**
- Ensure the WorldGuard region name is spelled correctly
- Use `/rg list` to see all available regions

**Sign not removed from world**
- The command clears sign text but doesn't break the block
- Manually break the sign block if you want to remove it completely

**Schematic not deleted**
- Schematic may not have existed (restoration feature was disabled)
- Check `plugins/RegionRental/schematics/` folder manually

## Technical Details

### Code Flow
1. Permission check (`regionrental.admin.remove`)
2. WorldGuard region existence validation
3. Active rental check and reset (if exists)
4. Sign removal via `SignManager.removeRegionSetup()`
5. Schematic deletion via `WorldEditManager.deleteCapture()`
6. Feedback generation and logging

### Files Modified
- `signs.yml` - Sign location removed
- `rentals.yml` - Active rental removed (if any)
- `schematics/<region>.dat` - Schematic file deleted

### Methods Used
- `RentalManager.resetRentalWithRefund()` - Full refund on active rental
- `SignManager.removeRegionSetup()` - Sign cleanup
- `WorldEditManager.deleteCapture()` - Schematic deletion

## Future Enhancements

Potential improvements for this feature:
- Confirmation prompt for regions with active rentals
- Bulk removal command (`/rrremove-bulk <region1> <region2>...`)
- Option to completely break sign block
- Backup creation before removal
- Restore removed region from backup

## Related Commands

- `/rrcreatesign <region>` - Create a new rental sign
- `/rrreset <region>` - Reset only the rental (keeps setup)
- `/rrinfo <region>` - Check rental status before removing
