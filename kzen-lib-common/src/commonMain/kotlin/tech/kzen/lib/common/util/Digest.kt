package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils


// NB: can't use Long in straight JSON transmission, see:
//  https://kotlinlang.org/docs/reference/js-to-kotlin-interop.html#representing-kotlin-types-in-javascript
// See: http://prng.di.unimi.it/
// Consider: init with http://prng.di.unimi.it/splitmix64.c
// See: https://stackoverflow.com/questions/1835976/what-is-a-sensible-prime-for-hashcode-calculation
@Suppress("unused", "MemberVisibilityCanBePrivate")
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


        private const val stringRadix = 32
        private const val constantSeed: Int = 0x9e3779bb.toInt()


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


        fun build(builder: Sink.() -> Unit): Digest {
            val buffer = Builder()
            builder(buffer)
            return buffer.digest()
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

            var s0: Int = constantSeed
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

                s3 = rotateLeft(s3, 11)
            }

            return finalize(s0, s1, s2, s3)
        }


        fun ofInt(value: Int): Digest {
            val a = constantSeed
            val b = murmurHash3(value)
            val c = hashMapHash(value)
            val d = guavaHashingSmear(value)
            return finalize(a, b, c, d)
        }


        fun parse(asString: String): Digest {
            val parts = asString.split('_')
            return Digest(
                parts[0].toInt(stringRadix),
                parts[1].toInt(stringRadix),
                parts[2].toInt(stringRadix),
                parts[3].toInt(stringRadix)
            )
        }


        private fun rotateLeft(x: Int, k: Int): Int {
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
            return 461845907 * rotateLeft(value * -862048943, 15)
        }


        private fun finalize(a: Int, b: Int, c: Int, d: Int): Digest {
            var s0 = murmurHash3(a)
            val t = b shl 9
            var s2 = guavaHashingSmear(c xor s0)
            var s3 = murmurHash3(d xor b + 0x6fa035c3)
            val s1 = b xor s2
            s0 = s0 xor s3
            s2 = murmurHash3(s2 xor t)
            s3 = rotateLeft(s3, 11)
            return Digest(s0, s1, s2, s3)
        }


