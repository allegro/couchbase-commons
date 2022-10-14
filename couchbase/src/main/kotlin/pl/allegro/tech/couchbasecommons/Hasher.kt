package pl.allegro.tech.couchbasecommons

import java.security.MessageDigest

fun String.sha256() = hashString("SHA-256", this)

private const val HEX_CHARS = "0123456789abcdef"

private const val SHIFT = 4

private fun hashString(type: String, input: String): String {
    val bytes = MessageDigest
        .getInstance(type)
        .digest(input.toByteArray())
    val result = StringBuilder(bytes.size * 2)

    bytes.forEach {
        val i = it.toInt()
        result.append(HEX_CHARS[i shr SHIFT and 0x0f])
        result.append(HEX_CHARS[i and 0x0f])
    }

    return result.toString()
}
