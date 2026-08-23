package org.litvin.export

import org.litvin.CompletedRender
import org.litvin.CompletedRendersStore
import org.litvin.RenderJob
import org.litvin.app.AppDataPaths
import java.io.File

interface CompletedRendersRepository {
    fun loadAll(): List<CompletedRender>
    fun append(job: RenderJob)
    fun clear()
}

class FileCompletedRendersRepository(
    private val file: File,
) : CompletedRendersRepository {
    override fun loadAll(): List<CompletedRender> = CompletedRendersStore.loadAll(file)

    override fun append(job: RenderJob) = CompletedRendersStore.append(job, file)

    override fun clear() = CompletedRendersStore.clear(file)
}

object ProductionCompletedRendersRepository : CompletedRendersRepository {
    private fun delegate() = FileCompletedRendersRepository(AppDataPaths.production().completedRenders)

    override fun loadAll(): List<CompletedRender> = delegate().loadAll()
    override fun append(job: RenderJob) = delegate().append(job)
    override fun clear() = delegate().clear()
}
