package dev.snivik.byedirty.data

/**
 * Shower head data decoded from the tag (an ST25DV64K, 2048 blocks x 4 bytes).
 *
 * Layout worked out from labelled before/after scans (research/experiment) and confirmed field
 * by field against the HelloKlean app's own "send device data" export (Devices.json); field
 * names in the docs below are that export's names. Multi-byte numbers are little-endian. Most
 * live counters are stored twice side by side (tear-safe copies); we read the first.
 *
 * The head runs off a supercapacitor charged by the water flow: its clock only ticks while
 * water runs, so device timestamps are ordered but aren't real dates.
 *
 * Memory map:
 *  - 0x00-0x07  NDEF link to helloklean.com
 *  - 0x80-0x8F  identity (model, serial, versions, id)
 *  - 0x94-0x99  configuration
 *  - 0xA4-0xB5  live counters
 *  - 0x100-0x7FF  ring buffer of 256 x 28-byte shower records ([ShowerRecord])
 */
data class ShowerHeadReading(
    val ndefUrl: String?,
    /** 0x80-0x85 `model`, ASCII: "HKSSH001". */
    val model: String?,
    /** 0x86-0x8B `serialNumber`, ASCII: "TW26030001". */
    val serial: String?,
    /** 0x8C `hardwareVersion` / 0x8D `firmwareVersion`, ASCII "0001". */
    val hardwareVersion: String?,
    val firmwareVersion: String?,
    /** 0x8E `rtcID` and 0x8F `norFlashId`. */
    val deviceId: String?,

    /** 0x95 byte 0 `resetKeyHoldTime`: seconds to hold the reset button (5). */
    val resetHoldSeconds: Int?,
    /** 0x95 byte 1 `datalogCombinedInterval` (10); presumably the gap that still counts as one shower. */
    val datalogCombinedInterval: Int?,
    /** 0x95 bytes 2-3 `filterMaxWaterUsage`: 8000 L. */
    val filterCapacityLitres: Int?,
    /** 0x96 bytes 0-1 `filterElementReminder`: litres used before the head warns (6400). */
    val filterReminderLitres: Int?,
    /** 0x96 bytes 2-3 `highTempLEDReminder`: 450 = 45.0 degC warning light. */
    val hotWarningC: Double?,
    /** 0x97 `highTempTimingTemp` / `lowTempTimingTemp`: 42.0 / 25.0 degC, what counts as hot / cold. */
    val hotThresholdC: Double?,
    val coldThresholdC: Double?,

    /** 0xA4 `currentFilterWaterUsage`: litres through the current filter. */
    val filterUsedLitres: Int?,
    /** 0xA5 `remainingWaterUsage`: litres left on the current filter. */
    val filterRemainingLitres: Int?,
    /** 0xA6 `currentFilterWorkingTime`: seconds of flow through the current filter. */
    val filterFlowSeconds: Long?,
    /** 0xA8 `totalWorkingTime`: lifetime seconds of flow. */
    val lifetimeFlowSeconds: Long?,
    /** 0xAA `totalFiltersUsed`: filter resets so far; also the filter number in log records. */
    val filterNumber: Int?,
    /** 0xAB `currentNFCEEPROM`: records ever written to the log (sessions plus filter markers). */
    val recordsWritten: Int?,
    /** 0xB1 `currentTimestamp`: device clock at the last log update (see class docs). */
    val lastEventClock: Long?,
    /** 0xB3 `supercapacitorVoltage` in mV. */
    val supercapMillivolts: Int?,
    /** 0xB5: byte offset of the newest log record. */
    val newestRecordOffset: Int?,

    /** Log records, oldest first; only those whose blocks were read and whose checksum matches. */
    val records: List<ShowerRecord>,
) {
    val isSupportedShowerHead: Boolean get() = model?.startsWith("HK") == true


    companion object {
        const val LOG_START_BLOCK = 0x100
        const val RECORD_BYTES = 28
        const val RECORD_SLOTS = 256

        fun decode(dump: TagDump): ShowerHeadReading {
            val m = Mem(dump)
            val newest = m.u16(0xB5 * 4)
            return ShowerHeadReading(
                ndefUrl = parseNdefUri(dump),
                model = m.ascii(0x80, 2),
                serial = m.ascii(0x86, 3),
                hardwareVersion = m.ascii(0x8C, 1),
                firmwareVersion = m.ascii(0x8D, 1),
                deviceId = m.bytes(0x8E * 4, 8)?.takeIf { b -> b.any { it != 0.toByte() } }?.toHex(),
                resetHoldSeconds = m.u8(0x95 * 4),
                datalogCombinedInterval = m.u8(0x95 * 4 + 1),
                filterCapacityLitres = m.u16(0x95 * 4 + 2)?.takeIf { it > 0 },
                filterReminderLitres = m.u16(0x96 * 4)?.takeIf { it > 0 },
                hotWarningC = m.u16(0x96 * 4 + 2)?.takeIf { it > 0 }?.div(10.0),
                hotThresholdC = m.u16(0x97 * 4)?.takeIf { it > 0 }?.div(10.0),
                coldThresholdC = m.u16(0x97 * 4 + 2)?.takeIf { it > 0 }?.div(10.0),
                filterUsedLitres = m.u16(0xA4 * 4),
                filterRemainingLitres = m.u16(0xA5 * 4),
                filterFlowSeconds = m.u32(0xA6 * 4),
                lifetimeFlowSeconds = m.u32(0xA8 * 4),
                filterNumber = m.u16(0xAA * 4),
                recordsWritten = m.u16(0xAB * 4),
                lastEventClock = m.u32(0xB1 * 4)?.takeIf { it > 0 },
                supercapMillivolts = m.u16(0xB3 * 4),
                newestRecordOffset = newest,
                records = readLog(m, newest?.let(::slotOfOffset)),
            )
        }

        private fun slotOfOffset(offset: Int): Int? =
            ((offset - LOG_START_BLOCK * 4) / RECORD_BYTES).takeIf { offset >= LOG_START_BLOCK * 4 && it < RECORD_SLOTS }

        /** Blocks holding the [count] newest log records, so a quick sync can skip the rest of the chip. */
        fun recentRecordBlocks(header: TagDump, count: Int): List<Int> {
            val newest = Mem(header).u16(0xB5 * 4)?.let(::slotOfOffset) ?: return emptyList()
            return (0 until minOf(count, RECORD_SLOTS)).flatMap { back ->
                val slot = Math.floorMod(newest - back, RECORD_SLOTS)
                val start = LOG_START_BLOCK * 4 + slot * RECORD_BYTES
                (start / 4..(start + RECORD_BYTES - 1) / 4)
            }.distinct().sorted()
        }

        /** Reads the ring buffer oldest-to-newest, starting just after the newest slot. */
        private fun readLog(m: Mem, newestSlot: Int?): List<ShowerRecord> {
            val order = if (newestSlot == null) (0 until RECORD_SLOTS).toList()
            else (1..RECORD_SLOTS).map { Math.floorMod(newestSlot + it, RECORD_SLOTS) }
            return order.mapNotNull { slot ->
                m.bytes(LOG_START_BLOCK * 4 + slot * RECORD_BYTES, RECORD_BYTES)?.let(ShowerRecord::parse)
            }
        }

        private val URI_PREFIXES = listOf(
            "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        )

        /** Minimal Type 5 NDEF parse: CC, then the first NDEF TLV, then a URI record if present. */
        fun parseNdefUri(dump: TagDump): String? {
            val m = Mem(dump)
            val magic = m.u8(0) ?: return null
            if (magic != 0xE1 && magic != 0xE2) return null
            // An 8-byte CC is signalled by a zero memory-length byte.
            var i = if (m.u8(2) == 0) 8 else 4
            while (i < 0x200) {
                val t = m.u8(i) ?: return null
                if (t == 0xFE) return null
                if (t == 0x00) { i++; continue } // NULL TLV has no length field
                var len = m.u8(i + 1) ?: return null
                var body = i + 2
                if (len == 0xFF) {
                    len = ((m.u8(i + 2) ?: return null) shl 8) or (m.u8(i + 3) ?: return null)
                    body = i + 4
                }
                if (t != 0x03) { i = body + len; continue }
                val header = m.u8(body) ?: return null
                val typeLen = m.u8(body + 1) ?: return null
                val shortRecord = header and 0x10 != 0
                val payloadLen = if (shortRecord) m.u8(body + 2) ?: return null
                else m.u32be(body + 2)?.toInt() ?: return null
                val idLen = if (header and 0x08 != 0) m.u8(body + (if (shortRecord) 3 else 6)) ?: 0 else 0
                val typeStart = body + (if (shortRecord) 3 else 6) + (if (header and 0x08 != 0) 1 else 0)
                val type = m.bytes(typeStart, typeLen) ?: return null
                if (header and 0x07 != 0x01 || type.toString(Charsets.US_ASCII) != "U") return null
                val payload = m.bytes(typeStart + typeLen + idLen, payloadLen) ?: return null
                if (payload.isEmpty()) return null
                val prefix = URI_PREFIXES.getOrElse(payload[0].toInt() and 0xFF) { "" }
                return prefix + payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
            }
            return null
        }
    }

    private class Mem(private val dump: TagDump) {
        fun u8(offset: Int): Int? = dump.byteAt(offset)
        fun bytes(offset: Int, len: Int): ByteArray? {
            val out = ByteArray(len)
            for (k in 0 until len) out[k] = (u8(offset + k) ?: return null).toByte()
            return out
        }
        fun u16(offset: Int): Int? = bytes(offset, 2)?.le16(0)
        fun u32(offset: Int): Long? = bytes(offset, 4)?.le32(0)
        fun u32be(offset: Int): Long? {
            val b = bytes(offset, 4) ?: return null
            return (0..3).fold(0L) { acc, k -> (acc shl 8) or (b[k].toLong() and 0xFF) }
        }
        fun ascii(block: Int, blocks: Int): String? =
            bytes(block * 4, blocks * 4)
                ?.takeWhile { it != 0.toByte() }
                ?.toByteArray()?.toString(Charsets.US_ASCII)
                ?.takeIf { it.isNotBlank() }
    }
}

/**
 * One 28-byte log record (the export's `dataLogs`). The head writes one per shower, updating it
 * in place while water runs. A filter reset starts a new record with log number 1, which can
 * pick up a little water of its own.
 *
 * | byte  | field |
 * |-------|-------|
 * | 0     | `filterNumber` |
 * | 1-4   | `timestamp`, device clock at last update |
 * | 5-6   | `logNumber` within that filter, from 1 |
 * | 7-22  | `waterLogData`: (volume in 0.1 L, seconds) for each of 4 pressure bands; only "low" (1.5) and "medium" (3) seen in use |
 * | 23-24 | `highTemperatureTime`, seconds above the hot threshold |
 * | 25-26 | `lowTemperatureTime`, seconds below the cold threshold |
 * | 27    | `checksum`, low byte of the sum of bytes 0-26 |
 */
data class ShowerRecord(
    /** The 28 raw bytes as hex, for persisting. */
    val hex: String,
    val filter: Int,
    val clock: Long,
    val logNumber: Int,
    /** Per pressure band, low to high. */
    val bandDecilitres: List<Int>,
    val bandSeconds: List<Int>,
    val hotSeconds: Int,
    val coldSeconds: Int,
) {
    val durationSeconds: Int get() = bandSeconds.sum()
    val litres: Double get() = bandDecilitres.sum() / 10.0

    /** The record a filter reset opens, before any real shower lands in it. */
    val isFilterMarker: Boolean get() = logNumber == 1 && durationSeconds < MIN_SHOWER_SECONDS
    val isShower: Boolean get() = durationSeconds >= MIN_SHOWER_SECONDS

    /** Unique across the head's life: log numbers restart with each filter. */
    val key: Long get() = (filter.toLong() shl 32) or logNumber.toLong()

    companion object {
        /** The HelloKlean app only keeps sessions of at least a minute; its averages match only with this cut. */
        const val MIN_SHOWER_SECONDS = 60

        fun parse(r: ByteArray): ShowerRecord? {
            if (r.size != ShowerHeadReading.RECORD_BYTES || r.all { it == 0.toByte() }) return null
            val sum = (0 until 27).sumOf { r[it].toInt() and 0xFF } and 0xFF
            if (sum != (r[27].toInt() and 0xFF)) return null
            return ShowerRecord(
                hex = r.toHex(),
                filter = r[0].toInt() and 0xFF,
                clock = r.le32(1),
                logNumber = r.le16(5),
                bandDecilitres = List(4) { r.le16(7 + it * 4) },
                bandSeconds = List(4) { r.le16(9 + it * 4) },
                hotSeconds = r.le16(23),
                coldSeconds = r.le16(25),
            )
        }
    }
}

private fun ByteArray.le16(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.le32(at: Int): Long = (0..3).fold(0L) { acc, k -> acc or ((this[at + k].toLong() and 0xFF) shl (8 * k)) }
