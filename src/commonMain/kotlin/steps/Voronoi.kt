package steps

import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.color.Colors
import com.soywiz.korim.color.RGBA
import components.Cell
import components.CellType
import components.MatrixMap
import components.Zone
import kotlin.math.hypot
import kotlin.math.roundToInt

class Voronoi(private val zones: List<Zone>, private val matrixLength: Int) {
    val matrixMap = initMatrixMap()

    init {
        assignEdges()
        //balanceZones()

    }

    /**
     * add passages at the edge of connected zones
     */
    fun createPassages() {
        val resolvedConnections = mutableListOf<Pair<Zone, Zone>>()
        // lists of cells with only one adjacent zone
        val goodCandidates = hashMapOf<Pair<Zone, Zone>, MutableList<Cell>>()
        // lists with many adjacent zones
        val badCandidates = hashMapOf<Pair<Zone, Zone>, MutableList<Cell>>()

        for (list in matrixMap.matrix) {
            for (cell in list) {
                if (cell.adjacentEdges.isNotEmpty()) {
                    // a good candidate
                    if (cell.adjacentEdges.all {
                            it.zone == cell.adjacentEdges[0].zone
                                    && it.adjacentEdges.all { i -> i.zone == cell.zone }
                        })
                        addCandidate(cell, cell.adjacentEdges[0], goodCandidates)
                    // bad candidate
                    else
                        for (c in cell.adjacentEdges)
                            addCandidate(cell, c, badCandidates)
                }
            }
        }

        for (conn in goodCandidates.keys)
            resolveOnePassage(conn, goodCandidates, resolvedConnections)

        for (conn in badCandidates.keys)
            if (!resolvedConnections.contains(conn))
                resolveOnePassage(conn, badCandidates, resolvedConnections)
    }

    /**
     * add Pair(cell.zone, compared.zone) to candidates map in order of zone indexes
     */
    private fun addCandidate(cell: Cell, compared: Cell, candidates: MutableMap<Pair<Zone, Zone>, MutableList<Cell>>) {
        if (cell.zone.getNullableConnection(compared.zone) != null) {
            val first = if (cell.zone.index > compared.zone.index)
                compared.zone else cell.zone
            val second = if (first == cell.zone) cell.adjacentEdges[0].zone else cell.zone
            if (candidates[Pair(first, second)] == null)
                candidates[Pair(first, second)] = mutableListOf()
            candidates[Pair(first, second)]!!.add(cell)
        }
    }

    /**
     * add passage to resolvedConnections, and choose random cell from candidates
     * that will be a passage
     */
    private fun resolveOnePassage(
        pass: Pair<Zone, Zone>,
        candidates: Map<Pair<Zone, Zone>, MutableList<Cell>>,
        resolvedConnections: MutableList<Pair<Zone, Zone>>
    ) {
        resolvedConnections.add(pass)
        if (candidates[pass]!!.size > 2) {
            candidates[pass]!!.removeAt(0)
            candidates[pass]!!.removeAt(candidates[pass]!!.lastIndex)
        }
        var chosenCell = candidates[pass]!![(0..candidates[pass]!!.lastIndex).random()]
        var iter = 0
        // make sure that passages are not near
        while (chosenCell.getNeighbors().any { it.cellType == CellType.ROAD } && iter < 50) {
            chosenCell = candidates[pass]!![(0..candidates[pass]!!.lastIndex).random()]
            iter++
        }
        val neighborOfChosen = chosenCell.adjacentEdges[(0..chosenCell.adjacentEdges.lastIndex).random()]

        chosenCell.cellType = CellType.ROAD
        neighborOfChosen.cellType = CellType.ROAD

        for (cell in chosenCell.getAllNeighbors()) {

            if (Constants.OBSTACLES.contains(cell.cellType))
                cell.cellType = CellType.EDGE
        }
        for (cell in neighborOfChosen.getAllNeighbors()) {

            if (Constants.OBSTACLES.contains(cell.cellType))
                cell.cellType = CellType.EDGE
        }
        // connect
        chosenCell.getOpposite(neighborOfChosen).cellType = CellType.EMPTY
        neighborOfChosen.getOpposite(chosenCell).cellType = CellType.EMPTY
    }

    /**
     * used for making zone sizes to be as expected
     */
    fun balanceZones() {

    }

    /**
     * assign edge fields of cells in the matrix
     */
    private fun assignEdges() {
        for (list in matrixMap.matrix)
            for (cell in list)
                cell.isAtEdge()

        for (list in matrixMap.matrix)
            for (cell in list)
                cell.adjacentEdges = cell.getEdge()
    }

