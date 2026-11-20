package com.catsblock.materialex.core

enum class GoogleFailure { NOT_REGISTERED, NETWORK, OTHER }

object GoogleErrors {
    private const val INTERNAL_ERROR = 8
    private const val DEVELOPER_ERROR = 10
    private const val NETWORK_ERROR = 7

    fun classify(code: String): GoogleFailure = when (code.toIntOrNull()) {
        INTERNAL_ERROR, DEVELOPER_ERROR -> GoogleFailure.NOT_REGISTERED
        NETWORK_ERROR -> GoogleFailure.NETWORK
        else -> GoogleFailure.OTHER
    }

    fun fingerprint(digest: ByteArray): String = digest.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
}
