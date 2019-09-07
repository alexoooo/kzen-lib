package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.util.Digest
import java.util.*


class DigestCollisionTest {
    @Test
    fun streamRepeatedIntegers() {
        val seen = mutableSetOf<Digest>()

        val value = Random().nextInt()
//        val value = 0

        val digest = Digest.Builder()

        for (i in 1 .. 1_000_000) {
            val current = digest.addInt(value).digest()
//            println(current)

            val added = seen.add(current)
            check(added) {"Collision found: $i"}
        }
    }


    @Test
    fun consecutiveNumbers() {
        val seen = mutableSetOf<Digest>()

        val random = Random()
        val start = random.nextInt()
        val digest = Digest.Builder()

        for (i in 1 .. 1_000_000) {
            val value = start + i

            val added = seen.add(digest.addInt(value).digest())
            check(added) {"Collision found: $i"}

            digest.clear()
        }
    }


    @Test
    fun consecutiveAsciiNumbers() {
        val seen = mutableSetOf<Digest>()

        val random = Random()
        val start = random.nextInt()

        for (i in 1 .. 1_000_000) {
            val value = start + i

            val added = seen.add(digest(value.toString()))
            check(added) {"Collision found: $i"}
        }
    }


    private fun digest(value: String): Digest =
            Digest.ofUtf8(value)
}
