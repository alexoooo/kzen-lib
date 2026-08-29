package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BinaryHandleExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypeAlgebra
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DataVariant
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.type.VariantSelection
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/** Immutable literal authoring and typed snapshot decoding. */
object LiteralDataValues {
    fun lift(value: Any?, expected: DataContract? = null): DataValue {
        if (expected != null && expected.nativeByPath.isNotEmpty()) {
            throw DataException(DataProblem(
                DataProblem.nativeTypeMissing,
                "A literal cannot satisfy a native-requiring contract"))
        }
        val inferred = inferType(value, mutableListOf())
        val target = expected?.structural ?: inferred
        val targetContract = inferredContract(target)
        val access = LiteralValueAccess()
        val root = access.build(value, targetContract, emptyList())
        return DataValue(access, root)
    }


    fun union(
        contract: DataContract,
        variant: VariantId,
        value: Any?
    ): DataValue {
        require(contract.nativeByPath.isEmpty()) { "Literal union contract must be structural" }
        val union = contract.structural as? DataType.Union
            ?: throw DataException(DataProblem(
                DataProblem.invalidUnion,
                "Literal union requires a union contract"))
        val selected = union.variants.firstOrNull { it.id == variant }
            ?: throw DataException(DataProblem(
                DataProblem.unionVariantUnknown,
                "Union has no variant '$variant'"))
        val access = LiteralValueAccess()
        val child = access.build(
            value,
            contract.child(DataPathSegment.Variant(variant)),
            listOf(DataPathSegment.Variant(variant)))
        val root = access.add(NodeData(
            contract,
            DataState.Present,
            emptyList(),
            NodePayload.Union(selected.id, child)))
        return DataValue(access, root)
    }


    internal fun decode(type: DataType, value: tech.kzen.lib.common.exec.ExecutionValue): DataValue {
        val access = LiteralValueAccess()
        val root = access.decode(value, DataContract(type), emptyList())
        return DataValue(access, root)
    }


    private fun inferType(value: Any?, open: MutableList<Any>): DataType {
        if (value == null) return DataType.Dynamic(true)
        scalarType(value)?.let { return it }
        if (value !is RecordLiteral && value !is List<*> && value !is Map<*, *>) {
            return DataType.Opaque()
        }
        if (open.any { it === value }) {
            throw DataException(DataProblem(DataProblem.snapshotCycle, "Literal contains a cycle"))
        }
        open += value
        try {
            return when (value) {
                is RecordLiteral -> DataType.Record(value.fields.map { field ->
                    DataField(FieldId(field.name), inferType(field.value, open))
                })
                is List<*> -> DataType.Listing(joinTypes(value, open))
                is Map<*, *> -> inferMapping(value, open)
                else -> error("guarded")
            }
        }
        finally {
            open.removeAt(open.lastIndex)
        }
    }


    private fun inferMapping(value: Map<*, *>, open: MutableList<Any>): DataType {
        if (value.isEmpty()) {
            return DataType.Mapping(DataType.Dynamic(false), DataType.Dynamic())
        }
        val keys = value.keys.map { key -> key ?: return opaqueMap() }
        val keyTypes = keys.map { scalarType(it) ?: return opaqueMap() }
        val joinedKey = keyTypes.reduce(DataTypeAlgebra::join)
        if (joinedKey !is DataType.Scalar || joinedKey.nullable) return opaqueMap()
        val canonical = mutableSetOf<String>()
        for (key in keys) {
            val encoded = scalarValue(key, joinedKey.kind)
            if (!canonical.add(canonicalText(encoded))) {
                throw DataException(DataProblem(
                    DataProblem.mappingKeyCollision,
                    "Mapping keys collide after canonicalization: $key"))
            }
        }
        val valueType = joinTypes(value.values, open)
        return DataType.Mapping(joinedKey, valueType)
    }


    private fun joinTypes(values: Iterable<Any?>, open: MutableList<Any>): DataType {
        var joined: DataType? = null
        for (value in values) {
            val inferred = inferType(value, open)
            joined = joined?.let { DataTypeAlgebra.join(it, inferred) } ?: inferred
        }
        return joined ?: DataType.Dynamic()
    }


