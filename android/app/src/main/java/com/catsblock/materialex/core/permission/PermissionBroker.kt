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
    private val clock: () -> Long = System::currentTimeMillis
) {
    val temporary = TemporaryGrants()
    private val queue = Mutex()
    private val current = MutableStateFlow<PermissionPrompt?>(null)
    private var pending: CompletableDeferred<PromptChoice>? = null
    private var counter = 0L

    val prompt: StateFlow<PermissionPrompt?> = current.asStateFlow()

    suspend fun resolve(requestOrigin: String?, type: PermissionType, scopeId: String = ""): Boolean {
        val origin = Origin.normalize(requestOrigin) ?: return false
        if (type.requiresSecureOrigin && !Origin.isSecure(origin)) return false
        return queue.withLock {
            when (manager.decide(profileId, origin, type)) {
                Decision.GRANT -> true
                Decision.REJECT -> false
                Decision.PROMPT -> if (temporary.isGranted(profileId, scopeId, origin, type, clock())) {
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
                    manager.choose(profileId, origin, type, recorded)
                    if (choice == PromptChoice.ALLOW_ONCE && type.supportsAllowOnce) {
                        temporary.grant(profileId, scopeId, origin, type, clock())
                        true
                    } else {
                        choice == PromptChoice.ALLOW
                    }
                }
            }
        }
    }

    fun isTemporarilyAllowed(origin: String, type: PermissionType, scopeId: String): Boolean =
        temporary.isGranted(profileId, scopeId, origin, type, clock())

    fun revokeTemporary(origin: String, type: PermissionType, scopeId: String) =
        temporary.revoke(profileId, scopeId, origin, type)

    fun revokeTemporaryForOrigin(origin: String) = temporary.revokeOrigin(profileId, origin)

    fun tabClosed(scopeId: String) = temporary.revokeScope(scopeId)

    fun navigated(scopeId: String, url: String?) = temporary.revokeScopeOutside(scopeId, Origin.normalize(url))

    fun answer(choice: PromptChoice) {
        pending?.complete(choice)
    }
}
