package com.catsblock.materialex.core

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

object SigningFingerprint {
    fun sha1(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return null
        val certificate = (if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory)
            ?.firstOrNull() ?: return null
        GoogleErrors.fingerprint(MessageDigest.getInstance("SHA-1").digest(certificate.toByteArray()))
    }.getOrNull()
}
