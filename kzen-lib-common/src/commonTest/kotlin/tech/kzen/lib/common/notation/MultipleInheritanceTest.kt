package tech.kzen.lib.common.notation

import tech.kzen.lib.common.model.attribute.AttributeName
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * Multiple inheritance: 'is' may be a non-empty list of parent references (mix-ins).
 * The linearized chain is most-derived first, primary parent ahead of mix-ins, with shared
 * ancestors deduplicated to the end (C3-like, via keep-last).
 */
class MultipleInheritanceTest: StructuralNotationTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun inheritsFromBothParents() {
        val graph = parseGraph("""
PrimaryParent:
  alpha: fromPrimary
MixinParent:
  beta: fromMixin
Combined:
  is:
    - PrimaryParent
    - MixinParent
""")

        assertEquals("fromPrimary", graph.getString(location("Combined"), attribute("alpha")))
        assertEquals("fromMixin", graph.getString(location("Combined"), attribute("beta")))

        val merged = graph.mergeObject(location("Combined"))!!
        assertEquals("fromPrimary", merged.get(AttributeName("alpha"))?.asString())
        assertEquals("fromMixin", merged.get(AttributeName("beta"))?.asString())
    }


    @Test
    fun primaryParentWinsOnConflict() {
        val graph = parseGraph("""
PrimaryParent:
  shared: fromPrimary
MixinParent:
  shared: fromMixin
Combined:
  is:
    - PrimaryParent
    - MixinParent
""")

        // NB: PrimaryParent appears before MixinParent in the linearized chain, so it wins
        assertEquals("fromPrimary", graph.getString(location("Combined"), attribute("shared")))
    }


    @Test
    fun ownAttributeOverridesBothParents() {
        val graph = parseGraph("""
PrimaryParent:
  shared: fromPrimary
MixinParent:
  shared: fromMixin
Combined:
  is:
    - PrimaryParent
    - MixinParent
  shared: fromSelf
""")

        assertEquals("fromSelf", graph.getString(location("Combined"), attribute("shared")))
    }


    @Test
    fun diamondDeduplicatesSharedAncestor() {
        val graph = parseGraph("""
Base:
  shared: fromBase
Left:
  is: Base
Right:
  is: Base
Diamond:
  is:
    - Left
    - Right
""")

        val chain = graph.inheritanceChain(location("Diamond"))
            .map { it.objectPath.asString() }
            .filter { it in setOf("Diamond", "Left", "Right", "Base") }

        // NB: shared ancestor Base appears exactly once, after both Left and Right
        assertEquals(listOf("Diamond", "Left", "Right", "Base"), chain)

        assertEquals("fromBase", graph.getString(location("Diamond"), attribute("shared")))
    }
}
