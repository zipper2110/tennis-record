package org.litvin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaginationTest {

    @Test
    fun `empty list returns zero pages and zero range`() {
        val r = Pagination.compute(totalItems = 0, requestedPage = 0, pageSize = 12)
        assertEquals(0, r.totalPages)
        assertEquals(0, r.currentPage)
        assertEquals(0, r.fromIndex)
        assertEquals(0, r.toIndex)
    }

    @Test
    fun `below page size yields single page`() {
        val r = Pagination.compute(totalItems = 11, requestedPage = 0, pageSize = 12)
        assertEquals(1, r.totalPages)
        assertEquals(0, r.currentPage)
        assertEquals(0, r.fromIndex)
        assertEquals(11, r.toIndex)
    }

    @Test
    fun `exact multiple of page size gives correct pages and ranges`() {
        val r1 = Pagination.compute(totalItems = 12, requestedPage = 0, pageSize = 12)
        assertEquals(1, r1.totalPages)
        assertEquals(0, r1.currentPage)
        assertEquals(0, r1.fromIndex)
        assertEquals(12, r1.toIndex)

        val r2 = Pagination.compute(totalItems = 24, requestedPage = 1, pageSize = 12)
        assertEquals(2, r2.totalPages)
        assertEquals(1, r2.currentPage)
        assertEquals(12, r2.fromIndex)
        assertEquals(24, r2.toIndex)
    }

    @Test
    fun `non-multiple last page smaller`() {
        val r0 = Pagination.compute(totalItems = 25, requestedPage = 0, pageSize = 12)
        assertEquals(3, r0.totalPages)
        assertEquals(0, r0.currentPage)
        assertEquals(0, r0.fromIndex)
        assertEquals(12, r0.toIndex)

        val r1 = Pagination.compute(totalItems = 25, requestedPage = 1, pageSize = 12)
        assertEquals(3, r1.totalPages)
        assertEquals(1, r1.currentPage)
        assertEquals(12, r1.fromIndex)
        assertEquals(24, r1.toIndex)

        val r2 = Pagination.compute(totalItems = 25, requestedPage = 2, pageSize = 12)
        assertEquals(3, r2.totalPages)
        assertEquals(2, r2.currentPage)
        assertEquals(24, r2.fromIndex)
        assertEquals(25, r2.toIndex)
    }

    @Test
    fun `requested page clamped to bounds`() {
        val low = Pagination.compute(totalItems = 10, requestedPage = -5, pageSize = 4)
        assertEquals(3, low.totalPages)
        assertEquals(0, low.currentPage)
        assertEquals(0, low.fromIndex)
        assertEquals(4, low.toIndex)

        val high = Pagination.compute(totalItems = 10, requestedPage = 99, pageSize = 4)
        assertEquals(3, high.totalPages)
        assertEquals(2, high.currentPage)
        assertEquals(8, high.fromIndex)
        assertEquals(10, high.toIndex)
    }

    @Test
    fun `page size zero or negative behaves as one item per page`() {
        val zero = Pagination.compute(totalItems = 3, requestedPage = 1, pageSize = 0)
        assertEquals(3, zero.totalPages)
        assertEquals(1, zero.currentPage)
        assertEquals(1, zero.fromIndex)
        assertEquals(2, zero.toIndex)

        val negative = Pagination.compute(totalItems = 3, requestedPage = 5, pageSize = -7)
        assertEquals(3, negative.totalPages)
        assertEquals(2, negative.currentPage)
        assertEquals(2, negative.fromIndex)
        assertEquals(3, negative.toIndex)
    }

    @Test
    fun `three items are shown on a single first page`() {
        val r = Pagination.compute(totalItems = 3, requestedPage = 0, pageSize = 12)
        assertEquals(1, r.totalPages)
        assertEquals(0, r.currentPage)
        assertEquals(0, r.fromIndex)
        assertEquals(3, r.toIndex)
    }
}