    private fun opaqueMap(): DataType = DataType.Opaque()


    private fun scalarType(value: Any): DataType.Scalar? =
        when (value) {
            is Boolean -> DataType.Scalar(ScalarKind.Boolean)
            is Byte -> DataType.Scalar(ScalarKind.Integer(8))
            is Short -> DataType.Scalar(ScalarKind.Integer(16))
            is Int -> DataType.Scalar(ScalarKind.Integer(32))
            is Long -> DataType.Scalar(ScalarKind.Integer(64))
            is Float -> DataType.Scalar(ScalarKind.Floating(32))
            is Double -> DataType.Scalar(ScalarKind.Floating(64))
            is String -> DataType.Scalar(ScalarKind.Text)
            is ByteArray -> DataType.Scalar(ScalarKind.Binary)
            else -> null
        }


    private fun scalarValue(value: Any, kind: ScalarKind): ScalarExecutionValue =
        when (kind) {
            ScalarKind.Boolean -> BooleanExecutionValue.of(value as Boolean)
            is ScalarKind.Integer -> TextExecutionValue((value as Number).toLong().toString())
            ScalarKind.Decimal -> TextExecutionValue(value.toString())
            is ScalarKind.Floating -> NumberExecutionValue((value as Number).toDouble())
            ScalarKind.Text -> TextExecutionValue(value as String)
            ScalarKind.Binary -> BinaryExecutionValue((value as ByteArray).copyOf())
            ScalarKind.Date,
            ScalarKind.Time,
            ScalarKind.Instant,
            ScalarKind.Duration,
            ScalarKind.Uuid -> TextExecutionValue(value as String)
        }


    internal fun canonicalText(value: ScalarExecutionValue): String =
        when (value) {
            is TextExecutionValue -> value.value
            is BooleanExecutionValue -> value.value.toString()
            is NumberExecutionValue -> value.value.toString()
            is LongExecutionValue -> value.value.toString()
            is BinaryExecutionValue -> value.asBase64()
            is BinaryHandleExecutionValue -> value.toString()
        }


    private class LiteralValueAccess: ValueAccess {
        private val nodes = mutableListOf<NodeData>()

        fun add(data: NodeData): DataNode {
            nodes += data
            return DataNode(nodes.lastIndex.toLong())
        }

        fun build(value: Any?, contract: DataContract, path: List<DataPathSegment>): DataNode {
            val type = contract.structural
            if (value == null) {
                if (!type.nullable) fail(DataProblem.invalidState, "Null is not allowed by $type", path)
                return add(NodeData(contract, DataState.Null, path, NodePayload.None))
            }
            return when (type) {
                is DataType.Scalar -> add(NodeData(
                    contract, DataState.Present, path,
                    NodePayload.Scalar(scalarValue(value, type.kind))))
                is DataType.Record -> buildRecord(value, contract, type, path)
                is DataType.Listing -> buildListing(value, contract, type, path)
                is DataType.Mapping -> buildMapping(value, contract, type, path)
                is DataType.Union -> buildInferredUnion(value, contract, type, path)
                is DataType.Opaque -> add(NodeData(
                    DataContract(type, mapOf(DataTypePath.root to TypeMetadata.any)),
                    DataState.Present, path, NodePayload.Native(value)))
                is DataType.Dynamic -> {
                    val inferred = inferType(value, mutableListOf())
                    build(value, inferredContract(inferred), path)
                }
            }
        }

        private fun buildRecord(
            value: Any,
            contract: DataContract,
            type: DataType.Record,
            path: List<DataPathSegment>
        ): DataNode {
            val literal = value as? RecordLiteral
                ?: fail(DataProblem.invalidValue, "RecordLiteral required for $type", path)
            val byName = literal.fields.associateBy { it.name }
            val known = type.fields.map { it.id.name }.toSet()
            val extra = byName.keys - known
            if (extra.isNotEmpty()) fail(DataProblem.invalidValue, "Extra record fields: $extra", path)
            val children = linkedMapOf<FieldId, DataNode>()
            for (field in type.fields) {
                val segment = DataPathSegment.Field(field.id)
                val literalField = byName[field.id.name]
                children[field.id] = if (literalField == null) {
                    if (!field.optional) fail(
                        DataProblem.missingValue, "Required field '${field.id}' is missing", path + segment)
                    add(NodeData(contract.child(segment), DataState.Absent, path + segment, NodePayload.None))
                }
                else {
                    build(literalField.value, contract.child(segment), path + segment)
                }
            }
            return add(NodeData(contract, DataState.Present, path, NodePayload.Record(children)))
        }

