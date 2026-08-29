package tech.kzen.lib.common.exec.data.type


data class DataField(
    val id: FieldId,
    val type: DataType,
    val optional: Boolean = false
)
