package com.nakamura196.kotenocr.ocr

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Reading order processor based on NDL's block_xy_cut algorithm.
 * Reference: https://github.com/ndl-lab/ndlkotenocr-lite/blob/master/src/reading_order/xy_cut/block_xy_cut.py
 *
 * Line-by-line port of iOS `ReadingOrder.swift` (v2.1). Keep the two in step: an earlier port used a
 * different split rule (2-way split, splits at non-empty minima, IoU = inter / box area, fixed grid 200),
 * which put a column that starts higher than its neighbours first on a wide page.
 */
class ReadingOrderProcessor {

    fun process(detections: List<Detection>, imageWidth: Int, imageHeight: Int): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val bboxes = Array(detections.size) { i ->
            val b = detections[i].box
            floatArrayOf(b[0].toFloat(), b[1].toFloat(), b[2].toFloat(), b[3].toFloat())
        }
        val ranks = solve(bboxes)

        // Ranks are unique, so the sort order is fully determined.
        return detections.indices.sortedBy { ranks[it] }.map { detections[it] }
    }

    // Block node (partition tree)

    private class BlockNode(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val children = mutableListOf<BlockNode>()
        var lineIdx = mutableListOf<Int>()
        var numLines = 0
        var numVerticalLines = 0

        /** True if all children share the same y-range (split was along x-axis). */
        fun isXSplit(): Boolean = children.all { it.y0 == y0 && it.y1 == y1 }

        /** True if majority of lines are vertical (w < h). */
        fun isVertical(): Boolean = numLines < numVerticalLines * 2
    }

    private fun solve(bboxes: Array<FloatArray>): IntArray {
        val n = bboxes.size
        if (n == 0) return IntArray(0)

        val grid = (100.0 * sqrt(n.toDouble())).toInt()

        normalizeBboxes(bboxes, grid)
        val intBoxes = Array(n) { i -> IntArray(4) { k -> max(0, bboxes[i][k].toInt()) } }

        val table = makeMeshTable(intBoxes)
        val h = table.size
        val w = if (h > 0) table[0].size else 0
        if (w <= 0 || h <= 0) return IntArray(n) { it }

        val root = BlockNode(0, 0, w, h)
        blockXYCut(table, root)
        assignBboxToNode(root, intBoxes)
        sortNodes(root, intBoxes)

        val ranks = IntArray(n) { -1 }
        getRanking(root, ranks, 0)

        var maxRank = ranks.maxOrNull() ?: -1
        for (i in 0 until n) {
            if (ranks[i] < 0) {
                maxRank += 1
                ranks[i] = maxRank
            }
        }
        return ranks
    }

    private fun normalizeBboxes(bboxes: Array<FloatArray>, grid: Int) {
        for (b in bboxes) {
            if (b[0] > b[2]) b[2] = b[0]
            if (b[1] > b[3]) b[3] = b[1]
        }
        if (bboxes.isEmpty()) return
        val xMin = bboxes.minOf { it[0] }
        val yMin = bboxes.minOf { it[1] }
        val xMax = bboxes.maxOf { it[2] }
        val yMax = bboxes.maxOf { it[3] }
        val wPage = xMax - xMin
        val hPage = yMax - yMin
        if (!(wPage > 0 && hPage > 0)) {
            // Degenerate page: scale both axes uniformly onto the grid.
            val extent = maxOf(wPage, hPage, 1f)
            val s = grid.toFloat() / extent
            for (b in bboxes) {
                b[0] = max(0f, floor((b[0] - xMin) * s))
                b[1] = max(0f, floor((b[1] - yMin) * s))
                b[2] = max(0f, floor((b[2] - xMin) * s))
                b[3] = max(0f, floor((b[3] - yMin) * s))
            }
            return
        }

        // The long axis gets grid × aspect cells, capped so one very flat or thin box cannot blow up the table.
        val maxAspect = 20f
        val xGrid = if (wPage < hPage) grid.toFloat() else grid.toFloat() * min(maxAspect, wPage / hPage)
        val yGrid = if (hPage < wPage) grid.toFloat() else grid.toFloat() * min(maxAspect, hPage / wPage)

        for (b in bboxes) {
            b[0] = max(0f, floor((b[0] - xMin) * xGrid / wPage))
            b[1] = max(0f, floor((b[1] - yMin) * yGrid / hPage))
            b[2] = max(0f, floor((b[2] - xMin) * xGrid / wPage))
            b[3] = max(0f, floor((b[3] - yMin) * yGrid / hPage))
        }
    }

    private fun makeMeshTable(bboxes: Array<IntArray>): Array<IntArray> {
        val xGrid = (bboxes.maxOfOrNull { it[2] } ?: 0) + 1
        val yGrid = (bboxes.maxOfOrNull { it[3] } ?: 0) + 1
        val table = Array(yGrid) { IntArray(xGrid) }
        for (b in bboxes) {
            for (y in b[1] until min(b[3], yGrid)) {
                for (x in b[0] until min(b[2], xGrid)) {
                    table[y][x] = 1
                }
            }
        }
        return table
    }

    private fun calcHist(table: Array<IntArray>, x0: Int, y0: Int, x1: Int, y1: Int): Pair<IntArray, IntArray> {
        val xHist = IntArray(x1 - x0)
        val yHist = IntArray(y1 - y0)
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val v = table[y][x]
                xHist[x - x0] += v
                yHist[y - y0] += v
            }
        }
        return Pair(xHist, yHist)
    }

    private data class Span(val start: Int, val end: Int, val value: Float)

    /**
     * Longest run of the minimum value. Score = -minVal / maxVal (closer to 0 = better gap).
     */
    private fun calcMinSpan(hist: IntArray): Span {
        if (hist.size <= 1) return Span(0, max(1, hist.size), 0f)
        val minVal = hist.min()
        val maxVal = hist.max()

        val runs = mutableListOf<Pair<Int, Int>>()
        var inRun = false
        var runStart = 0
        for (i in hist.indices) {
            if (hist[i] == minVal) {
                if (!inRun) { runStart = i; inRun = true }
            } else if (inRun) {
                runs.add(Pair(runStart, i)); inRun = false
            }
        }
        if (inRun) runs.add(Pair(runStart, hist.size))
        if (runs.isEmpty()) return Span(0, hist.size, 0f)

        // Swift `max(by:)` keeps the FIRST of equal maxima (it replaces only on strictly longer); match that.
        var best = runs[0]
        for (r in runs) {
            if ((r.second - r.first) > (best.second - best.first)) best = r
        }
        val v = if (maxVal > 0) -minVal.toFloat() / maxVal.toFloat() else 0f
        return Span(best.first, best.second, v)
    }

    private fun blockXYCut(table: Array<IntArray>, node: BlockNode) {
        val x0 = node.x0; val y0 = node.y0; val x1 = node.x1; val y1 = node.y1
        if (!(x1 > x0 && y1 > y0)) return

        val (xHist, yHist) = calcHist(table, x0, y0, x1, y1)
        val xs = calcMinSpan(xHist)
        val ys = calcMinSpan(yHist)
        val xBeg = xs.start + x0; val xEnd = xs.end + x0
        val yBeg = ys.start + y0; val yEnd = ys.end + y0

        // No split possible
        if (x0 == xBeg && x1 == xEnd && y0 == yBeg && y1 == yEnd) return

        when {
            ys.value < xs.value -> splitX(node, table, xBeg, xEnd)
            xs.value < ys.value -> splitY(node, table, yBeg, yEnd)
            (xEnd - xBeg) < (yEnd - yBeg) -> splitY(node, table, yBeg, yEnd)
            else -> splitX(node, table, xBeg, xEnd)
        }
    }

    // 3-way split: before the gap, the gap itself, after the gap.

    private fun splitX(parent: BlockNode, table: Array<IntArray>, xBeg: Int, xEnd: Int) {
        addChild(parent, table, null, null, xBeg, null)
        addChild(parent, table, xBeg, null, xEnd, null)
        addChild(parent, table, xEnd, null, null, null)
    }

    private fun splitY(parent: BlockNode, table: Array<IntArray>, yBeg: Int, yEnd: Int) {
        addChild(parent, table, null, null, null, yBeg)
        addChild(parent, table, null, yBeg, null, yEnd)
        addChild(parent, table, null, yEnd, null, null)
    }

    private fun addChild(parent: BlockNode, table: Array<IntArray>, x0: Int?, y0: Int?, x1: Int?, y1: Int?) {
        val cx0 = x0 ?: parent.x0
        val cy0 = y0 ?: parent.y0
        val cx1 = x1 ?: parent.x1
        val cy1 = y1 ?: parent.y1
        if (!(cx0 < cx1 && cy0 < cy1)) return
        if (cx0 == parent.x0 && cy0 == parent.y0 && cx1 == parent.x1 && cy1 == parent.y1) return

        val child = BlockNode(cx0, cy0, cx1, cy1)
        parent.children.add(child)
        blockXYCut(table, child)
    }

    // Assign bboxes to leaf nodes by IoU

    private fun assignBboxToNode(root: BlockNode, bboxes: Array<IntArray>) {
        val leaves = mutableListOf<BlockNode>()
        collectLeaves(root, leaves)
        if (leaves.isEmpty()) return
        val leafBoxes = leaves.map { intArrayOf(it.x0, it.y0, it.x1, it.y1) }

        for ((i, bbox) in bboxes.withIndex()) {
            var bestIdx = 0
            var bestIoU = Float.NEGATIVE_INFINITY
            for ((j, leafBox) in leafBoxes.withIndex()) {
                val iou = calcIoU(bbox, leafBox)
                if (iou > bestIoU) {
                    bestIoU = iou
                    bestIdx = j
                }
            }
            leaves[bestIdx].lineIdx.add(i)
        }
    }

    private fun collectLeaves(node: BlockNode, leaves: MutableList<BlockNode>) {
        if (node.children.isEmpty()) leaves.add(node) else node.children.forEach { collectLeaves(it, leaves) }
    }

    private fun calcIoU(a: IntArray, b: IntArray): Float {
        val ix0 = max(a[0], b[0])
        val iy0 = max(a[1], b[1])
        val ix1 = min(a[2], b[2])
        val iy1 = min(a[3], b[3])
        val inter = max(0, ix1 - ix0).toFloat() * max(0, iy1 - iy0).toFloat()
        if (inter == 0f) return 0f
        val aArea = ((a[2] - a[0]) * (a[3] - a[1])).toFloat()
        val bArea = ((b[2] - b[0]) * (b[3] - b[1])).toFloat()
        val union = aArea + bArea - inter
        return if (union > 0) inter / union else 0f
    }

    // Sort nodes (reading order per subtree)

    private fun sortNodes(node: BlockNode, bboxes: Array<IntArray>): Pair<Int, Int> {
        if (node.lineIdx.isNotEmpty()) {
            var numVert = 0
            for (i in node.lineIdx) {
                val w = bboxes[i][2] - bboxes[i][0]
                val h = bboxes[i][3] - bboxes[i][1]
                if (w < h) numVert++
            }
            node.numLines = node.lineIdx.size
            node.numVerticalLines = numVert
            if (node.numLines > 1) {
                node.lineIdx = sortLeafLines(node.lineIdx, bboxes, node.isVertical())
            }
        } else {
            for (child in node.children) {
                val (num, vNum) = sortNodes(child, bboxes)
                node.numLines += num
                node.numVerticalLines += vNum
            }
            // Vertical content split along x: reverse to read right to left.
            if (node.isXSplit() && node.isVertical()) node.children.reverse()
        }
        return Pair(node.numLines, node.numVerticalLines)
    }

    /**
     * Orders the lines of one leaf (as NDLOCR-Lite's sort_lines_local): lines whose centres lie within
     * 0.3 × the median line width (height, for horizontal text) form one column, read top to bottom;
     * columns go right to left. Horizontal text: rows top to bottom, each left to right.
     */
    private fun sortLeafLines(lines: List<Int>, bboxes: Array<IntArray>, vertical: Boolean): MutableList<Int> {
        fun across(i: Int): Int = if (vertical) bboxes[i][0] + bboxes[i][2] else bboxes[i][1] + bboxes[i][3]
        fun along(i: Int): Int = if (vertical) bboxes[i][1] + bboxes[i][3] else bboxes[i][0] + bboxes[i][2]
        val spans = lines.map { if (vertical) bboxes[it][2] - bboxes[it][0] else bboxes[it][3] - bboxes[it][1] }.sorted()
        // Centres are kept doubled (x0 + x1), so the margin is doubled too.
        val margin = spans[spans.size / 2].toDouble() * 0.3 * 2

        val byAcross = lines.sortedWith { a, b ->
            if (vertical) across(b).compareTo(across(a)) else across(a).compareTo(across(b))
        }
        val groups = mutableListOf<MutableList<Int>>()
        var previous = 0
        for (i in byAcross) {
            if (groups.isNotEmpty() && abs(across(i) - previous).toDouble() <= margin) {
                groups.last().add(i)
            } else {
                groups.add(mutableListOf(i))
            }
            previous = across(i)
        }
        val out = mutableListOf<Int>()
        for (group in groups) {
            out.addAll(group.sortedWith { a, b ->
                if (along(a) != along(b)) along(a).compareTo(along(b))
                else if (vertical) across(b).compareTo(across(a)) else across(a).compareTo(across(b))
            })
        }
        return out
    }

    // Ranking (depth-first traversal)

    private fun getRanking(node: BlockNode, ranks: IntArray, rank: Int): Int {
        var r = rank
        for (i in node.lineIdx) {
            ranks[i] = r
            r += 1
        }
        for (child in node.children) r = getRanking(child, ranks, r)
        return r
    }
}
