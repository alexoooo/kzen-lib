package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.fixture.BeanFixtures
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


/**
 * E7 items 1–4: an ordinary Java class is a Record by the fixed bean convention (lexical order, precedence,
 * inheritance, nullability, named errors), enums are text, Sets are unordered listings, and native type
 * tokens are per class identity.
 */
class PlainObjectShapeTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun ordinaryClassIsARecordInLexicalOrderWithGetterAndFieldPrecedence() {
        DefaultDataAdapterRegistry().use { registry ->
            val described = registry.describe(typeOf<BeanFixtures.Person>())
            val record = assertIs<DataType.Record>(described.structural)
            // isBoxed (boxed Boolean), get(), is(), getStatic, getNothing, describe and getClass are not properties
            assertEquals(listOf("URL", "active", "age", "name", "note"), record.fields.map { it.id.name })
            assertEquals(DataType.Scalar(ScalarKind.Boolean), record.fields.single { it.id.name == "active" }.type)
            assertEquals(DataType.Scalar(ScalarKind.Integer(32)), record.fields.single { it.id.name == "age" }.type)
            // Unannotated Java references are optional
            assertEquals(DataType.Scalar(ScalarKind.Text, nullable = true), record.fields.single { it.id.name == "name" }.type)

            val value = registry.lift(BeanFixtures.Person("ada", 36))
            assertEquals(described.structural, value.type)
            assertEquals("ada", readText(value, "name"))
            assertEquals("https://example.test/ada", readText(value, "URL"))
            assertEquals("n/a", readText(value, "note"))
            assertEquals(36L, value.access.readLong(value.access.field(value.root, FieldId("age"))))
            assertTrue(value.access.readBoolean(value.access.field(value.root, FieldId("active"))))
        }
    }


    @Test
    fun mostDerivedDeclarationWinsAcrossTheHierarchy() {
        DefaultDataAdapterRegistry().use { registry ->
            val record = assertIs<DataType.Record>(registry.describe(typeOf<BeanFixtures.Employee>()).structural)
            assertEquals(
                listOf("URL", "active", "age", "department", "name", "note", "reports", "side", "tags"),
                record.fields.map { it.id.name })
            assertEquals(DataType.Scalar(ScalarKind.Text, nullable = true), record.fields.single { it.id.name == "side" }.type)
            assertEquals(
                DataType.Listing(DataType.Scalar(ScalarKind.Text), nullable = true),
                record.fields.single { it.id.name == "tags" }.type)
            val reports = assertIs<DataType.Listing>(record.fields.single { it.id.name == "reports" }.type)
            assertIs<DataType.Record>(reports.element)

            val value = registry.lift(BeanFixtures.Employee("bob", 40, "ops"))
            assertEquals("employee:bob", readText(value, "name"), "the override is read")
            assertEquals("employee note", readText(value, "note"), "the hiding field is read")
            assertEquals("BUY", readText(value, "side"), "an enum reads as its constant name")
            val tags = value.access.field(value.root, FieldId("tags"))
            assertEquals(2, value.access.size(tags))
            assertEquals(setOf("a", "b"), (0 until 2).map { value.access.readText(value.access.element(tags, it)) }.toSet())
        }
    }


    @Test
    fun conflictingGetterAndFieldTypesFailNamingTheClass() {
        DefaultDataAdapterRegistry().use { registry ->
            val error = assertFailsWith<DataException> { registry.describe(typeOf<BeanFixtures.Conflicting>()) }
            assertEquals(DataProblem.nativeShapeConflict, error.problem.code)
            assertTrue(error.problem.message.contains(BeanFixtures.Conflicting::class.java.name), error.problem.message)
            assertTrue(error.problem.message.contains("'value'"), error.problem.message)
        }
    }


    @Test
    fun throwingGetterIsANamedAccessFailureNotARunCrash() {
        DefaultDataAdapterRegistry().use { registry ->
            val value = registry.lift(BeanFixtures.Throwing())
            assertEquals("fine", readText(value, "fine"))
            val error = assertFailsWith<DataAccessException> { value.access.field(value.root, FieldId("broken")) }
            assertTrue(error.problem.message.contains("'broken'"), error.problem.message)
            assertTrue(error.problem.message.contains("broken getter"), error.problem.message)
            assertEquals(listOf(DataPathSegment.Field(FieldId("broken"))), error.problem.path)
        }
    }


    @Test
    fun nullabilityFromAnnotationsThenOptionalByDefault() {
        DefaultDataAdapterRegistry().use { registry ->
            val record = assertIs<DataType.Record>(registry.describe(typeOf<BeanFixtures.Annotated>()).structural)
            fun nullable(name: String) = record.fields.single { it.id.name == name }.type.nullable
            assertTrue(nullable("maybe"))
            assertTrue(!nullable("surely"))
            assertTrue(nullable("unknown"))
            assertTrue(nullable("optional"))
            assertTrue(!nullable("required"))
            assertTrue(nullable("unannotated"))
            assertTrue(!nullable("primitive"))

            val marked = assertIs<DataType.Record>(registry.describe(typeOf<BeanFixtures.Marked>()).structural)
            assertTrue(!marked.fields.single { it.id.name == "required" }.type.nullable, "@NullMarked class")
            assertTrue(marked.fields.single { it.id.name == "optional" }.type.nullable)

            val value = registry.lift(BeanFixtures.Annotated())
            assertEquals(DataState.Null, value.access.state(value.access.field(value.root, FieldId("maybe"))))
            assertEquals("required", readText(value, "required"))
        }
    }


    @Test
    fun kotlinClassKeepsDeclaredNullabilityAndAClassWithoutPropertiesStaysOpaque() {
        DefaultDataAdapterRegistry().use { registry ->
            val record = assertIs<DataType.Record>(registry.describe(typeOf<PlainKotlin>()).structural)
            assertEquals(listOf("label", "size", "tags"), record.fields.map { it.id.name })
            assertEquals(DataType.Scalar(ScalarKind.Text, nullable = true), record.fields[0].type)
            assertEquals(DataType.Scalar(ScalarKind.Integer(32)), record.fields[1].type)
            assertEquals(DataType.Listing(DataType.Scalar(ScalarKind.Text)), record.fields[2].type)

            assertIs<DataType.Opaque>(registry.describe(typeOf<BeanFixtures.Empty>()).structural)
            assertEquals(DataType.Dynamic(nullable = false), registry.describe(typeOf<Any>()).structural)
            assertEquals(DataType.Dynamic(nullable = true), registry.describe(typeOf<Any?>()).structural)
        }
    }


    @Test
    fun enumsAreTextAndSetsAreUnorderedListingsInDescribeAndLift() {
        DefaultDataAdapterRegistry().use { registry ->
            assertEquals(DataType.Scalar(ScalarKind.Text), registry.describe(typeOf<BeanFixtures.Side>()).structural)
            assertEquals(DataType.Scalar(ScalarKind.Text), registry.describe(typeOf<Suit>()).structural)
            val side = registry.lift(BeanFixtures.Side.SELL)
            assertEquals(TextExecutionValue("SELL"), side.access.scalar(side.root))
            val withBody = registry.lift(Suit.HEARTS)
            assertEquals(TextExecutionValue("HEARTS"), withBody.access.scalar(withBody.root))
            assertEquals("HEARTS", withBody.access.readText(withBody.root))

            assertEquals(
                DataType.Listing(DataType.Scalar(ScalarKind.Integer(32))),
                registry.describe(typeOf<Set<Int>>()).structural)
            val set = registry.lift(linkedSetOf(3, 1, 2))
            assertIs<DataType.Listing>(set.type)
            assertEquals(3, set.access.size(set.root))
            val seen = (0 until 3).map { set.access.readLong(set.access.element(set.root, it)) }
            assertEquals(listOf(3L, 1L, 2L), seen)
            assertEquals(seen, (0 until 3).map { set.access.readLong(set.access.element(set.root, it)) }, "one snapshot per node")
        }
    }


    @Test
    fun nativeTokensAreCachedPerClassIdentityAndStayLoaderLocal() {
        val compiled = compileClasses(mapOf(
            "fixture.Twin" to "package fixture; public class Twin { public String getLabel() { return \"twin\"; } }"))
        URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { first ->
            URLClassLoader(arrayOf(compiled.toURI().toURL()), javaClass.classLoader).use { second ->
                DefaultDataAdapterRegistry().use { registry ->
                    val a = first.loadClass("fixture.Twin").getDeclaredConstructor().newInstance()
                    val b = second.loadClass("fixture.Twin").getDeclaredConstructor().newInstance()
                    val liftedA = registry.lift(a)
                    val liftedB = registry.lift(b)
                    val tokenA = (liftedA.access as JvmNativeValueAccess).nativeType(liftedA.root)!!
                    val tokenB = (liftedB.access as JvmNativeValueAccess).nativeType(liftedB.root)!!
                    assertEquals(tokenA.type.classifier, (registry.lift(a).access as JvmNativeValueAccess).nativeType(liftedA.root)!!.type.classifier)
                    assertNotEquals(tokenA.type.classifier, tokenB.type.classifier, "same name, different loader")
                    assertEquals("twin", readText(liftedA, "label"))
                    assertEquals("twin", readText(liftedB, "label"))
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    class PlainKotlin(val label: String?, val size: Int) {
        val tags: List<String> get() = listOf("k")
    }

    enum class Suit {
        HEARTS { override fun colour() = "red" },
        SPADES { override fun colour() = "black" };
        abstract fun colour(): String
    }


    private fun readText(value: DataValue, field: String): String =
        value.access.readText(value.access.field(value.root, FieldId(field)))


    private fun compileClasses(sources: Map<String, String>): File {
        val root = Files.createTempDirectory("kzen-bean-shape-").toFile()
        val compiler = ToolProvider.getSystemJavaCompiler()
        val units = sources.map { (name, source) ->
            object: SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"),
                javax.tools.JavaFileObject.Kind.SOURCE
            ) {
                override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = source
            }
        }
        val task = compiler.getTask(null, null, null, listOf("-d", root.absolutePath), null, units)
        check(task.call()) { "fixture compilation failed" }
        return root
    }
}
