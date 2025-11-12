# Rental Reset Refund Implementation

## Summary of Changes

This document outlines the implementation of the full refund system when an admin uses `/rrreset` to reset a player's rental.

## Changes Made

### 1. ConfigManager.java
**Location:** `src/main/java/com/regionrental/config/ConfigManager.java`

Added two new default messages:
- `admin-reset-success`: Message shown to admin after successfully resetting a rental with refund details
- `rental-reset-refund`: Message shown to the player when their rental is reset by an admin

```java
messages.put("admin-reset-success", "&aSuccessfully reset rental for &e{region}&a. Player &e{player}&a has been refunded &e{amount}&a.");
messages.put("rental-reset-refund", "&aYour rental of &e{region}&a has been reset by an admin. You have been refunded &e{amount}&a.");
```

### 2. RentalManager.java
**Location:** `src/main/java/com/regionrental/managers/RentalManager.java`

**Added new method:** `resetRentalWithRefund(String regionName)`
- Returns a Map containing refund details (playerUUID, playerName, refundAmount)
- Retrieves rental information before expiring
- Calculates and issues full refund based on `rental.getTotalPaid()`
- Notifies the player if they're online
- Calls `expireRental()` to handle all cleanup (remove from region, store items, restore blocks)
- Returns refund details for admin notification

**Deprecated old method:** `resetRental(String regionName)`
- Marked as deprecated with @Deprecated annotation
- Documentation recommends using `resetRentalWithRefund` instead

### 3. ResetCommand.java
**Location:** `src/main/java/com/regionrental/commands/ResetCommand.java`

**Updated to:**
- Import `java.util.Map` for handling refund details
- Use `resetRentalWithRefund()` instead of `resetRental()`
- Extract refund details from returned Map
- Format refund amount using currency format from config
- Display detailed success message to admin with:
  - Region name
  - Player name
  - Refund amount (formatted)
- Log the admin action to server logs for audit trail
- Handle failure case if refund details return null

### 4. config.yml
**Location:** `src/main/resources/config.yml`

Added customizable messages in the messages section:
```yaml
admin-reset-success: '&aSuccessfully reset rental for &e{region}&a. Player &e{player}&a has been refunded &e{amount}&a.'
rental-reset-refund: '&aYour rental of &e{region}&a has been reset by an admin. You have been refunded &e{amount}&a.'
```

## How It Works

### Workflow When Admin Uses `/rrreset <region>`

1. **Permission Check**: Verify admin has `regionrental.admin.reset` permission
2. **Validation**: Check if region exists and is currently rented
3. **Get Rental Data**: Retrieve rental information (player UUID, name, total paid amount)
4. **Issue Refund**:
   - Deposit full `totalPaid` amount to player's account via Vault Economy
   - Use configured currency format for display
5. **Notify Player**: If player is online, send them a message about the reset and refund
6. **Clean Up**: Call `expireRental()` which:
   - Removes player from WorldGuard region
   - Stores items from containers (if enabled)
   - Restores blocks with WorldEdit (if enabled)
   - Removes rental record
   - Updates sign to "AVAILABLE"
7. **Notify Admin**: Send detailed success message with refund information
8. **Log Action**: Record the admin action in server logs

### Example Messages

**Admin receives:**
```
[RegionRental] Successfully reset rental for shop1. Player Steve has been refunded $500.00.
```

**Player receives (if online):**
```
[RegionRental] Your rental of shop1 has been reset by an admin. You have been refunded $500.00.
```

**Server log:**
```
[RegionRental] Admin Notch reset rental for region shop1. Refunded $500.00 to Steve
```

## Refund Calculation

The refund amount is based on `Rental.getTotalPaid()`, which includes:
- Initial rental payment
- All extension payments

This ensures the player receives a **full refund** of everything they paid for the rental.

## Message Placeholders

Both new messages support the following placeholders:
- `{region}` - The region name
- `{player}` - The player's name
- `{amount}` - The refund amount (formatted with currency format)

## Configuration

Server owners can customize the messages in `config.yml`:

```yaml
messages:
  admin-reset-success: '&aSuccessfully reset rental for &e{region}&a. Player &e{player}&a has been refunded &e{amount}&a.'
  rental-reset-refund: '&aYour rental of &e{region}&a has been reset by an admin. You have been refunded &e{amount}&a.'
```

## Testing Checklist

- [ ] Admin can reset a rental with `/rrreset <region>`
- [ ] Player receives full refund of `totalPaid` amount
- [ ] Online player receives notification message
- [ ] Admin receives success message with refund details
- [ ] Server logs record the admin action
- [ ] Region is properly cleaned up (WorldGuard members, blocks restored, items stored)
- [ ] Sign updates to "AVAILABLE" status
- [ ] Economy integration works correctly (Vault)
- [ ] Custom messages in config.yml are applied correctly

## Backward Compatibility

The old `resetRental()` method is deprecated but still functional. It will:
- Continue to work for any code that calls it
- Show a deprecation warning in IDE
- Not issue refunds (old behavior)

Recommended to update any custom code to use `resetRentalWithRefund()` instead.

## Security Considerations

- Only users with `regionrental.admin.reset` permission can use this command
- All actions are logged to server logs for audit trail
- Refunds are only issued if economy system (Vault) is available
- Refund amount is based on stored data, not user input (prevents exploits)

## Future Enhancements

Potential future improvements:
- Optional partial refund percentage (configurable in config.yml)
- Configurable refund policies per region
- Reason parameter for admin to specify why rental was reset
- Include reset reason in player notification
- Admin confirmation prompt for large refunds
