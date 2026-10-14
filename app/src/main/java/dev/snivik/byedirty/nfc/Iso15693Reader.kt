package dev.snivik.byedirty.nfc

import android.nfc.Tag
import android.nfc.tech.NfcV
import dev.snivik.byedirty.data.TagDump
import java.io.IOException

/**
 * Reads the raw memory of an ISO 15693 (NFC-V) tag.
 *
 * The shower head uses a large dynamic tag (Capability Container advertises 8 KB), so blocks
 * past 0xFF need the *extended* read commands; the plain ones only take an 8-bit address and
 * silently wrap around, which is why off-the-shelf dumps repeat after block 0xFF.
 */
object Iso15693Reader {

    private const val FLAG_HIGH_DATA_RATE = 0x02
    private const val FLAG_ADDRESSED = 0x20

    private const val CMD_READ_SINGLE = 0x20
    private const val CMD_READ_MULTIPLE = 0x23
    private const val CMD_GET_SYSTEM_INFO = 0x2B
    private const val CMD_EXT_READ_SINGLE = 0x30
    private const val CMD_EXT_READ_MULTIPLE = 0x33
    private const val CMD_EXT_GET_SYSTEM_INFO = 0x3B

    /** Blocks per Read Multiple request; keeps responses well under the transceive limit. */
    private const val CHUNK = 32

    /** Never try to read more than this many blocks (ST25DV64K has 2048). */
    private const val MAX_BLOCKS = 2048

    /** Blocks before the log: NDEF, identity, config and live counters. */
    private const val HEADER_BLOCKS = 0x100

    private class SystemInfo(val blockCount: Int?, val blockSize: Int?, val icReference: Int?)

    /**
     * Reads the first 256 blocks, then either the rest of the chip ([fullMemory]) or just the
     * blocks [extraBlocks] asks for after looking at that header. Unread blocks are null.
     */
    fun read(
        tag: Tag,
        fullMemory: Boolean,
        extraBlocks: (header: TagDump) -> List<Int> = { emptyList() },
        onProgress: (Float) -> Unit = {},
    ): TagDump {
        val nfcV = NfcV.get(tag) ?: throw IOException("This isn't an ISO 15693 tag")
        nfcV.use { v ->
            v.connect()
            val session = Session(v, tag.id)
            val info = session.systemInfo()
            val blockSize = info?.blockSize ?: 4
            val total = (info?.blockCount ?: session.probeBlockCount()).coerceAtMost(MAX_BLOCKS)
            val blocks = arrayOfNulls<ByteArray>(total)
            fun dump() = TagDump(tag.id, info?.icReference, blockSize, blocks.toList(), System.currentTimeMillis())

            val header = 0 until minOf(HEADER_BLOCKS, total)
            // Progress is a guess until the header tells us how much else to read.
            session.readBlocks(header.toList(), blockSize, blocks) { onProgress(it * 0.4f) }
            val rest = if (fullMemory) (HEADER_BLOCKS until total).toList()
            else extraBlocks(dump()).filter { it in HEADER_BLOCKS until total }
            session.readBlocks(rest, blockSize, blocks) { onProgress(0.4f + it * 0.6f) }
            onProgress(1f)
            return dump()
        }
    }

    private class Session(private val v: NfcV, private val uid: ByteArray) {
        private var flags = FLAG_HIGH_DATA_RATE or FLAG_ADDRESSED

        private fun command(code: Int, vararg params: Int, paramsBeforeUid: Boolean = false): ByteArray {
            val p = ByteArray(params.size) { params[it].toByte() }
            val head = byteArrayOf(flags.toByte(), code.toByte())
            val addressed = flags and FLAG_ADDRESSED != 0
            return when {
                !addressed -> head + p
                paramsBeforeUid -> head + p + uid
                else -> head + uid + p
            }
        }

        private fun transceive(cmd: ByteArray, retries: Int = 2): ByteArray {
            var last: IOException? = null
            repeat(retries + 1) {
                try {
                    val resp = v.transceive(cmd)
                    if (resp.isEmpty()) throw IOException("Empty response")
                    if (resp[0].toInt() and 0x01 != 0) {
                        // Tag answered with an error code; retrying won't change that.
                        throw TagErrorException(resp.getOrNull(1)?.toInt()?.and(0xFF) ?: -1)
                    }
                    return resp.copyOfRange(1, resp.size)
                } catch (e: TagErrorException) {
                    throw e
                } catch (e: IOException) {
                    last = e
                }
            }
            throw last ?: IOException("Transceive failed")
        }

