package org.litvin

/**
 * Small, UI-agnostic helper for paginating a list.
 */
object Pagination {
    data class Result(
        val totalPages: Int,
        val currentPage: Int, // clamped to valid range (0-based); 0 when no items
        val fromIndex: Int,   // inclusive
        val toIndex: Int      // exclusive
    )

    /**
     * Computes pagination metrics for a given totalItems, requestedPage (0-based), and pageSize.
     * - If pageSize <= 0, uses 1.
     * - If totalItems <= 0, returns totalPages=0, currentPage=0, from=0, to=0.
     */
    fun compute(totalItems: Int, requestedPage: Int, pageSize: Int): Result {
        val ps = if (pageSize <= 0) 1 else pageSize
        if (totalItems <= 0) {
            return Result(totalPages = 0, currentPage = 0, fromIndex = 0, toIndex = 0)
        }
        val totalPages = ((totalItems + ps - 1) / ps)
        val clampedPage = when {
            requestedPage < 0 -> 0
            requestedPage >= totalPages -> totalPages - 1
            else -> requestedPage
        }
        val from = clampedPage * ps
        val to = kotlin.math.min(from + ps, totalItems)
        return Result(totalPages = totalPages, currentPage = clampedPage, fromIndex = from, toIndex = to)
    }
}