        private fun buildListing(
            value: Any,
            contract: DataContract,
            type: DataType.Listing,
            path: List<DataPathSegment>
        ): DataNode {
            val list = value as? List<*>
                ?: fail(DataProblem.invalidValue, "List required for $type", path)
            val elementContract = contract.child(DataPathSegment.ListingElement)
            val children = list.mapIndexed { index, element ->
                build(element, elementContract, path + DataPathSegment.Element(index))
            }
            return add(NodeData(contract, DataState.Present, path, NodePayload.Listing(children)))
        }

        private fun buildMapping(
            value: Any,
            contract: DataContract,
            type: DataType.Mapping,
            path: List<DataPathSegment>
        ): DataNode {
            val map = value as? Map<*, *>
                ?: fail(DataProblem.invalidValue, "Map required for $type", path)
            val keyType = type.key as? DataType.Scalar
                ?: if (map.isEmpty()) null else fail(
                    DataProblem.invalidMappingKey, "Non-empty mapping needs a scalar key type", path)
            val valueContract = contract.child(DataPathSegment.MappingValue)
            val entries = map.entries.map { (key, element) ->
                if (key == null || keyType == null) fail(
                    DataProblem.invalidMappingKey, "Mapping key must be a non-null scalar", path)
                val encoded = scalarValue(key, keyType.kind)
                val segment = DataPathSegment.Entry(keyType.kind, encoded)
                encoded to build(element, valueContract, path + segment)
            }
            return add(NodeData(contract, DataState.Present, path, NodePayload.Mapping(entries)))
        }

        private fun buildInferredUnion(
            value: Any,
            contract: DataContract,
            type: DataType.Union,
            path: List<DataPathSegment>
        ): DataNode {
            val actual = inferType(value, mutableListOf())
            val selection = DataTypeAlgebra.selectVariant(type, actual)
            val selected = when (selection) {
                is VariantSelection.Selected -> selection.variant
                is VariantSelection.NoMatch -> throw DataException(selection.problem)
                is VariantSelection.Ambiguous -> fail(
                    DataProblem.unionVariantAmbiguous,
                    "Literal matches multiple union variants: ${selection.candidates}", path)
            }
            val child = build(
                value,
                contract.child(DataPathSegment.Variant(selected)),
                path + DataPathSegment.Variant(selected))
            return add(NodeData(contract, DataState.Present, path, NodePayload.Union(selected, child)))
        }

        fun decode(
            value: tech.kzen.lib.common.exec.ExecutionValue,
            contract: DataContract,
            path: List<DataPathSegment>
        ): DataNode = SnapshotDecoder(this).decode(value, contract, path)

        override fun contract(node: DataNode): DataContract = get(node).contract
        override fun state(node: DataNode): DataState = get(node).state

        override fun activeVariant(node: DataNode): VariantId =
            payload< NodePayload.Union>(node, "activeVariant").variant

        override fun selected(node: DataNode): DataNode =
            payload<NodePayload.Union>(node, "selected").selected

        override fun field(node: DataNode, field: FieldId): DataNode =
            payload<NodePayload.Record>(node, "field").fields[field]
                ?: failNode(node, "Unknown record field '$field'")

        override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode =
            payload<NodePayload.Mapping>(node, "entry").entries.firstOrNull { it.first == key }?.second
                ?: failNode(node, "Unknown mapping key '$key'")

        override fun element(node: DataNode, index: Int): DataNode {
            val elements = payload<NodePayload.Listing>(node, "element").elements
            if (index !in elements.indices) failNode(node, "Listing index out of bounds: $index")
            return elements[index]
        }

