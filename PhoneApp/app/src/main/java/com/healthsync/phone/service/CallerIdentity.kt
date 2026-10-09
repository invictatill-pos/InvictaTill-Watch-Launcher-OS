package com.healthsync.phone.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

internal object CallerIdentity {
    fun name(context: Context, number: String, suggested: String = ""): String {
        if (number.isNotBlank() && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0)?.trim()?.takeIf(String::isNotBlank)?.let { name -> return name.take(120) }
                }
            } catch (_: Exception) { /* Contacts access can be revoked while a call rings. */ }
        }
        return suggested.trim().take(120).ifBlank { number.ifBlank { "Unknown caller" } }
    }
}
