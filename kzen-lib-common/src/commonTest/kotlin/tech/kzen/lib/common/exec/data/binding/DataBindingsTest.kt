package tech.kzen.lib.common.exec.data.binding

import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.SnapshotResult
import tech.kzen.lib.common.exec.data.value.ValueAccess
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame


class DataBindingsTest {
    private val textType = DataType.Scalar(ScalarKind.Text)
    private val nullableTextType = DataType.Scalar(ScalarKind.Text, nullable = true)

    @Test
    fun schemaIsFrozenOrderedEnumerableAndRejectsInvalidNamesAndDuplicates() {
        val definitions = mutableListOf(
            definition("first", textType),
            definition("second", textType, DataPresence.Optional))
        val schema = BindingSchema.of(definitions)
        definitions.clear()

        assertEquals(listOf("first", "second"), schema.definitions.map { it.name.value })
        assertEquals(textType, schema[BindingName("first")].contract.structural)
        assertEquals(null, schema.find(BindingName("missing")))
        assertNotEquals(BindingSchema.empty.digest(), schema.digest())

        assertFailsWith<DataException> { BindingName(" ") }
        assertFailsWith<DataException> { BindingName(" padded ") }
        assertFailsWith<DataException> {
            BindingSchema.of(definition("same", textType), definition("same", textType))
        }
    }

    @Test
    fun bindingAppliesDefaultsOnceAndPreservesUnboundNullOriginsAndSchemaOrder() {
        val default = DataDefault(DataSnapshot.of(textType, TextExecutionValue("fallback")))
        val schema = BindingSchema.of(
            definition("required", textType),
            definition("optional", textType, DataPresence.Optional),
            definition("defaulted", textType, DataPresence.Defaulted(default)),
            definition("nullable", nullableTextType))
        val suppliedRequired = LiteralDataValues.lift("value")
        val suppliedNull = LiteralDataValues.lift(null, DataContract(nullableTextType))

        val bindings = DataBindings.bind(schema,
            BindingName("nullable") to suppliedNull,
            BindingName("required") to suppliedRequired)

        assertEquals(
            listOf("required", "optional", "defaulted", "nullable"),
            bindings.entries().map { it.first.name.value })
        assertSame(suppliedRequired, bindings.requireValue(BindingName("required")))
        assertEquals(BindingState.Unbound, bindings[BindingName("optional")])
        assertEquals(
            BindingOrigin.Defaulted,
            assertIs<BindingState.Bound>(bindings[BindingName("defaulted")]).origin)
        val nullState = assertIs<BindingState.Bound>(bindings[BindingName("nullable")])
        assertEquals(BindingOrigin.Supplied, nullState.origin)
        assertEquals(DataState.Null, nullState.value.access.state(nullState.value.root))

        val firstDefault = bindings.requireValue(BindingName("defaulted"))
        val secondConstruction = DataBindings.bind(schema,
            BindingName("required") to suppliedRequired,
            BindingName("nullable") to suppliedNull)
        assertNotEquals(firstDefault, secondConstruction.requireValue(BindingName("defaulted")))
    }

    @Test
    fun constructionRejectsUnknownDuplicateMissingWrongTypeAndAbsentRoot() {
        val name = BindingName("value")
        val schema = BindingSchema.of(definition(name.value, textType))

        assertProblem(DataProblem.invalidIdentifier) {
            DataBindings.bind(schema, BindingName("other") to LiteralDataValues.lift("x"))
        }
        assertProblem(DataProblem.invalidIdentifier) {
            DataBindings.bind(schema, listOf(
                name to LiteralDataValues.lift("x"),
                name to LiteralDataValues.lift("y")))
        }
        assertProblem(DataProblem.missingValue) { DataBindings.bind(schema) }
        assertProblem(DataProblem.incompatibleType) {
            DataBindings.bind(schema, name to LiteralDataValues.lift(1))
        }

        val mutableRoot = RootOnlyAccess(DataContract(textType))
        val value = DataValue(mutableRoot, DataNode(0))
        mutableRoot.rootState = DataState.Absent
        assertProblem(DataProblem.invalidState) { DataBindings.bind(schema, name to value) }

        val partial = DataBindings.assemble(schema)
        assertEquals(BindingState.Unbound, partial[name])
        assertProblem(DataProblem.missingValue) { partial.requireValue(name) }
    }

