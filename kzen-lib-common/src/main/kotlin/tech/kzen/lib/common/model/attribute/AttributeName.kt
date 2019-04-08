package tech.kzen.lib.common.model.attribute


data class AttributeName(
        val value: String
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun asAttributeNesting(): AttributePath {
        return AttributePath.ofName(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return value
    }
}