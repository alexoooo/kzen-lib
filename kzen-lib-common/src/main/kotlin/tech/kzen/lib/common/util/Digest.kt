package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils

// NB: can't use Long in straight JSON transmission, see:
//  https://kotlinlang.org/docs/reference/js-to-kotlin-interop.html#representing-kotlin-types-in-javascript
data class Digest(
        val a: Int,
        val b: Int,
        val c: Int,
        val d: Int
) {
    companion object {
        val zero = Digest(0, 0, 0, 0)
        val empty = Digest(1, 0, 0, 0)


        private fun rotl(x: Int, k: Int): Int {
            return x.shl(k) or (x ushr (32 - k))
        }

        private fun murmurHash3(value: Int): Int {
            var x = value
            x = x xor x.ushr(16)
            x *= -0x7a143595
            x = x xor x.ushr(13)
            x *= -0x3d4d51cb
            x = x xor x.ushr(16)
            return x
        }

        private fun hashCodeHash(value: Int): Int {
            return value xor value.ushr(16)
        }

        private fun guavaHashingSmear(value: Int): Int {
            return 461845907 * rotl(value * -862048943, 15)
        }


        fun ofXoShiRo256StarStar(utf8: String): Digest {
            val bytes = IoUtils.stringToUtf8(utf8)
            return ofXoShiRo256StarStar(bytes)
        }

        fun ofXoShiRo256StarStar(bytes: ByteArray): Digest {
            if (bytes.isEmpty()) {
                return empty
            }

            var s0: Int = 0
            var s1: Int = murmurHash3(bytes.size)
            var s2: Int = hashCodeHash(bytes.size)
            var s3: Int = guavaHashingSmear(bytes.size)

            for (b in bytes) {
                s0 += guavaHashingSmear(b.toInt())

                val t = s1 shl 9

                s2 = s2 xor s0
                s3 = s3 xor s1
                s1 = s1 xor s2
                s0 = s0 xor s3

                s2 = s2 xor t

                s3 = rotl(s3, 11)
            }

            return Digest(s0, s1, s2, s3)
        }


        fun decode(encoded: String): Digest {
            val parts = encoded.split('_')
            return Digest(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt(),
                    parts[3].toInt())
        }
    }


    class UnorderedCombiner {
        private var a: Int = 0
        private var b: Int = 0
        private var c: Int = 0
        private var d: Int = 0

        fun add(digest: Digest) {
            a += digest.a
            b += digest.b
            c += digest.c
            d += digest.d
        }

        fun combine(): Digest {
            return Digest(a, b, c, d)
        }
    }


    class OrderedCombiner {
        private var a: Int = 0
        private var b: Int = 0
        private var c: Int = 0
        private var d: Int = 0

        fun clear() {
            a = 0
            b = 0
            c = 0
            d = 0
        }

        fun add(digest: Digest) {
            a = a * 37 xor digest.a
            b = b * 37 xor digest.b
            c = c * 37 xor digest.c
            d = d * 37 xor digest.d
        }

        fun combine(): Digest {
            return Digest(a, b, c, d)
        }
    }


    class Streaming {
        private var s0: Int = 0
        private var s1: Int = 0
        private var s2: Int = 0
        private var s3: Int = 0


        fun clear() {
            s0 = 0
            s1 = 0
            s2 = 0
            s3 = 0
        }


        fun addUtf8(utf8: String) {
            val bytes = IoUtils.stringToUtf8(utf8)
            addBytes(bytes)
        }

        fun addBytes(bytes: ByteArray) {
            addInt(bytes.size)
            bytes.forEach(this@Streaming::addByte)
        }

        fun addByte(value: Byte) {
            addInt(value.toInt())
        }

        fun addInt(value: Int) {
            if (isZero()) {
                init(value)
            }
            else {
                s0 += guavaHashingSmear(value)

                val t = s1 shl 9

                s2 = s2 xor s0
                s3 = s3 xor s1
                s1 = s1 xor s2
                s0 = s0 xor s3

                s2 = s2 xor t

                s3 = rotl(s3, 11)
            }
        }


        private fun init(value: Int) {
            s1 = murmurHash3(value)
            s2 = hashCodeHash(value)
            s3 = guavaHashingSmear(value)
        }

        private fun isZero(): Boolean {
            return s0 == 0 &&
                    s1 == 0 &&
                    s2 == 0 &&
                    s3 == 0
        }


        fun digest(): Digest =
                if (isZero()) {
                    Digest.empty
                }
                else {
                    Digest(s0, s1, s2, s3)
                }
    }


    fun encode(): String {
        return "${a}_${b}_${c}_$d"
    }
}
