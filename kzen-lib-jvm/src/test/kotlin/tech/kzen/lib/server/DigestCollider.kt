package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.IoUtils
import java.util.*


class CommonTest {

    @Test
    fun collisionDetector() {
        val seen = mutableSetOf<Digest>()

        val start = Random().nextInt()

        for (i in 1 .. 1_000_000) {
            val value = start + i
            val added = seen.add(digest(value.toString()))
            check(added, {"Collision found: $i"})
        }
    }



    private fun digest(value: String): Digest =
            Digest.ofXoShiRo256StarStar(IoUtils.stringToUtf8(value))
}