    @Test
    fun defaultsCannotPromiseNativeValuesAndBindingChecksNativePresenceShallowly() {
        val snapshot = DataSnapshot.of(textType, TextExecutionValue("literal"))
        val nativeContract = DataContract(
            DataType.Opaque(),
            mapOf(DataTypePath.root to TypeMetadata.any))
        val nativeValue = LiteralDataValues.lift(Any())
        val nativeSchema = BindingSchema.of(BindingDefinition(BindingName("native"), nativeValue.contract))
        DataBindings.bind(nativeSchema, BindingName("native") to nativeValue)

        assertProblem(DataProblem.nativeTypeMissing) {
            BindingDefinition(
                BindingName("native"), nativeContract,
                DataPresence.Defaulted(DataDefault(snapshot)))
        }

        val record = DataType.Record(listOf(DataField(FieldId("child"), textType)))
        val access = RootOnlyAccess(DataContract(record))
        val live = DataValue(access, DataNode(0))
        val shallow = BindingSchema.of(BindingDefinition(BindingName("record"), DataContract(record)))
        DataBindings.bind(shallow, BindingName("record") to live)
        assertEquals(0, access.deepReads)

        val requiresNative = BindingSchema.of(BindingDefinition(BindingName("record"), DataContract(
            record, mapOf(DataTypePath.root to TypeMetadata.any))))
        assertProblem(DataProblem.nativeTypeMissing) {
            DataBindings.bind(requiresNative, BindingName("record") to live)
        }
    }

    @Test
    fun sensitivityIsWholeBindingDisplayPolicyNotValueTaint() {
        val value = LiteralDataValues.lift("secret")
        val schema = BindingSchema.of(
            BindingDefinition(BindingName("secret"), value.contract, sensitive = true),
            BindingDefinition(BindingName("public"), value.contract))
        val bindings = DataBindings.bind(schema,
            BindingName("secret") to value,
            BindingName("public") to value)

        assertEquals(SnapshotResult.Redacted, bindings.snapshot(BindingName("secret")))
        assertIs<SnapshotResult.Complete>(bindings.snapshot(BindingName("public")))
        assertSame(value, bindings.requireValue(BindingName("public")))
    }

    private fun definition(
        name: String,
        type: DataType,
        presence: DataPresence = DataPresence.Required
    ) = BindingDefinition(BindingName(name), DataContract(type), presence)

    private fun assertProblem(code: String, block: () -> Unit) {
        val failure = assertFailsWith<DataException>(block = block)
        assertEquals(code, failure.problem.code)
    }

    private class RootOnlyAccess(
        private val rootContract: DataContract
    ): ValueAccess {
        var rootState = DataState.Present
        var deepReads = 0

        override fun contract(node: DataNode) = rootContract
        override fun state(node: DataNode) = rootState
        override fun activeVariant(node: DataNode) = deep<VariantId>()
        override fun selected(node: DataNode) = deep<DataNode>()
        override fun field(node: DataNode, field: FieldId) = deep<DataNode>()
        override fun entry(node: DataNode, key: tech.kzen.lib.common.exec.ScalarExecutionValue) = deep<DataNode>()
        override fun element(node: DataNode, index: Int) = deep<DataNode>()
        override fun size(node: DataNode) = deep<Int>()
        override fun keyAt(node: DataNode, index: Int) = deep<tech.kzen.lib.common.exec.ScalarExecutionValue>()
        override fun scalar(node: DataNode) = deep<tech.kzen.lib.common.exec.ScalarExecutionValue>()
        override fun readBoolean(node: DataNode) = deep<Boolean>()
        override fun readLong(node: DataNode) = deep<Long>()
        override fun readDouble(node: DataNode) = deep<Double>()
        override fun readText(node: DataNode) = deep<String>()
        override fun readBinary(node: DataNode) = deep<ByteArray>()
        override fun native(node: DataNode) = deep<Any>()

        private fun <T> deep(): T {
            deepReads++
            error("Deep access is forbidden in this test")
        }
    }
}
