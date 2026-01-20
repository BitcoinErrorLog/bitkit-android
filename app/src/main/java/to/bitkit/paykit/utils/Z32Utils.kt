package to.bitkit.paykit.utils

private const val Z32_ALPHABET = "ybndrfg8ejkmcpqxot1uwisza345h769"

fun z32Decode(z32: String): ByteArray {
    val normalized = z32.lowercase()

    val lookup = IntArray(128) { -1 }
    for (i in Z32_ALPHABET.indices) {
        lookup[Z32_ALPHABET[i].code] = i
    }

    val bits = normalized.map { c ->
        val value = lookup.getOrElse(c.code) { -1 }
        require(value >= 0) { "Invalid z32 character: $c" }
        value
    }

    val result = ByteArray((bits.size * 5) / 8)
    var bitBuffer = 0
    var bitsInBuffer = 0
    var outIndex = 0

    for (value in bits) {
        bitBuffer = (bitBuffer shl 5) or value
        bitsInBuffer += 5

        if (bitsInBuffer >= 8) {
            bitsInBuffer -= 8
            result[outIndex++] = ((bitBuffer shr bitsInBuffer) and 0xFF).toByte()
        }
    }

    return result
}
