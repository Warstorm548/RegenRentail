package com.zonerental.models

import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Represents a GUI session for item retrieval from expired rentals.
 * Replaces StorageManager.StorageGUISession inner class.
 */
data class StorageGUISession(
    val playerUUID: UUID,
    val items: MutableList<ItemStack>,
    var currentPage: Int = 0
) {
    /**
     * Items per page in the GUI (45 slots, leaving room for navigation).
     */
    companion object {
        const val ITEMS_PER_PAGE = 45

        /**
         * Creates a new session with a copy of the items list.
         */
        @JvmStatic
        fun create(playerUUID: UUID, items: List<ItemStack>): StorageGUISession =
            StorageGUISession(playerUUID, items.toMutableList())
    }

    /**
     * Gets the total number of pages needed to display all items.
     */
    val totalPages: Int
        get() = maxOf(1, (items.size - 1) / ITEMS_PER_PAGE + 1)

    /**
     * Checks if there is a next page available.
     */
    val hasNextPage: Boolean
        get() = currentPage < totalPages - 1

    /**
     * Checks if there is a previous page available.
     */
    val hasPreviousPage: Boolean
        get() = currentPage > 0

    /**
     * Gets the items for the current page.
     */
    val currentPageItems: List<ItemStack>
        get() {
            val startIndex = currentPage * ITEMS_PER_PAGE
            val endIndex = minOf(startIndex + ITEMS_PER_PAGE, items.size)
            return if (startIndex < items.size) {
                items.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
        }

    /**
     * Advances to the next page if available.
     * Returns true if the page changed.
     */
    fun nextPage(): Boolean {
        return if (hasNextPage) {
            currentPage++
            true
        } else false
    }

    /**
     * Goes back to the previous page if available.
     * Returns true if the page changed.
     */
    fun previousPage(): Boolean {
        return if (hasPreviousPage) {
            currentPage--
            true
        } else false
    }

    /**
     * Removes an item from the session.
     * Returns true if the item was found and removed.
     */
    fun removeItem(item: ItemStack): Boolean = items.remove(item)

    /**
     * Checks if the session still has items.
     */
    val hasItems: Boolean
        get() = items.isNotEmpty()

    /**
     * Gets the total number of items remaining.
     */
    val itemCount: Int
        get() = items.size
}
