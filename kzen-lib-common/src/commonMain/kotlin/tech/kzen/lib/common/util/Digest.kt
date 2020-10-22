package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils


// NB: can't use Long in straight JSON transmission, see:
//  https://kotlinlang.org/docs/reference/js-to-kotlin-interop.html#representing-kotlin-types-in-javascript
data class Digest(
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        /**
         * Clear state, hashing any data will never produce this
         */
        val zero = Digest(0, 0, 0, 0)

        /**
         * Initial state, result of digesting zero bytes or empty string
         */
        val empty = Digest(1, 0, 0, 0)

        /**
         * Data to be digested is absent or missing, e.g. null
         */
        val missing = Digest(Int.MIN_VALUE, 0, 0, 0)


        fun fromBytes(bytes: ByteArray): Digest {
            val a =
                bytes[0].toInt() shl 24 or
                (bytes[1].toInt() and 255 shl 16) or
                (bytes[2].toInt() and 255 shl 8) or
                (bytes[3].toInt() and 255)

            val b =
                bytes[4].toInt() shl 24 or
                (bytes[5].toInt() and 255 shl 16) or
                (bytes[6].toInt() and 255 shl 8) or
                (bytes[7].toInt() and 255)

            val c =
                bytes[8].toInt() shl 24 or
                (bytes[9].toInt() and 255 shl 16) or
                (bytes[10].toInt() and 255 shl 8) or
                (bytes[11].toInt() and 255)

            val d =
                bytes[12].toInt() shl 24 or
                (bytes[13].toInt() and 255 shl 16) or
                (bytes[14].toInt() and 255 shl 8) or
                (bytes[15].toInt() and 255)

            return Digest(a, b, c, d)
        }


        fun ofUtf8(utf8: String?): Digest {
            if (utf8 == null) {
                return missing
            }

            val bytes = IoUtils.utf8Encode(utf8)
            return ofBytes(bytes)
        }


        fun ofBytes(bytes: ByteArray?): Digest {
            if (bytes == null) {
                return missing
            }
            if (bytes.isEmpty()) {
                return empty
            }

            @Suppress("RedundantExplicitType")
            var s0: Int = Int.MAX_VALUE
            var s1: Int = murmurHash3(bytes.size)
            var s2: Int = hashMapHash(bytes.size)
            var s3: Int = guavaHashingSmear(bytes.size)

            for (b in bytes) {
                s0 = s0 * 37 xor guavaHashingSmear(b.toInt())

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


        fun ofInt(value: Int): Digest {
            val a = Int.MAX_VALUE
            val b = murmurHash3(value)
            val c = hashMapHash(value)
            val d = guavaHashingSmear(value)
            return Digest(a, b, c, d)
        }


        fun parse(asString: String): Digest {
            val parts = asString.split('_')
            return Digest(
                parts[0].toInt(),
                parts[1].toInt(),
                parts[2].toInt(),
                parts[3].toInt()
            )
        }


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


        private fun hashMapHash(value: Int): Int {
            return value xor value.ushr(16)
        }


        private fun guavaHashingSmear(value: Int): Int {
            return 461845907 * rotl(value * -862048943, 15)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Builder {
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


        fun addMissing(): Builder {
            addDigest(missing)
            return this
        }


        fun addBoolean(value: Boolean): Builder {
            if (value) {
                addInt(1)
            }
            else {
                addInt(-1)
            }
            return this
        }


        fun addBooleanNullable(value: Boolean?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addBoolean(value)
            }
            return this
        }


        fun addByte(value: Byte): Builder {
            addInt(value.toInt())
            return this
        }


        fun addByteNullable(value: Byte?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addByte(value)
            }
            return this
        }


        fun addChar(value: Char): Builder {
            addInt(value.toInt())
            return this
        }


        fun addCharNullable(value: Char?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addChar(value)
            }
            return this
        }


        fun addShort(value: Short): Builder {
            addInt(value.toInt())
            return this
        }

        fun addShortNullable(value: Short?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addShort(value)
            }
            return this
        }


        fun addDouble(value: Double): Builder {
            addLong(value.toBits())
            return this
        }


        fun addDoubleNullable(value: Double?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addDouble(value)
            }
            return this
        }


        fun addLong(value: Long): Builder {
            // https://stackoverflow.com/a/12772968/1941359
            addInt(value.toInt())
            addInt((value shr Int.SIZE_BITS).toInt())
            return this
        }


        fun addLongNullable(value: Long?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addLong(value)
            }
            return this
        }


        fun addDigest(digest: Digest): Builder {
            addInt(digest.a)
            addInt(digest.b)
            addInt(digest.c)
            addInt(digest.d)
            return this
        }


        fun addDigestNullable(digest: Digest?): Builder {
            if (digest == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addDigest(digest)
            }
            return this
        }


        fun addDigestible(digestible: Digestible): Builder {
            digestible.digest(this)
            return this
        }


        fun addDigestibleNullable(digestible: Digestible?): Builder {
            if (digestible == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addDigestible(digestible)
            }
            return this
        }


        fun addDigestibleList(digestibleList: List<Digestible>) {
            addInt(digestibleList.size)
            for (digestible in digestibleList) {
                digestible.digest(this)
            }
        }


        fun addDigestibleUnorderedList(digestibleList: List<Digestible>) {
            addInt(digestibleList.size)

            val unorderedCombiner = UnorderedCombiner()
            val valueDigester = Builder()

            for (value in digestibleList) {
                valueDigester.clear()
                valueDigester.addDigestible(value)
                unorderedCombiner.add(valueDigester.digest())
            }

            addDigest(unorderedCombiner.combine())
        }



        fun addDigestibleOrderedSet(digestibleSet: Set<Digestible>) {
            addInt(digestibleSet.size)
            for (value in digestibleSet) {
                addDigestible(value)
            }
        }


        fun addDigestibleUnorderedSet(digestibleSet: Set<Digestible>) {
            addInt(digestibleSet.size)

            val unorderedCombiner = UnorderedCombiner()
            val valueDigester = Builder()

            for (value in digestibleSet) {
                valueDigester.clear()
                valueDigester.addDigestible(value)
                unorderedCombiner.add(valueDigester.digest())
            }

            addDigest(unorderedCombiner.combine())
        }


        fun addDigestibleOrderedMap(digestibleMap: Map<out Digestible, Digestible>) {
            addInt(digestibleMap.size)
            for ((key, value) in digestibleMap) {
                addDigestible(key)
                addDigestible(value)
            }
        }


        fun addDigestibleUnorderedMap(digestibleMap: Map<out Digestible, Digestible>) {
            addInt(digestibleMap.size)

            val unorderedCombiner = UnorderedCombiner()
            val entryDigester = Builder()

            for ((key, value) in digestibleMap) {
                entryDigester.clear()

                entryDigester.addDigestible(key)
                entryDigester.addDigestible(value)

                unorderedCombiner.add(entryDigester.digest())
            }

            addDigest(unorderedCombiner.combine())
        }


        fun addUtf8(utf8: String): Builder {
            val bytes = IoUtils.utf8Encode(utf8)
            addBytes(bytes)
            return this
        }


        fun addUtf8Nullable(utf8: String?): Builder {
            if (utf8 == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addUtf8(utf8)
            }
            return this
        }


        fun addBytes(bytes: ByteArray): Builder {
            addInt(bytes.size)
            bytes.forEach {
                addByte(it)
            }
            return this
        }


        fun addBytesNullable(bytes: ByteArray?): Builder {
            if (bytes == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addBytes(bytes)
            }
            return this
        }


        fun addInt(value: Int): Builder {
            if (isZero()) {
                init(value)
            }
            else {
                s0 = s0 * 37 xor guavaHashingSmear(value)

                val t = s1 shl 9

                s2 = s2 xor s0
                s3 = s3 xor s1
                s1 = s1 xor s2
                s0 = s0 xor s3

                s2 = s2 xor t

                s3 = rotl(s3, 11)
            }
            return this
        }


        fun addIntNullable(value: Int?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addInt(value)
            }
            return this
        }


        private fun init(value: Int) {
            s0 = Int.MAX_VALUE
            s1 = murmurHash3(value)
            s2 = hashMapHash(value)
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
                    empty
                }
                else {
                    Digest(s0, s1, s2, s3)
                }
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Combiner {
        /**
         * Reset state to zero
         */
        fun clear()


        /**
         * Update state to incorporate given digest
         * @digest to be incorporated
         */
        fun add(digest: Digest)


        /**
         * Current state
         * @return accumulated combination of add added digests
         */
        fun combine(): Digest
    }


    class UnorderedCombiner: Combiner {
        private var a: Int = 0
        private var b: Int = 0
        private var c: Int = 0
        private var d: Int = 0


        override fun clear() {
            a = 0
            b = 0
            c = 0
            d = 0
        }


        override fun add(digest: Digest) {
            a += digest.a
            b += digest.b
            c += digest.c
            d += digest.d
        }


        override fun combine(): Digest {
            return Digest(a, b, c, d)
        }
    }


    class OrderedCombiner: Combiner {
        private var a: Int = 0
        private var b: Int = 0
        private var c: Int = 0
        private var d: Int = 0


        override fun clear() {
            a = 0
            b = 0
            c = 0
            d = 0
        }


        override fun add(digest: Digest) {
            a = a * 37 xor digest.a
            b = b * 37 xor digest.b
            c = c * 37 xor digest.c
            d = d * 37 xor digest.d
        }


        override fun combine(): Digest {
            return Digest(a, b, c, d)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(): Digest {
        return this
    }


    override fun digest(builder: Builder) {
        builder.addDigest(this)
    }


    fun asString(): String {
        return "${a}_${b}_${c}_$d"
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toByteArray(): ByteArray {
        return byteArrayOf(
            (a shr 24).toByte(),
            (a shr 16).toByte(),
            (a shr 8).toByte(),
            a.toByte(),

            (b shr 24).toByte(),
            (b shr 16).toByte(),
            (b shr 8).toByte(),
            b.toByte(),

            (c shr 24).toByte(),
            (c shr 16).toByte(),
            (c shr 8).toByte(),
            c.toByte(),

            (d shr 24).toByte(),
            (d shr 16).toByte(),
            (d shr 8).toByte(),
            d.toByte()
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}
