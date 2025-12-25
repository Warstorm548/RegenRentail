package com.zonerental.extensions

/**
 * Extension functions for Collection operations.
 */

/**
 * Returns the first element matching the predicate, or null if none found.
 * Alias for firstOrNull for readability.
 */
inline fun <T> Iterable<T>.findFirst(predicate: (T) -> Boolean): T? = firstOrNull(predicate)

/**
 * Returns a list containing only non-null results of applying the transform.
 * Same as mapNotNull but with a more descriptive name for certain contexts.
 */
inline fun <T, R : Any> Iterable<T>.mapNonNull(transform: (T) -> R?): List<R> = mapNotNull(transform)

/**
 * Filters and maps in one operation. Only includes elements where filter returns true,
 * then applies the transform.
 */
inline fun <T, R> Iterable<T>.filterMap(filter: (T) -> Boolean, transform: (T) -> R): List<R> =
    filter(filter).map(transform)

/**
 * Gets a value from a map, or computes and stores it if absent.
 * Thread-safe version using computeIfAbsent for ConcurrentHashMap compatibility.
 */
inline fun <K, V> MutableMap<K, V>.getOrCompute(key: K, defaultValue: () -> V): V =
    get(key) ?: defaultValue().also { put(key, it) }

/**
 * Removes entries from a mutable map that match the predicate.
 * Returns the number of entries removed.
 */
inline fun <K, V> MutableMap<K, V>.removeIf(predicate: (Map.Entry<K, V>) -> Boolean): Int {
    var count = 0
    val iterator = entries.iterator()
    while (iterator.hasNext()) {
        if (predicate(iterator.next())) {
            iterator.remove()
            count++
        }
    }
    return count
}

/**
 * Converts a Set to a comma-separated string.
 */
fun <T> Set<T>.toCommaSeparated(): String = joinToString(", ")

/**
 * Converts a Collection to a formatted list string.
 */
fun <T> Collection<T>.toFormattedList(
    prefix: String = "",
    separator: String = ", ",
    transform: (T) -> String = { it.toString() }
): String = joinToString(separator, prefix) { transform(it) }

/**
 * Checks if a collection contains any element matching the predicate.
 */
inline fun <T> Collection<T>.containsAny(predicate: (T) -> Boolean): Boolean = any(predicate)

/**
 * Returns true if the collection is not null and not empty.
 */
fun <T> Collection<T>?.isNotNullOrEmpty(): Boolean = this != null && this.isNotEmpty()

/**
 * Safely gets an element at index, or null if out of bounds.
 */
fun <T> List<T>.getOrNullSafe(index: Int): T? = if (index in indices) get(index) else null
