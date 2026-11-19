package com.catsblock.materialex.core.permission

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PermissionPrompt(val id: Long, val origin: String, val type: PermissionType)

class PermissionBroker(private val manager: PermissionManager, private val profileId: String) {
    private val queue = Mutex()
    private val current = MutableStateFlow<PermissionPrompt?>(null)
    private var pending: CompletableDeferred<PromptChoice>? = null
    private var counter = 0L

    val prompt: StateFlow<PermissionPrompt?> = current.asStateFlow()

    suspend fun resolve(requestOrigin: String?, type: PermissionType): Boolean {
        val origin = Origin.normalize(requestOrigin) ?: return false
        if (type.requiresSecureOrigin && !Origin.isSecure(origin)) return false
        return queue.withLock {
            when (manager.decide(profileId, origin, type)) {
                Decision.GRANT -> true
                Decision.REJECT -> false
                Decision.PROMPT -> {
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
                    choice == PromptChoice.ALLOW
                }
            }
        }
    }

    fun answer(choice: PromptChoice) {
        pending?.complete(choice)
    }
}
