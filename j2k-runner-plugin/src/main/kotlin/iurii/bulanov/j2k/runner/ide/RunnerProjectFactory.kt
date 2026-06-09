@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner.ide

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates and disposes the short-lived IntelliJ project and Java module used for one conversion run.
 */
class RunnerProjectFactory {
    /**
     * Creates a disposable project rooted at the staged benchmark source tree.
     *
     * When [index] is true, uses the IDE open path (`openProject`) so the project is fully
     * registered and the open sequence schedules indexing — required for the indexed (smart-mode)
     * converters. When false, uses bare `newProject`, which schedules no indexing; the dumb-mode
     * converter then runs with no background indexing to race against.
     */
    fun createProject(
        projectName: String,
        stagingDirectory: Path,
        index: Boolean,
    ): Project {
        val openProjectTask =
            OpenProjectTask
                .build()
                .asNewProject()
                .withProjectName(projectName)
        val projectManager = ProjectManagerEx.getInstanceEx()
        return if (index) {
            projectManager.openProject(stagingDirectory, openProjectTask)
                ?: error("failed to open IntelliJ project for $stagingDirectory")
        } else {
            projectManager.newProject(stagingDirectory, openProjectTask)
                ?: error("failed to create IntelliJ project for $stagingDirectory")
        }
    }

    /**
     * Creates a disposable Java module with configured source roots.
     */
    fun createModule(
        project: Project,
        stagingDirectory: Path,
        sourceRoots: List<String>,
    ): Module =
        WriteAction.computeAndWait<Module, RuntimeException> {
            val sdk = createRunnerJdk()
            val modulePath = stagingDirectory.resolve("${project.name}.iml")
            val module =
                ModuleManager.getInstance(project).newModule(
                    modulePath.toString(),
                    StdModuleTypes.JAVA.id,
                )
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.sdk = sdk
                attachKotlinStdlib(model)
                val contentEntry = model.addContentEntry(VfsUtil.pathToUrl(stagingDirectory.toString()))
                sourceRoots.forEach { sourceRoot ->
                    contentEntry.addSourceFolder(VfsUtil.pathToUrl(stagingDirectory.resolve(sourceRoot).toString()), false)
                }
            }
            module
        }

    /**
     * Closes the temporary IDE project with the write-intent context required by the platform.
     */
    fun closeProject(project: Project) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            ProjectManagerEx.getInstanceEx().forceCloseProject(project, true)
        } else {
            application.invokeAndWait {
                ProjectManagerEx.getInstanceEx().forceCloseProject(project, true)
            }
        }
    }

    /**
     * Adds the IDE-bundled Kotlin standard library to the temporary module.
     */
    private fun attachKotlinStdlib(model: ModifiableRootModel) {
        val stdlibJars = kotlinStdlibJars()
        require(stdlibJars.isNotEmpty()) {
            "failed to locate bundled Kotlin stdlib jars under ${PathManager.getHomePath()}"
        }
        val library = model.moduleLibraryTable.createLibrary("kotlin-stdlib")
        val libraryModel = library.modifiableModel
        stdlibJars.forEach { jar ->
            libraryModel.addRoot(VfsUtil.getUrlForLibraryRoot(jar), OrderRootType.CLASSES)
        }
        libraryModel.commit()
    }

    /**
     * Resolves Kotlin stdlib jars from the bundled Kotlin plugin.
     */
    private fun kotlinStdlibJars(): List<Path> {
        val kotlinLibDirectory = Path.of(PathManager.getHomePath(), "plugins", "Kotlin", "kotlinc", "lib")
        return listOf(
            "kotlin-stdlib.jar",
            "kotlin-stdlib-jdk7.jar",
            "kotlin-stdlib-jdk8.jar",
        ).map { kotlinLibDirectory.resolve(it) }.filter(Files::exists)
    }

    /**
     * Creates or reuses a JDK SDK backed by the IDE runner JVM.
     */
    private fun createRunnerJdk(): Sdk {
        val jdkName = "j2k-runner-jdk"
        val projectJdkTable = ProjectJdkTable.getInstance()
        return projectJdkTable.findJdk(jdkName)
            ?: JavaSdk.getInstance().createJdk(jdkName, System.getProperty("java.home"), false).also { sdk ->
                projectJdkTable.addJdk(sdk)
            }
    }
}