//        private fun finalizeLong(a: Int, b: Int, c: Int, d: Int): Long {
//            var s0 = murmurHash3(a)
//            val t = b shl 9
//            var s2 = guavaHashingSmear(c xor s0)
//            var s3 = murmurHash3(d xor b + 0x6fa035c3)
//            val s1 = b xor s2
//            s0 = s0 xor s3
//            s2 = murmurHash3(s2 xor t)
//            s3 = rotl(s3, 11)
//            return (s0.toLong() shl 32 or (s1.toLong() and 0xffffffffL)) * 92821 +
//                    (s2.toLong() shl 32 or (s3.toLong() and 0xffffffffL))
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    interface Sink {
        fun addMissing(): Sink

        fun addBoolean(value: Boolean): Sink
        fun addBooleanNullable(value: Boolean?): Sink

        fun addByte(value: Byte): Sink
        fun addByteNullable(value: Byte?): Sink

        fun addChar(value: Char): Sink
        fun addCharNullable(value: Char?): Sink

        fun addShort(value: Short): Sink
        fun addShortNullable(value: Short?): Sink

        fun addInt(value: Int): Sink
        fun addIntNullable(value: Int?): Sink

        fun addDouble(value: Double): Sink
        fun addDoubleNullable(value: Double?): Sink

        fun addLong(value: Long): Sink
        fun addLongNullable(value: Long?): Sink

        fun addBytes(value: ByteArray): Sink
        fun addBytesNullable(value: ByteArray?): Sink

        fun addUtf8(value: String): Sink
        fun addUtf8Nullable(value: String?): Sink

        fun addDigest(value: Digest): Sink
        fun addDigestNullable(value: Digest?): Sink

        fun addDigestible(value: Digestible): Sink
        fun addDigestibleNullable(value: Digestible?): Sink

        fun addDigestibleList(digestibleList: List<Digestible>): Sink
        fun addDigestibleUnorderedList(digestibleList: List<Digestible>): Sink
        fun addDigestibleOrderedSet(digestibleSet: Set<Digestible>): Sink
        fun addDigestibleUnorderedSet(digestibleSet: Set<Digestible>): Sink
        fun addDigestibleOrderedMap(digestibleMap: Map<out Digestible, Digestible>): Sink
        fun addDigestibleUnorderedMap(digestibleMap: Map<out Digestible, Digestible>): Sink

        fun <T> addCollection(collection: Collection<T>, digester: Sink.(T) -> Unit): Sink
        fun <T> addUnorderedCollection(collection: Collection<T>, digester: Sink.(T) -> Unit): Sink
    }


    class Builder: Sink {
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


        override fun addMissing(): Builder {
            addDigest(missing)
            return this
        }


        override fun addBoolean(value: Boolean): Builder {
            if (value) {
                addInt(1)
            }
            else {
                addInt(-1)
            }
            return this
        }


        override fun addBooleanNullable(value: Boolean?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addBoolean(value)
            }
            return this
        }


        override fun addByte(value: Byte): Builder {
            addInt(value.toInt())
            return this
        }


        override fun addByteNullable(value: Byte?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addByte(value)
            }
            return this
        }


        override fun addChar(value: Char): Builder {
            addInt(value.code)
            return this
        }


        override fun addCharNullable(value: Char?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addChar(value)
            }
            return this
        }


        override fun addShort(value: Short): Builder {
            addInt(value.toInt())
            return this
        }

        override fun addShortNullable(value: Short?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addShort(value)
            }
            return this
        }


        override fun addDouble(value: Double): Builder {
            addLong(value.toBits())
            return this
        }


        override fun addDoubleNullable(value: Double?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addDouble(value)
            }
            return this
        }


        override fun addLong(value: Long): Builder {
            // https://stackoverflow.com/a/12772968/1941359
            addInt(value.toInt())
            addInt((value shr Int.SIZE_BITS).toInt())
            return this
        }


        override fun addLongNullable(value: Long?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addLong(value)
            }
            return this
        }


        override fun addDigest(value: Digest): Builder {
            addInt(value.a)
            addInt(value.b)
            addInt(value.c)
            addInt(value.d)
            return this
        }


        override fun addDigestNullable(value: Digest?): Builder {
            if (value == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addDigest(value)
            }
            return this
        }


        override fun addDigestible(value: Digestible): Builder {
            value.digest(this)
            return this
        }


        override fun addDigestibleNullable(value: Digestible?): Builder {
            if (value == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addDigestible(value)
            }
            return this
        }


        override fun addDigestibleList(digestibleList: List<Digestible>): Builder {
            addInt(digestibleList.size)
            for (digestible in digestibleList) {
                digestible.digest(this)
            }
            return this
        }


        override fun addDigestibleUnorderedList(digestibleList: List<Digestible>): Builder {
            addInt(digestibleList.size)

            val unorderedCombiner = UnorderedCombiner()
            val valueDigester = Builder()

            for (value in digestibleList) {
                valueDigester.clear()
                valueDigester.addDigestible(value)
                unorderedCombiner.add(valueDigester.digest())
            }

            addDigest(unorderedCombiner.combine())
            return this
        }


        override fun addDigestibleOrderedSet(digestibleSet: Set<Digestible>): Builder {
            addInt(digestibleSet.size)
            for (value in digestibleSet) {
                addDigestible(value)
            }
            return this
        }


        override fun addDigestibleUnorderedSet(digestibleSet: Set<Digestible>): Builder {
            addInt(digestibleSet.size)

            val unorderedCombiner = UnorderedCombiner()
            val valueDigester = Builder()

            for (value in digestibleSet) {
                valueDigester.clear()
                valueDigester.addDigestible(value)
                unorderedCombiner.add(valueDigester.digest())
            }

            addDigest(unorderedCombiner.combine())
            return this
        }


        override fun addDigestibleOrderedMap(digestibleMap: Map<out Digestible, Digestible>): Builder {
            addInt(digestibleMap.size)
            for ((key, value) in digestibleMap) {
                addDigestible(key)
                addDigestible(value)
            }
            return this
        }


        override fun addDigestibleUnorderedMap(digestibleMap: Map<out Digestible, Digestible>): Builder {
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
            return this
        }


        override fun <T> addCollection(collection: Collection<T>, digester: Sink.(T) -> Unit): Builder {
            addInt(collection.size)
            for (element in collection) {
                digester(element)
            }
            return this
        }


        override fun <T> addUnorderedCollection(collection: Collection<T>, digester: Sink.(T) -> Unit): Builder {
            addInt(collection.size)

            val unorderedCombiner = UnorderedCombiner()
            val elementBuffer = Builder()

            for (element in collection) {
                elementBuffer.clear()
                digester(elementBuffer, element)
                unorderedCombiner.add(elementBuffer.digest())
            }

            addDigest(unorderedCombiner.combine())
            return this
        }


        override fun addUtf8(value: String): Builder {
            val bytes = IoUtils.utf8Encode(value)
            addBytes(bytes)
            return this
        }


        override fun addUtf8Nullable(value: String?): Builder {
            if (value == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addUtf8(value)
            }
            return this
        }


        override fun addBytes(value: ByteArray): Builder {
            addInt(value.size)
            value.forEach {
                addByte(it)
            }
            return this
        }


        override fun addBytesNullable(value: ByteArray?): Builder {
            if (value == null) {
                addBoolean(false)
            }
            else {
                addBoolean(true)
                addBytes(value)
            }
            return this
        }


        override fun addInt(value: Int): Builder {
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

                s3 = rotateLeft(s3, 11)
            }
            return this
        }


        override fun addIntNullable(value: Int?): Builder {
            if (value == null) {
                addMissing()
            }
            else {
                addInt(value)
            }
            return this
        }


        private fun init(value: Int) {
            s0 = constantSeed
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


        fun digest(): Digest {
            if (isZero()) {
                return empty
            }
            return finalize(s0, s1, s2, s3)
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


    override fun digest(sink: Sink) {
        sink.addDigest(this)
    }


    fun asString(): String {
        val aAsString = a.toString(stringRadix)
        val bAsString = b.toString(stringRadix)
        val cAsString = c.toString(stringRadix)
        val dAsString = d.toString(stringRadix)

        return "${aAsString}_${bAsString}_${cAsString}_${dAsString}"
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


    fun longHashCode(): Long {
        return (a.toLong() shl 32 or (b.toLong() and 0xffffffffL)) * 92821 +
                (c.toLong() shl 32 or (d.toLong() and 0xffffffffL))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }


    override fun hashCode(): Int {
        val longHashCode = longHashCode()
        return (longHashCode xor (longHashCode ushr 32)).toInt()
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Digest

        if (a != other.a) return false
        if (b != other.b) return false
        if (c != other.c) return false
        if (d != other.d) return false

        return true
    }
}
