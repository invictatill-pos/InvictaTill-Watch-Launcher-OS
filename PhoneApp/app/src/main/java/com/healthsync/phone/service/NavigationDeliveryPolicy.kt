package com.healthsync.phone.service

import com.healthsync.phone.data.model.NavigationProtocol
import com.healthsync.phone.data.model.SyncMessage

/** A queued route snapshot is disposable as soon as a newer source state exists. */
internal fun navigationSnapshotCanSend(candidate: SyncMessage, latest: SyncMessage?, queuedAt: Long, now: Long): Boolean =
    candidate === latest && now >= queuedAt && now - queuedAt <= NavigationProtocol.HEARTBEAT_MS
