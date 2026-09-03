package org.litvin.ui.flow.screens

import org.junit.jupiter.api.Test
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.assertTrue

class ScreenObjectContractTest {
    @Test
    fun screenObjectsKeepSwingImplementationDetailsBehindTheDriverBoundary() {
        val screenTypes = listOf(
            ApplicationScreen::class.java,
            ProjectsScreen::class.java,
            RalliesScreen::class.java,
            ColorsScreen::class.java,
            CropScreen::class.java,
            ScoringScreen::class.java,
            ExportScreen::class.java,
        )
        val leakedReturnTypes = screenTypes
            .flatMap { type -> type.declaredMethods.map { method -> "${type.simpleName}.${method.name}" to method.returnType } }
            .filter { (_, returnType) ->
                Component::class.java.isAssignableFrom(returnType) ||
                    returnType.name.startsWith("org.assertj.swing.fixture.")
            }
            .map { (method, returnType) -> "$method returns ${returnType.name}" }

        val forbiddenImports = sourceFilesBelow("screens", "scenarios")
            .flatMap { source ->
                Files.readAllLines(source)
                    .filter { line -> line.trim().startsWith("import org.assertj.swing") }
                    .map { line -> "${source.toString().replace('\\', '/')}: ${line.trim()}" }
            }

        assertTrue(
            leakedReturnTypes.isEmpty() && forbiddenImports.isEmpty(),
            buildString {
                if (leakedReturnTypes.isNotEmpty()) appendLine("Leaked UI implementation types: $leakedReturnTypes")
                if (forbiddenImports.isNotEmpty()) appendLine("AssertJ Swing imports outside driver/spike packages: $forbiddenImports")
            },
        )
    }

    private fun sourceFilesBelow(vararg packageNames: String): List<Path> = packageNames.flatMap { packageName ->
        val root = Path.of("src/test/kotlin/org/litvin/ui/flow", packageName)
        if (!Files.isDirectory(root)) {
            emptyList()
        } else {
            Files.walk(root).use { paths ->
                paths.filter { path -> Files.isRegularFile(path) && path.extension == "kt" }.toList()
            }
        }
    }
}
