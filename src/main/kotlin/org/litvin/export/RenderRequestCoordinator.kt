package org.litvin.export

internal class RenderRequestCoordinator {
    private data class OwnerState(
        val requestIds: MutableSet<String> = linkedSetOf(),
        var closed: Boolean = false,
    )

    private data class CurrentRequest(
        val ownerId: String,
        val requestId: String,
        var canceled: Boolean,
        var process: Process? = null,
    )

    private val monitor = Any()
    private val owners = mutableMapOf<String, OwnerState>()
    private var current: CurrentRequest? = null

    fun register(ownerId: String, requestId: String) = synchronized(monitor) {
        val owner = owners.getOrPut(ownerId) { OwnerState() }
        check(!owner.closed) { "Render owner is closed: $ownerId" }
        check(owner.requestIds.add(requestId)) { "Render request is already registered: $requestId" }
    }

    fun markCurrent(ownerId: String, requestId: String): Boolean = synchronized(monitor) {
        val owner = owners[ownerId]
        check(owner?.requestIds?.contains(requestId) == true) { "Render request is not registered: $requestId" }
        current = CurrentRequest(ownerId, requestId, canceled = owner.closed)
        !owner.closed
    }

    fun startProcess(ownerId: String, requestId: String, starter: () -> Process): Process? = synchronized(monitor) {
        val active = current?.takeIf { it.ownerId == ownerId && it.requestId == requestId }
            ?: error("Render request is not current: $requestId")
        if (active.canceled || owners[ownerId]?.closed != false) return null
        starter().also { active.process = it }
    }

    fun clearProcess(ownerId: String, requestId: String, process: Process) = synchronized(monitor) {
        current
            ?.takeIf { it.ownerId == ownerId && it.requestId == requestId && it.process === process }
            ?.process = null
    }

    fun isCanceled(ownerId: String, requestId: String): Boolean = synchronized(monitor) {
        owners[ownerId]?.closed == true || current
            ?.takeIf { it.ownerId == ownerId && it.requestId == requestId }
            ?.canceled == true
    }

    fun cancelCurrent(ownerId: String) {
        val process = synchronized(monitor) {
            current?.takeIf { it.ownerId == ownerId }?.also { it.canceled = true }?.process
        }
        destroy(process)
    }

    fun closeOwner(ownerId: String) {
        val process = synchronized(monitor) {
            val owner = owners[ownerId] ?: return
            owner.closed = true
            current?.takeIf { it.ownerId == ownerId }?.also { it.canceled = true }?.process
        }
        destroy(process)
    }

    fun finish(ownerId: String, requestId: String) = synchronized(monitor) {
        current?.takeIf { it.ownerId == ownerId && it.requestId == requestId }?.let { current = null }
        val owner = owners[ownerId] ?: return
        owner.requestIds.remove(requestId)
        if (owner.requestIds.isEmpty()) owners.remove(ownerId)
    }

    private fun destroy(process: Process?) {
        if (process == null) return
        try {
            process.destroy()
        } catch (_: Throwable) {
        }
        try {
            process.destroyForcibly()
        } catch (_: Throwable) {
        }
    }
}
