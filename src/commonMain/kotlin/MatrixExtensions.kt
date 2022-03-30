import com.soywiz.kds.Queue
import com.soywiz.kds.TGenPriorityQueue
import components.Cell
import components.CellType

object MatrixExtensions {
    fun getCellsAtRadius(
        r: Int,
        cell: Cell,
        filtered: List<CellType> = mutableListOf(CellType.EDGE, CellType.BUILDING, CellType.ROAD)
    ): List<Cell> {
        val queue = Queue<Cell>()
        val resolved = mutableSetOf<Cell>()
        var radius = 0
        var beforeIncreasingRadius = 1
        queue.enqueue(cell)
        while (queue.isNotEmpty() && radius < r) {
            beforeIncreasingRadius--
            val current = queue.dequeue()
            resolved.add(current)
            for (neighbor in current.getNeighbors()) {
                if (!queue.any { it == neighbor } && !resolved.contains(neighbor) && !filtered.contains(neighbor.cellType))
                    queue.enqueue(neighbor)
            }
            if (beforeIncreasingRadius == 0) {
                radius++
                beforeIncreasingRadius = queue.size
            }
        }
        return queue.toList()
    }

    fun getMaxMinRadius(
        cell: Cell,
        filtered: List<CellType> = mutableListOf(CellType.EDGE, CellType.BUILDING, CellType.ROAD)
    ): Pair<Int, Int> {
        var min = -1
        val queue = Queue<Pair<Cell, Int>>()
        val resolved = mutableSetOf<Cell>()
        queue.enqueue(Pair(cell, 0))
        var last = queue.peek()!!.second
        while (queue.isNotEmpty()) {
            val (current, rad) = queue.dequeue()
            last = rad
            resolved.add(current)
            for (neighbor in current.getNeighbors()) {
                if (neighbor.cellType == CellType.EDGE && min == -1)
                    min = rad
                else if (!queue.any { it.first == neighbor } && !resolved.contains(neighbor) && !filtered.contains(
                        neighbor.cellType
                    ))
                    queue.enqueue(Pair(neighbor, rad + 1))
            }
        }

        return Pair(min, last)
    }

    fun getCellsInInterval(
        root: Cell,
        radiusMin: Int,
        radiusMax: Int,
        filtered: List<CellType> = mutableListOf(CellType.EDGE, CellType.BUILDING, CellType.ROAD)
    ): MutableList<Cell> {
        val res = mutableListOf<Cell>()
        val queue = Queue<Pair<Cell, Int>>()
        val resolved = mutableSetOf<Cell>()
        queue.enqueue(Pair(root, 0))

        while (queue.isNotEmpty()) {
            val (current, rad) = queue.dequeue()
            if (rad > radiusMax)
                return res
            if (rad >= radiusMin)
                res.add(current)
            else resolved.add(current)
            for (neighbor in current.getNeighbors())
                if (!queue.any { it.first == neighbor } && !res.contains(neighbor) && !resolved.contains(neighbor) && !filtered.contains(
                        neighbor.cellType
                    )
                )
                    queue.enqueue(Pair(neighbor, rad + 1))
        }

        return res
    }

    /**
     * diagonal costs not calculated
     * Source: https://www.redblobgames.com/pathfinding/a-star/introduction.html
     */
    fun calculateCosts(root: Cell): Boolean {
        val queue = TGenPriorityQueue<Pair<Int, Cell>>(
            comparator = Comparator { a, b -> a.first.compareTo(b.first) })
        val previous = mutableMapOf<Cell, Cell>()
        val cost = mutableMapOf<Cell, Int>()
        var emptyMinValue = Int.MAX_VALUE - Constants.SIDE_COST * 2
        var emptyMinCell: Cell = root.matrix.matrix[0, 0]

        queue.add(Pair(0, root))
        cost[root] = 0

        while (queue.isNotEmpty()) {
            val current = queue.removeHead()
            for (next in current.second.getNeighbors()) {
                if (Constants.SUPER_OBSTACLES.contains(next.cellType) || next.cellType == CellType.ROAD)
                    continue
                val costNext = cost[current.second]!! +
                        (if (Constants.OBSTACLES.contains(next.cellType))
                            Constants.SIDE_COST else Constants.EMPTY_COST)
                if (cost[next] == null || costNext < cost[next]!!) {
                    cost[next] = costNext
                    previous[next] = current.second

                    queue.add(Pair(costNext, next))

                    // cell in the next region that will be connected
                    if (Constants.EMPTY.contains(next.cellType) && costNext < emptyMinValue && costNext > 0) {
                        emptyMinValue = costNext
                        emptyMinCell = next
                    }
                }
            }
        }
        if (emptyMinCell == root.matrix.matrix[0, 0])
            return false

        var routeCell = previous[emptyMinCell]
        while (routeCell != root) {
            if (Constants.OBSTACLES.contains(routeCell!!.cellType))
                routeCell.cellType = CellType.EMPTY
            routeCell = previous[routeCell]
        }
        return true
    }

    fun makeRoads() {

    }

    fun makeEmptyConnections() {

    }
}