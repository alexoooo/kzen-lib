package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


class DataTypePath(segments: List<DataPathSegment> = emptyList()) {
    val segments: List<DataPathSegment> = segments.toList()

    fun child(segment: DataPathSegment): DataTypePath =
        DataTypePath(segments + segment)

    fun startsWith(prefix: DataTypePath): Boolean =
        segments.size >= prefix.segments.size &&
                segments.subList(0, prefix.segments.size) == prefix.segments

    fun removePrefix(prefix: DataTypePath): DataTypePath {
        if (!startsWith(prefix)) {
            throw DataException(DataProblem(
                DataProblem.invalidPath,
                "Path $this does not start with $prefix",
                segments))
        }
        return DataTypePath(segments.drop(prefix.segments.size))
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is DataTypePath && segments == other.segments

    override fun hashCode(): Int = segments.hashCode()

    override fun toString(): String =
        if (segments.isEmpty()) {
            "<root>"
        }
        else {
            segments.joinToString(separator = "/", prefix = "/") { it.render() }
        }

    companion object {
        val root = DataTypePath()
    }
}


private fun DataPathSegment.render(): String =
    when (this) {
        is DataPathSegment.Field -> "field:${id.name}#${id.occurrence}"
        is DataPathSegment.Entry -> "entry:${kind.renderName()}:${key}"
        is DataPathSegment.Element -> "element:$index"
        is DataPathSegment.Variant -> "variant:${id.value}"
        DataPathSegment.ListingElement -> "listing-element"
        DataPathSegment.MappingKey -> "mapping-key"
        DataPathSegment.MappingValue -> "mapping-value"
    }
