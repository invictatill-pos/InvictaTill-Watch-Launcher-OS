package com.healthsync.watch.update

import com.healthsync.watch.data.*
import org.junit.Assert.*
import org.junit.Test

class OtaTransferPolicyTest {
    private val start = OtaStartPayload("2.7.0", 26, 5000, 2, "a".repeat(64))
    private val chunk = OtaChunkPayload(0, 2, "", 0, 4096)

    @Test fun startRequiresBoundedSizeAndVerification() {
        assertTrue(OtaTransferPolicy.validStart(start))
        assertFalse(OtaTransferPolicy.validStart(start.copy(sha256 = "")))
        assertFalse(OtaTransferPolicy.validStart(start.copy(fileSize = Long.MAX_VALUE)))
        assertFalse(OtaTransferPolicy.validStart(start.copy(totalChunks = 0)))
    }

    @Test fun exactOffsetsAndFinalChunkLengthAreRequired() {
        assertTrue(OtaTransferPolicy.validChunk(start, chunk, 4096))
        assertTrue(OtaTransferPolicy.validChunk(start, chunk.copy(chunkIndex = 1, offset = 4096), 904))
        assertFalse(OtaTransferPolicy.validChunk(start, chunk.copy(chunkIndex = 1, offset = 4000), 904))
        assertFalse(OtaTransferPolicy.validChunk(start, chunk.copy(chunkIndex = 1, offset = 4096), 905))
        assertFalse(OtaTransferPolicy.validChunk(start, chunk.copy(chunkIndex = 2), 0))
        assertFalse(OtaTransferPolicy.validChunk(start, chunk.copy(totalChunks = 3), 4096))
    }

    @Test fun correlatedTransferCannotAcceptOldMissingOrChangedChunkIdentity() {
        val identified = start.copy(transferId = "new-transfer")
        assertFalse(OtaTransferPolicy.validChunk(identified, chunk, 4096))
        assertFalse(OtaTransferPolicy.validChunk(identified, chunk.copy(transferId = "old-transfer"), 4096))
        assertTrue(OtaTransferPolicy.validChunk(identified, chunk.copy(transferId = "new-transfer"), 4096))
        assertFalse(OtaTransferPolicy.matchesTransfer(identified, null))
        assertFalse(OtaTransferPolicy.matchesTransfer(identified, "old-transfer"))
        assertTrue(OtaTransferPolicy.matchesTransfer(identified, "new-transfer"))
    }

    @Test fun transferChunkGeometryCannotChangeAfterFirstFrame() {
        assertTrue(OtaTransferPolicy.validChunk(start, chunk, 4096, 4096))
        assertFalse(OtaTransferPolicy.validChunk(start, chunk.copy(chunkSize = 4000), 4000, 4096))
    }
}
