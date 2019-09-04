package tech.kzen.lib.common.model.document


data class DocumentSegment(
        val value: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val segmentPattern = Regex("[a-zA-Z0-9_\\- ]+")
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(value.isNotEmpty()) {
            "Document segment can't be empty"
        }
        check(segmentPattern.matches(value)) {
            "Document segment uses invalid character: $value"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return value
    }
}