        override fun size(node: DataNode): Int =
            when (val payload = present(node, "size").payload) {
                is NodePayload.Listing -> payload.elements.size
                is NodePayload.Mapping -> payload.entries.size
                else -> failNode(node, "size is valid only for listing and mapping nodes")
            }

        override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue {
            val entries = payload<NodePayload.Mapping>(node, "keyAt").entries
            if (index !in entries.indices) failNode(node, "Mapping index out of bounds: $index")
            return frozenScalar(entries[index].first)
        }

        override fun scalar(node: DataNode): ScalarExecutionValue =
            frozenScalar(payload<NodePayload.Scalar>(node, "scalar").value)

        override fun readBoolean(node: DataNode): Boolean =
            (scalar(node) as? BooleanExecutionValue)?.value
                ?: failNode(node, "Boolean scalar required")

        override fun readLong(node: DataNode): Long =
            when (val value = scalar(node)) {
                is LongExecutionValue -> value.value
                is TextExecutionValue -> value.value.toLongOrNull()
                    ?: failNode(node, "Scalar cannot be represented exactly as Long")
                else -> failNode(node, "Integer scalar required")
            }

        override fun readDouble(node: DataNode): Double =
            when (val value = scalar(node)) {
                is NumberExecutionValue -> value.value
                is LongExecutionValue -> {
                    val projected = value.value.toDouble()
                    if (projected.toLong() != value.value) failNode(
                        node, "Scalar cannot be represented exactly as Double")
                    projected
                }
                is TextExecutionValue -> value.value.toDoubleOrNull()
                    ?.takeIf { it.isFinite() }
                    ?: failNode(node, "Scalar cannot be represented exactly as Double")
                else -> failNode(node, "Numeric scalar required")
            }

        override fun readText(node: DataNode): String =
            (scalar(node) as? TextExecutionValue)?.value
                ?: failNode(node, "Text scalar required")

        override fun readBinary(node: DataNode): ByteArray =
            (scalar(node) as? BinaryExecutionValue)?.value?.copyOf()
                ?: failNode(node, "Inline binary scalar required")

        override fun native(node: DataNode): Any =
            (present(node, "native").payload as? NodePayload.Native)?.value
                ?: failNode(node, "Node has no native facet")

