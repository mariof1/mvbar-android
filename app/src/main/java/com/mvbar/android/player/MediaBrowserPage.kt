package com.mvbar.android.player

internal fun <T> mediaBrowserPage(items: List<T>, page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return emptyList()
    val start = page.toLong() * pageSize
    if (start >= items.size) return emptyList()
    val end = (start + pageSize).coerceAtMost(items.size.toLong())
    return items.subList(start.toInt(), end.toInt())
}
