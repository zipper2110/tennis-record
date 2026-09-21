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
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : CompletedRendersRepository {
    override fun loadAll(): List<CompletedRender> = CompletedRendersStore.loadAll(file)

    override fun append(job: RenderJob) = CompletedRendersStore.append(
        file,
        CompletedRender(
            id = job.id,
            projectId = job.projectId,
            projectName = job.projectName,
            outputPath = job.outputPath,
            fileName = File(job.outputPath).name,
            encoderLabel = job.encoderLabel,
            outWidth = job.outWidth,
            outHeight = job.outHeight,
            outputFrameRate = job.outputFrameRate,
            bytesWritten = job.bytesWritten,
            idleTrim = job.idleTrim,
            favoriteOnly = job.favoriteOnly,
            includeScoreboard = job.includeScoreboard,
            includeComments = job.includeComments,
            createdAtEpochMs = nowEpochMs(),
        ),
    )

    override fun clear() = CompletedRendersStore.clear(file)
}

object ProductionCompletedRendersRepository : CompletedRendersRepository {
    private fun delegate() = FileCompletedRendersRepository(AppDataPaths.production().completedRenders)

    override fun loadAll(): List<CompletedRender> = delegate().loadAll()
    override fun append(job: RenderJob) = delegate().append(job)
    override fun clear() = delegate().clear()
}