        private fun get(node: DataNode): NodeData {
            val index = node.token.toInt()
            if (node.token != index.toLong() || index !in nodes.indices) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidOperation,
                    "Data node does not belong to this literal backing: ${node.token}"))
            }
            return nodes[index]
        }

        private fun present(node: DataNode, operation: String): NodeData {
            val data = get(node)
            if (data.state != DataState.Present) failNode(
                node, "$operation requires a present node, found ${data.state}")
            return data
        }

        private inline fun <reified T: NodePayload> payload(node: DataNode, operation: String): T =
            present(node, operation).payload as? T
                ?: failNode(node, "$operation is incompatible with ${get(node).contract.structural}")

        private fun failNode(node: DataNode, message: String): Nothing {
            val data = get(node)
            throw DataAccessException(DataProblem(
                DataProblem.invalidOperation, message, data.path))
        }
    }


    private fun inferredContract(type: DataType): DataContract =
        DataContract(type, opaqueMetadata(type))


    private fun opaqueMetadata(
        type: DataType,
        path: DataTypePath = DataTypePath.root
    ): Map<DataTypePath, TypeMetadata> =
        when (type) {
            is DataType.Opaque -> mapOf(
                path to TypeMetadata(
                    tech.kzen.lib.platform.ClassNames.kotlinAny,
                    emptyList(),
                    type.nullable))

            is DataType.Record -> type.fields.flatMap { field ->
                opaqueMetadata(
                    field.type,
                    DataTypePath(path.segments + DataPathSegment.Field(field.id))).entries
            }.associate { it.toPair() }

            is DataType.Listing -> opaqueMetadata(
                type.element,
                DataTypePath(path.segments + DataPathSegment.ListingElement))

            is DataType.Mapping -> opaqueMetadata(
                type.value,
                DataTypePath(path.segments + DataPathSegment.MappingValue))

            is DataType.Union -> type.variants.flatMap { variant ->
                opaqueMetadata(
                    variant.type,
                    DataTypePath(path.segments + DataPathSegment.Variant(variant.id))).entries
            }.associate { it.toPair() }

            is DataType.Dynamic,
            is DataType.Scalar -> emptyMap()
        }


    private class SnapshotDecoder(private val access: LiteralValueAccess) {
        fun decode(
            value: tech.kzen.lib.common.exec.ExecutionValue,
            contract: DataContract,
            path: List<DataPathSegment>
        ): DataNode {
            val type = contract.structural
            if (value === tech.kzen.lib.common.exec.NullExecutionValue) {
                if (!type.nullable) fail(DataProblem.invalidState, "Null is not allowed by $type", path)
                return access.add(NodeData(contract, DataState.Null, path, NodePayload.None))
            }
            return when (type) {
                is DataType.Scalar -> decodeScalar(value, contract, type, path)
                is DataType.Record -> decodeRecord(value, contract, type, path)
                is DataType.Listing -> decodeListing(value, contract, path)
                is DataType.Mapping -> decodeMapping(value, contract, type, path)
                is DataType.Union -> decodeUnion(value, contract, type, path)
                is DataType.Dynamic -> fail(
                    DataProblem.invalidValue, "Dynamic snapshot values require a concrete type", path)
                is DataType.Opaque -> fail(DataProblem.snapshotOpaque, "Opaque snapshot values are forbidden", path)
            }
        }

        private fun decodeScalar(
            value: tech.kzen.lib.common.exec.ExecutionValue,
            contract: DataContract,
            type: DataType.Scalar,
            path: List<DataPathSegment>
        ): DataNode {
            val scalar = value as? ScalarExecutionValue
                ?: fail(DataProblem.invalidValue, "Scalar snapshot value required", path)
            val valid = when (type.kind) {
                ScalarKind.Boolean -> scalar is BooleanExecutionValue
                is ScalarKind.Integer,
                ScalarKind.Decimal,
                ScalarKind.Text,
                ScalarKind.Date,
                ScalarKind.Time,
                ScalarKind.Instant,
                ScalarKind.Duration,
                ScalarKind.Uuid -> scalar is TextExecutionValue
                is ScalarKind.Floating -> scalar is NumberExecutionValue
                ScalarKind.Binary -> scalar is BinaryExecutionValue
            }
            if (!valid || scalar is BinaryHandleExecutionValue) {
                fail(DataProblem.invalidValue, "Scalar snapshot does not match ${type.kind}", path)
            }
            return access.add(NodeData(
                contract, DataState.Present, path, NodePayload.Scalar(frozenScalar(scalar))))
        }

        private fun decodeRecord(
            value: tech.kzen.lib.common.exec.ExecutionValue,
            contract: DataContract,
            type: DataType.Record,
            path: List<DataPathSegment>
        ): DataNode {
            val map = value as? tech.kzen.lib.common.exec.MapExecutionValue
                ?: fail(DataProblem.invalidValue, "Record snapshot must be a map", path)
            val known = type.fields.map { it.id.name }.toSet()
            if ((map.values.keys - known).isNotEmpty()) fail(
                DataProblem.invalidValue, "Record snapshot has extra fields", path)
            val fields = linkedMapOf<FieldId, DataNode>()
            for (field in type.fields) {
                val segment = DataPathSegment.Field(field.id)
                val childValue = map.values[field.id.name]
                fields[field.id] = if (childValue == null) {
                    if (!field.optional) fail(
                        DataProblem.missingValue, "Required record field is absent", path + segment)
                    access.add(NodeData(
                        contract.child(segment), DataState.Absent, path + segment, NodePayload.None))
                }
                else decode(childValue, contract.child(segment), path + segment)
            }
            return access.add(NodeData(contract, DataState.Present, path, NodePayload.Record(fields)))
        }

        private fun decodeListing(
            value: tech.kzen.lib.common.exec.ExecutionValue,
            contract: DataContract,
            path: List<DataPathSegment>
        ): DataNode {
            val list = value as? tech.kzen.lib.common.exec.ListExecutionValue
                ?: fail(DataProblem.invalidValue, "Listing snapshot must be a list", path)
            val childContract = contract.child(DataPathSegment.ListingElement)
            val elements = list.values.mapIndexed { index, child ->
                decode(child, childContract, path + DataPathSegment.Element(index))
            }
            return access.add(NodeData(contract, DataState.Present, path, NodePayload.Listing(elements)))
        }

        private fun decodeMapping(
            value: tech.kzen.lib.common.exec.ExecutionValue,
            contract: DataContract,
            type: DataType.Mapping,
            path: List<DataPathSegment>
        ): DataNode {
            val keyType = type.key as? DataType.Scalar
            val values = mutableListOf<Pair<ScalarExecutionValue, DataNode>>()
            val childContract = contract.child(DataPathSegment.MappingValue)
            if (keyType?.kind == ScalarKind.Text && value is tech.kzen.lib.common.exec.MapExecutionValue) {
                for ((key, child) in value.values) {
                    val encoded = TextExecutionValue(key)
                    values += encoded to decode(
                        child, childContract, path + DataPathSegment.Entry(keyType.kind, encoded))
                }
            }
            else {
                val list = value as? tech.kzen.lib.common.exec.ListExecutionValue
                    ?: fail(DataProblem.invalidValue, "Non-text mapping snapshot must be an entry list", path)
                for (entryValue in list.values) {
                    val entry = entryValue as? tech.kzen.lib.common.exec.MapExecutionValue
                        ?: fail(DataProblem.invalidValue, "Mapping entry must be a map", path)
                    val key = entry.values["key"] as? ScalarExecutionValue
                        ?: fail(DataProblem.invalidValue, "Mapping entry key must be scalar", path)
                    val child = entry.values["value"]
                        ?: fail(DataProblem.invalidValue, "Mapping entry value is missing", path)
                    val segment = DataPathSegment.Entry(
                        keyType?.kind ?: fail(
                            DataProblem.invalidMappingKey, "Dynamic mapping key cannot decode entries", path),
                        key)
                    values += frozenScalar(key) to decode(child, childContract, path + segment)
                }
            }
            return access.add(NodeData(contract, DataState.Present, path, NodePayload.Mapping(values)))
        }

        private fun decodeUnion(
            value: tech.kzen.lib.common.exec.ExecutionValue,
            contract: DataContract,
            type: DataType.Union,
            path: List<DataPathSegment>
        ): DataNode {
            val map = value as? tech.kzen.lib.common.exec.MapExecutionValue
                ?: fail(DataProblem.invalidValue, "Union snapshot must be a map", path)
            val id = VariantId((map.values["variant"] as? TextExecutionValue)?.value
                ?: fail(DataProblem.invalidValue, "Union snapshot variant is missing", path))
            if (type.variants.none { it.id == id }) fail(
                DataProblem.unionVariantUnknown, "Union has no variant '$id'", path)
            val segment = DataPathSegment.Variant(id)
            val child = decode(
                map.values["value"] ?: fail(
                    DataProblem.invalidValue, "Union snapshot value is missing", path),
                contract.child(segment), path + segment)
            return access.add(NodeData(contract, DataState.Present, path, NodePayload.Union(id, child)))
        }
    }
}


private data class NodeData(
    val contract: DataContract,
    val state: DataState,
    val path: List<DataPathSegment>,
    val payload: NodePayload
)


private sealed interface NodePayload {
    data object None: NodePayload
    data class Scalar(val value: ScalarExecutionValue): NodePayload
    data class Record(val fields: Map<FieldId, DataNode>): NodePayload
    data class Listing(val elements: List<DataNode>): NodePayload
    data class Mapping(val entries: List<Pair<ScalarExecutionValue, DataNode>>): NodePayload
    data class Union(val variant: VariantId, val selected: DataNode): NodePayload
    data class Native(val value: Any): NodePayload
}


private fun frozenScalar(value: ScalarExecutionValue): ScalarExecutionValue =
    when (value) {
        is BinaryExecutionValue -> BinaryExecutionValue(value.value.copyOf())
        else -> value
    }


private fun fail(code: String, message: String, path: List<DataPathSegment>): Nothing =
    throw DataAccessException(DataProblem(code, message, path))
