package org.litvin.ui.flow.fixtures

import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.markup.EdlIO
import org.litvin.markup.EdlV1
import org.litvin.markup.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolute

data class UiFlowProject(
    val directory: Path,
    val manifestFile: Path,
    val manifest: ProjectManifestV1,
)

class UiFlowFixtureBuilder(
    private val projectsRoot: Path,
    val sourceVideo: Path = immutableSmokeVideo(),
) {
    private val sequence = AtomicInteger()

    fun onlyProject(): UiFlowProject {
        val manifests = Files.walk(projectsRoot).use { paths ->
            paths.filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".trproj") }
                .toList()
        }
        require(manifests.size == 1) { "Expected exactly one project manifest below $projectsRoot, found $manifests" }
        val manifestFile = manifests.single()
        return UiFlowProject(
            directory = requireNotNull(manifestFile.parent),
            manifestFile = manifestFile,
            manifest = ManifestIO.read(manifestFile.toString()),
        )
    }

    fun emptyProject(name: String = "Empty Match"): UiFlowProject = create(
        name = name,
        source = sourceVideo,
        edl = EdlV1(),
        adjustments = AdjustmentsV1(),
        score = ScoreV1(),
    )

    fun editedProject(name: String = "Edited Match"): UiFlowProject {
        val point = PointV1(id = "point-1", startMs = 1_000, endMs = 2_000, favorite = true)
        return create(
            name = name,
            source = sourceVideo,
            edl = EdlV1(listOf(point)),
            adjustments = AdjustmentsV1(brightness = 1.2f, zoom = 1.25f, rotationDeg = 15f),
            score = ScoreV1(outcomes = mapOf(point.id to Outcome.P1)),
        )
    }

    fun missingSourceProject(name: String = "Missing Source Match"): UiFlowProject = create(
        name = name,
        source = projectsRoot.resolve("missing-source-${sequence.incrementAndGet()}.mp4").absolute(),
        edl = EdlV1(),
        adjustments = AdjustmentsV1(),
        score = ScoreV1(),
    )

    fun exportReadyProject(name: String = "Export Ready Match"): UiFlowProject {
        val points = listOf(
            PointV1(id = "point-1", startMs = 500, endMs = 2_500, favorite = true),
            PointV1(id = "point-2", startMs = 4_000, endMs = 6_000),
        )
        return create(
            name = name,
            source = sourceVideo,
            edl = EdlV1(points),
            adjustments = AdjustmentsV1(),
            score = ScoreV1(outcomes = mapOf("point-1" to Outcome.P1, "point-2" to Outcome.P2)),
        )
    }

    private fun create(
        name: String,
        source: Path,
        edl: EdlV1,
        adjustments: AdjustmentsV1,
        score: ScoreV1,
    ): UiFlowProject {
        val ordinal = sequence.incrementAndGet()
        val safeName = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "project" }
        val directory = projectsRoot.resolve("$safeName-$ordinal")
        Files.createDirectories(directory)
        val id = UUID.nameUUIDFromBytes("$name-$ordinal".toByteArray(StandardCharsets.UTF_8)).toString()
        val manifest = ProjectManifestV1(
            id = id,
            name = name,
            createdAt = FIXED_TIME,
            lastOpenedAt = FIXED_TIME,
            sourceVideo = source.toFile().absolutePath,
        )
        val manifestFile = directory.resolve("project.trproj")
        ManifestIO.write(manifestFile.toString(), manifest)
        EdlIO.writeForProjectDir(directory.toString(), edl)
        AdjustmentsIO.writeForProjectDir(directory.toString(), adjustments)
        ScoreIO.writeForProjectDir(directory.toString(), score)
        return UiFlowProject(directory, manifestFile, manifest)
    }

    companion object {
        private const val FIXED_TIME = "2026-08-23T00:00:00Z"

        fun immutableSmokeVideo(): Path {
            val resource = requireNotNull(UiFlowFixtureBuilder::class.java.getResource("/media/ui-smoke.mp4")) {
                "Missing immutable media fixture /media/ui-smoke.mp4"
            }
            require(resource.protocol == "file") { "UI flow media fixture must be a regular test resource: $resource" }
            return Path.of(resource.toURI()).toAbsolutePath().normalize()
        }
    }
}
