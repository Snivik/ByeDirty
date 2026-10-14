package dev.snivik.byedirty.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Raw memory image of an ISO 15693 tag. A null block wasn't read: skipped by a quick sync,
 * or refused by the tag (e.g. a password-protected area).
 */
class TagDump(
    /** UID exactly as Android reports it (LSB first). */
    val uid: ByteArray,
    val icReference: Int?,
    val blockSize: Int,
    val blocks: List<ByteArray?>,
    val readAtMillis: Long,
) {
    val blockCount: Int get() = blocks.size
    val readCount: Int get() = blocks.count { it != null }

    /** UID in the conventional MSB-first order, e.g. "E0:02:...". */
    val uidDisplay: String get() = uid.reversedArray().toHex(":")

    /** Second UID byte (MSB first) is the ISO 7816-6 manufacturer code. */
    val manufacturer: String
        get() = when (uid.reversedArray().getOrNull(1)?.toInt()?.and(0xFF)) {
            0x02 -> "STMicroelectronics"
            0x04 -> "NXP"
            0x07 -> "Texas Instruments"
            0x16 -> "EM Microelectronic"
            null -> "Unknown"
            else -> "Unknown (0x%02X)".format(uid.reversedArray()[1])
        }

    fun block(index: Int): ByteArray? = blocks.getOrNull(index)

    /** Byte at absolute memory [offset], or null when unread. */
    fun byteAt(offset: Int): Int? = block(offset / blockSize)?.getOrNull(offset % blockSize)?.toInt()?.and(0xFF)

    /** Same layout as the "export.txt" produced by NFC Tools, plus a commented header. */
    fun toText(label: String? = null): String = buildString {
        appendLine("# ByeDirty dump")
        label?.let { appendLine("# Label: $it") }
        appendLine("# UID: $uidDisplay ($manufacturer)")
        icReference?.let { appendLine("# IC reference: 0x%02X".format(it)) }
        appendLine("# Blocks: $blockCount x $blockSize bytes")
        appendLine("# Read at: ${java.time.Instant.ofEpochMilli(readAtMillis)}")
        val addrWidth = if (blockCount > 0x100) 3 else 2
        blocks.forEachIndexed { i, b ->
            val addr = i.toString(16).uppercase().padStart(addrWidth, '0')
            if (b == null) appendLine("[ ${List(blockSize) { "??" }.joinToString(":")} ] Addr. $addr : NOT READ")
            else appendLine("[ ${b.toHex(":")} ] Addr. $addr : DATA")
        }
    }

    fun toJson(): String = JSONObject()
        .put("uid", uid.toHex())
        .put("ic", icReference ?: -1)
        .put("blockSize", blockSize)
        .put("readAt", readAtMillis)
        .put("blocks", JSONArray(blocks.map { it?.toHex() ?: "" }))
        .toString()

    companion object {
        fun fromJson(json: String): TagDump {
            val o = JSONObject(json)
            val arr = o.getJSONArray("blocks")
            return TagDump(
                uid = o.getString("uid").hexToBytes(),
                icReference = o.getInt("ic").takeIf { it >= 0 },
                blockSize = o.getInt("blockSize"),
                blocks = List(arr.length()) { i -> arr.getString(i).takeIf { it.isNotEmpty() }?.hexToBytes() },
                readAtMillis = o.getLong("readAt"),
            )
        }

        private val LINE = Regex("""\[\s*([0-9A-Fa-f?:]+)\s*]\s*Addr\.\s*([0-9A-Fa-f]+)""")

        /** Parses NFC Tools style text dumps ("[ E2:40:00:00 ] Addr. 00 : DATA"). */
        fun parseText(text: String, uid: ByteArray = ByteArray(8)): TagDump {
            val byAddr = sortedMapOf<Int, ByteArray?>()
            var blockSize = 4
            for (line in text.lineSequence()) {
                val m = LINE.find(line) ?: continue
                val parts = m.groupValues[1].split(':')
                blockSize = parts.size
                byAddr[m.groupValues[2].toInt(16)] =
                    if (parts.any { it.contains('?') }) null else ByteArray(parts.size) { parts[it].toInt(16).toByte() }
            }
            val count = (byAddr.keys.maxOrNull() ?: -1) + 1
            return TagDump(uid, null, blockSize, List(count) { byAddr[it] }, 0L)
        }
    }
}

fun ByteArray.toHex(separator: String = ""): String =
    joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

fun String.hexToBytes(): ByteArray =
    ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
