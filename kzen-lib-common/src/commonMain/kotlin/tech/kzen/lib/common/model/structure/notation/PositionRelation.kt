package tech.kzen.lib.common.model.structure.notation


data class PositionRelation(
    val relativeIndex: Int,
    val direction: Direction
) {
    //-----------------------------------------------------------------------------------------------------------------
    enum class Direction {
        At, After;

        fun offset(): Int {
            return when (this) {
                At -> 0
                After -> 1
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("MemberVisibilityCanBePrivate", "unused")
    companion object {
        private const val afterSuffix = "a"

        val first = at(0)
        val last = fromEnd(0)
        val afterLast = afterFromEnd(0)

        fun at(index: Int): PositionRelation {
            require(index >= 0) { "Can't be negative: $index" }
            return PositionRelation(index, Direction.At)
        }

        fun after(index: Int): PositionRelation {
            require(index >= 0) { "Can't be negative: $index" }
            return PositionRelation(index, Direction.After)
        }

        fun fromEnd(index: Int): PositionRelation {
            require(index >= 0) { "Can't be negative: $index" }
            return PositionRelation(-(index + 1), Direction.At)
        }

        fun afterFromEnd(index: Int): PositionRelation {
            require(index >= 0) { "Can't be negative: $index" }
            return PositionRelation(-(index + 1), Direction.After)
        }


        fun parse(asString: String): PositionRelation {
            val isAfter = asString.endsWith(afterSuffix)

            val direction =
                if (isAfter) {
                    Direction.After
                }
                else {
                    Direction.At
                }

            val indexAsString =
                if (isAfter) {
                    asString.substring(0, asString.length - 1)
                }
                else {
                    asString
                }

            val relativeIndex = indexAsString.toInt()

            return PositionRelation(relativeIndex, direction)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun resolve(containerSize: Int): PositionIndex {
        require(containerSize >= 0) { "Container size can't be negative: $containerSize" }

        if (relativeIndex == -1 && containerSize == 0) {
            return PositionIndex.zero
        }

        val absolute = when {
            relativeIndex >= 0 ->
                relativeIndex

            else ->
                containerSize + relativeIndex
        }

        require(absolute >= 0) { "Too far from end of ${asString()}: $absolute in $containerSize" }

        val resolved = absolute + direction.offset()

        require(resolved <= containerSize) {
            "Resolved ${asString()} to $resolved doesn't fit in $containerSize"
        }

        return PositionIndex(resolved)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        val suffix =
            if (direction == Direction.At) {
                ""
            }
            else {
                afterSuffix
            }

        return "$relativeIndex$suffix"
    }
}