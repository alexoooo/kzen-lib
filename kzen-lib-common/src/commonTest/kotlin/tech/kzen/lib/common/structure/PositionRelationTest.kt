package tech.kzen.lib.common.structure

import tech.kzen.lib.common.model.structure.notation.PositionRelation
import kotlin.test.Test
import kotlin.test.assertEquals


class PositionRelationTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun firstOfEmptyAtZero() {
        assertEquals(0, PositionRelation.first.resolve(0).value)
    }


    @Test
    fun lastOfEmptyAtZero() {
        assertEquals(0, PositionRelation.last.resolve(0).value)
    }


    @Test
    fun afterLastOfEmptyAtZero() {
        assertEquals(0, PositionRelation.afterLast.resolve(0).value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun firstOfSingleAtZero() {
        assertEquals(0, PositionRelation.first.resolve(1).value)
    }


    @Test
    fun lastOfSingleAtZero() {
        assertEquals(0, PositionRelation.last.resolve(1).value)
    }


    @Test
    fun afterLastOfSingleAtOne() {
        assertEquals(1, PositionRelation.afterLast.resolve(1).value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun firstOfThreeAtZero() {
        assertEquals(0, PositionRelation.first.resolve(3).value)
    }


    @Test
    fun lastOfThreeAtTwo() {
        assertEquals(2, PositionRelation.last.resolve(3).value)
    }


    @Test
    fun afterLastOfThreeAtTwo() {
        assertEquals(3, PositionRelation.afterLast.resolve(3).value)
    }
}