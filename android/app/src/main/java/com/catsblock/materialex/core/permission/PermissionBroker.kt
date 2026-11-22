package com.catsblock.materialex.core.permission

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PermissionPrompt(val id: Long, val origin: String, val type: PermissionType)

class PermissionBroker(
    private val manager: PermissionManager,
    private val profileId: String,
    private val profileOf: (String) -> String = { profileId },
    private val clock: () -> Long = System::currentTimeMillis
) {
    val temporary = TemporaryGrants()
    private val queue = Mutex()
    private val current = MutableStateFlow<PermissionPrompt?>(null)
    private var pending: CompletableDeferred<PromptChoice>? = null
    private var counter = 0L

    val prompt: StateFlow<PermissionPrompt?> = current.asStateFlow()

    var onTemporaryRevoked: ((Revocation) -> Unit)? = null

    suspend fun resolve(requestOrigin: String?, type: PermissionType, scopeId: String = ""): Boolean {
        val origin = Origin.normalize(requestOrigin) ?: return false
        if (type.requiresSecureOrigin && !Origin.isSecure(origin)) return false
        val profile = profileOf(scopeId)
        return queue.withLock {
            when (manager.decide(profile, origin, type)) {
                Decision.GRANT -> true
                Decision.REJECT -> false
                Decision.PROMPT -> if (temporary.isGranted(profile, scopeId, origin, type, clock())) {
                    true
                } else {
                    val answer = CompletableDeferred<PromptChoice>()
                    pending = answer
                    current.value = PermissionPrompt(++counter, origin, type)
                    val choice = try {
                        answer.await()
                    } finally {
                        current.value = null
                        pending = null
                    }
                    val recorded = if (choice == PromptChoice.ALLOW_ONCE && !type.supportsAllowOnce) PromptChoice.ALWAYS_ASK else choice
                    manager.choose(profile, origin, type, recorded)
                    if (choice == PromptChoice.ALLOW_ONCE && type.supportsAllowOnce) {
                        temporary.grant(profile, scopeId, origin, type, clock())
                        true
                    } else {
                        choice == PromptChoice.ALLOW
                    }
                }
            }
        }
    }

    fun isTemporarilyAllowed(origin: String, type: PermissionType, scopeId: String): Boolean =
        temporary.isGranted(profileOf(scopeId), scopeId, origin, type, clock())

    fun revokeTemporary(origin: String, type: PermissionType, scopeId: String) {
        val profile = profileOf(scopeId)
        temporary.revoke(profile, scopeId, origin, type)
        onTemporaryRevoked?.invoke(Revocation(profile, origin, type))
    }

    fun revokeTemporaryForOrigin(origin: String, scopeId: String) {
        val profile = profileOf(scopeId)
        temporary.revokeOrigin(profile, origin)
        onTemporaryRevoked?.invoke(Revocation(profile, origin, null))
    }

    fun tabClosed(scopeId: String) = temporary.revokeScope(scopeId)

    fun navigated(scopeId: String, url: String?) = temporary.revokeScopeOutside(scopeId, Origin.normalize(url))

    fun answer(choice: PromptChoice) {
        pending?.complete(choice)
    }
}
