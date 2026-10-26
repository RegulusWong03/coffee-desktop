package com.coffee.pet.android.playback

import com.coffee.pet.android.R

object FrameCatalog {
    const val WIDTH = 192
    const val HEIGHT = 208
    const val DEFAULT_INTERVAL_MS = 170
    val counts = intArrayOf(6, 8, 8, 4, 5, 8, 6, 6, 6, 8, 8)
    val actionRows = 1..8
    private val rows = listOf(
        listOf(R.drawable.r00_c00, R.drawable.r00_c01, R.drawable.r00_c02, R.drawable.r00_c03, R.drawable.r00_c04, R.drawable.r00_c05),
        listOf(R.drawable.r01_c00, R.drawable.r01_c01, R.drawable.r01_c02, R.drawable.r01_c03, R.drawable.r01_c04, R.drawable.r01_c05, R.drawable.r01_c06, R.drawable.r01_c07),
        listOf(R.drawable.r02_c00, R.drawable.r02_c01, R.drawable.r02_c02, R.drawable.r02_c03, R.drawable.r02_c04, R.drawable.r02_c05, R.drawable.r02_c06, R.drawable.r02_c07),
        listOf(R.drawable.r03_c00, R.drawable.r03_c01, R.drawable.r03_c02, R.drawable.r03_c03),
        listOf(R.drawable.r04_c00, R.drawable.r04_c01, R.drawable.r04_c02, R.drawable.r04_c03, R.drawable.r04_c04),
        listOf(R.drawable.r05_c00, R.drawable.r05_c01, R.drawable.r05_c02, R.drawable.r05_c03, R.drawable.r05_c04, R.drawable.r05_c05, R.drawable.r05_c06, R.drawable.r05_c07),
        listOf(R.drawable.r06_c00, R.drawable.r06_c01, R.drawable.r06_c02, R.drawable.r06_c03, R.drawable.r06_c04, R.drawable.r06_c05),
        listOf(R.drawable.r07_c00, R.drawable.r07_c01, R.drawable.r07_c02, R.drawable.r07_c03, R.drawable.r07_c04, R.drawable.r07_c05),
        listOf(R.drawable.r08_c00, R.drawable.r08_c01, R.drawable.r08_c02, R.drawable.r08_c03, R.drawable.r08_c04, R.drawable.r08_c05),
        listOf(R.drawable.r09_c00, R.drawable.r09_c01, R.drawable.r09_c02, R.drawable.r09_c03, R.drawable.r09_c04, R.drawable.r09_c05, R.drawable.r09_c06, R.drawable.r09_c07),
        listOf(R.drawable.r10_c00, R.drawable.r10_c01, R.drawable.r10_c02, R.drawable.r10_c03, R.drawable.r10_c04, R.drawable.r10_c05, R.drawable.r10_c06, R.drawable.r10_c07),
    )
    fun frame(row: Int, col: Int): Int = rows[row][col]
    fun frameCount(row: Int): Int = rows[row].size
}
