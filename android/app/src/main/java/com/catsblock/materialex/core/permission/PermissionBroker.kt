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
    val temporary: TemporaryGrants = TemporaryGrants()
) {
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
                Decision.REJECT -> {
                    temporary.revoke(profileId, origin, type)
                    false
                }
                Decision.PROMPT -> {
                    if (scopeId.isNotEmpty() && temporary.isActive(profileId, origin, type, scopeId)) {
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
                        manager.choose(profileId, origin, type, choice)
                        if (choice == PromptChoice.ALLOW_ONCE && scopeId.isNotEmpty()) {
                            temporary.grant(profileId, origin, type, scopeId)
                        }
                        choice == PromptChoice.ALLOW || (choice == PromptChoice.ALLOW_ONCE && scopeId.isNotEmpty())
                    }
                }
            }
        }
    }

    fun answer(choice: PromptChoice) {
        pending?.complete(choice)
    }
}
