package com.healthsync.watch.update

import com.healthsync.watch.data.OtaChunkPayload
import com.healthsync.watch.data.OtaStartPayload

internal object OtaTransferPolicy {
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    fun validStart(start: OtaStartPayload): Boolean = start.fileSize in 1..MAX_APK_BYTES &&
        start.versionCode > 0 && start.versionName.orEmpty().length <= 80 &&
        Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?").matches(start.versionName.orEmpty()) &&
        start.transferId.orEmpty().length <= 80 &&
        Regex("[a-fA-F0-9]{64}").matches(start.sha256.orEmpty()) &&
        start.totalChunks in ((start.fileSize + 8191) / 8192).toInt()..25600

    fun matchesTransfer(start: OtaStartPayload, transferId: String?): Boolean =
        start.transferId.orEmpty() == transferId.orEmpty()

    fun validChunk(start: OtaStartPayload, chunk: OtaChunkPayload, size: Int, expectedChunkSize: Int? = null): Boolean {
        if (!matchesTransfer(start, chunk.transferId) || (expectedChunkSize != null && chunk.chunkSize != expectedChunkSize)) return false
        if (chunk.chunkSize !in 1..8192 || chunk.totalChunks != start.totalChunks || chunk.chunkIndex !in 0 until start.totalChunks) return false
        if ((start.fileSize + chunk.chunkSize - 1) / chunk.chunkSize != start.totalChunks.toLong()) return false
        val expectedOffset = chunk.chunkIndex.toLong() * chunk.chunkSize
        val suppliedOffset = if (chunk.offset == 0L) expectedOffset else chunk.offset
        return suppliedOffset == expectedOffset && size == minOf(chunk.chunkSize.toLong(), start.fileSize - expectedOffset).toInt()
    }
}