    /**
     * for debugging mainly
     * yellow - centers of zones
     * blue - roads, passages
     * gray - mines, castles
     */
    fun visualizeMatrix(): Bitmap32 {
        val res = Bitmap32(matrixLength, matrixLength)

        for (x in 0 until matrixLength)
            for (y in 0 until matrixLength) {

                if (matrixMap.matrix[x][y].cellType == CellType.ROAD)
                    res[x, y] = Colors.BLUE
                else if (Constants.OBSTACLES.contains(matrixMap.matrix[x][y].cellType)) {
                    res[x, y] = Colors[matrixMap.matrix[x][y].zone.type.color].minus(RGBA(0x2A2A2A))
                } else res[x, y] = Colors[matrixMap.matrix[x][y].zone.type.color]
            }
        for (c in matrixMap.zones) {
            res[c.center.first, c.center.second] = Colors.YELLOW
        }
        return res
    }

    private fun initMatrixMap(): MatrixMap {
        val bounds = findProperBounds()

        assignCenters(bounds, zones, matrixLength)

        val res = buildMatrix(zones, matrixLength)

        // init matrix field in cells
        for (list in res.matrix)
            for (cell in list)
                cell.matrix = res

        return res
    }

    /**
     * bounds that
     * 1) have equal height and width
     * 2) and each zone center should be at least one cell away from the edge
     * 3) left and top bounds have 0 coordinate
     */
    private fun findProperBounds(): List<Double> {
        // x min, x max, y min, y max
        val bounds = mutableListOf(50.0, 50.0, 50.0, 50.0)
        for (z in zones) {
            if (z.circle.pos.x < bounds[0])
                bounds[0] = z.circle.pos.x
            if (z.circle.pos.x > bounds[1])
                bounds[1] = z.circle.pos.x
            if (z.circle.pos.y < bounds[2])
                bounds[2] = z.circle.pos.y
            if (z.circle.pos.y > bounds[3])
                bounds[3] = z.circle.pos.y
        }

        makeBoundsSquare(bounds)

        // make centers not on edge
        normalizeBounds(bounds, matrixLength)

        return bounds
    }

    private fun makeBoundsSquare(bounds: MutableList<Double>) {
        val width = bounds[1] - bounds[0]
        val height = bounds[3] - bounds[2]

        if (width > height) {
            bounds[2] -= (width - height) / 2
            bounds[3] += (width - height) / 2
        } else {
            bounds[0] -= (-width + height) / 2
            bounds[1] += (-width + height) / 2
        }
    }

    /**
     * make zone centers not on edge and bound left and right edge as 0
     * matrixLength - amount of cells in a matrix row
     */
    private fun normalizeBounds(bounds: MutableList<Double>, matrixLength: Int) {
        // save 2 cells for the edges
        val oneCell = (bounds[1] - bounds[0]) / (matrixLength - 2)

        for (i in 0..bounds.lastIndex)
            if (i % 2 == 0)
                bounds[i] -= oneCell
            else
                bounds[i] += oneCell
    }

    private fun assignCenters(bounds: List<Double>, zones: List<Zone>, matrixLength: Int) {
        zones.forEach {
            val x = (it.circle.pos.x - bounds[0]) / (bounds[1] - bounds[0]) * matrixLength
            val y = (it.circle.pos.y - bounds[2]) / (bounds[3] - bounds[2]) * matrixLength

            it.center = Pair(x.roundToInt(), y.roundToInt())
        }
    }

    private fun buildMatrix(
        zones: List<Zone>,
        matrixLength: Int
    ): MatrixMap {

        val matrix = List<MutableList<Cell>>(matrixLength) { mutableListOf() }
        for (i in 0 until matrixLength) {
            for (j in 0 until matrixLength) {
                val zone = findNearestZoneCenter(Pair(i, j), zones)
                matrix[i].add(Cell(Pair(i, j), zone))
            }
        }

        return MatrixMap(matrix, zones)
    }

    private fun findNearestZoneCenter(cell: Pair<Int, Int>, zones: List<Zone>): Zone {
        var nearest = zones[0]
        var smallest = Double.MAX_VALUE
        for (i in 0..zones.lastIndex) {
            if (cell.distance(zones[i].center) < smallest) {
                smallest = cell.distance(zones[i].center)
                nearest = zones[i]
            }
        }
        return nearest
    }

    private fun Pair<Int, Int>.distance(other: Pair<Int, Int>): Double {
        return hypot((first - other.first).toDouble(), (second - other.second).toDouble())
    }
}