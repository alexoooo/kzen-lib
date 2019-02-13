package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.util.Digest
import java.util.*


class DigestCollisionTest {
    @Test
    fun collisionDetector() {
        val seen = mutableSetOf<Digest>()

        val random = Random()
        val start = random.nextInt()

        for (i in 1 .. 1_000_000) {
            val value = start + i
//            val value = random.nextDouble()

            val added = seen.add(digest(value.toString()))
            check(added) {"Collision found: $i"}
        }
    }


    private fun digest(value: String): Digest =
            Digest.ofXoShiRo256StarStar(value)
}