        fun systemInfo(): SystemInfo? {
            // Some Android stacks dislike addressed mode, so fall back to non-addressed.
            for (f in listOf(FLAG_HIGH_DATA_RATE or FLAG_ADDRESSED, FLAG_HIGH_DATA_RATE)) {
                flags = f
                extendedSystemInfo()?.let { return it }
                legacySystemInfo()?.let { return it }
            }
            flags = FLAG_HIGH_DATA_RATE or FLAG_ADDRESSED
            return null
        }

        /** Extended Get System Info: reports 16-bit block counts. Request DSFID, AFI, size, IC ref. */
        private fun extendedSystemInfo(): SystemInfo? = runCatching {
            val r = transceive(command(CMD_EXT_GET_SYSTEM_INFO, 0x0F, paramsBeforeUid = true), retries = 0)
            val infoFlags = r[0].toInt()
            var i = 1 + 8
            if (infoFlags and 0x01 != 0) i++ // DSFID
            if (infoFlags and 0x02 != 0) i++ // AFI
            var count: Int? = null
            var size: Int? = null
            if (infoFlags and 0x04 != 0) {
                count = ((r[i].toInt() and 0xFF) or ((r[i + 1].toInt() and 0xFF) shl 8)) + 1
                size = (r[i + 2].toInt() and 0x1F) + 1
                i += 3
            }
            val ic = if (infoFlags and 0x08 != 0) r[i].toInt() and 0xFF else null
            SystemInfo(count, size, ic)
        }.getOrNull()

        private fun legacySystemInfo(): SystemInfo? = runCatching {
            val r = transceive(command(CMD_GET_SYSTEM_INFO), retries = 0)
            val infoFlags = r[0].toInt()
            var i = 1 + 8
            if (infoFlags and 0x01 != 0) i++
            if (infoFlags and 0x02 != 0) i++
            var count: Int? = null
            var size: Int? = null
            if (infoFlags and 0x04 != 0) {
                count = (r[i].toInt() and 0xFF) + 1
                size = (r[i + 1].toInt() and 0x1F) + 1
                i += 2
            }
            val ic = if (infoFlags and 0x08 != 0) r[i].toInt() and 0xFF else null
            SystemInfo(count, size, ic)
        }.getOrNull()

        /** No system info: assume 256 blocks, then see whether extended addressing reaches further. */
        fun probeBlockCount(): Int {
            var count = 0x100
            while (count < MAX_BLOCKS && runCatching { readSingle(count) }.isSuccess) count += 0x100
            return count
        }

        private fun readSingle(block: Int): ByteArray =
            if (block < 0x100) transceive(command(CMD_READ_SINGLE, block))
            else transceive(command(CMD_EXT_READ_SINGLE, block and 0xFF, block shr 8))

        private fun readMultiple(first: Int, count: Int): ByteArray =
            if (first < 0x100) transceive(command(CMD_READ_MULTIPLE, first, count - 1))
            else transceive(command(CMD_EXT_READ_MULTIPLE, first and 0xFF, first shr 8, (count - 1) and 0xFF, (count - 1) shr 8))

        /** Reads [wanted] (sorted) into [into], batching contiguous runs into multi-block requests. */
        fun readBlocks(wanted: List<Int>, blockSize: Int, into: Array<ByteArray?>, onProgress: (Float) -> Unit) {
            var done = 0
            var i = 0
            while (i < wanted.size) {
                val start = wanted[i]
                var count = 1
                // Never let a request straddle 0xFF/0x100: the two halves use different commands.
                while (i + count < wanted.size && wanted[i + count] == start + count && count < CHUNK &&
                    (start >= 0x100 || start + count < 0x100)
                ) count++
                readRange(start, count, blockSize).forEachIndexed { k, b -> into[start + k] = b }
                i += count
                done += count
                onProgress(done.toFloat() / wanted.size)
            }
        }

        fun readRange(first: Int, count: Int, blockSize: Int): List<ByteArray?> {
            runCatching { readMultiple(first, count) }.getOrNull()
                ?.takeIf { it.size == count * blockSize }
                ?.let { data -> return List(count) { data.copyOfRange(it * blockSize, (it + 1) * blockSize) } }

            // Multi-block read can fail on protected areas; go block by block so we keep what we can.
            return List(count) { i ->
                try {
                    readSingle(first + i).takeIf { it.size == blockSize }
                } catch (e: TagErrorException) {
                    null
                }
            }
        }
    }

    class TagErrorException(val code: Int) : IOException("Tag error 0x%02X".format(code))
}
