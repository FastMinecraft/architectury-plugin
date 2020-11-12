package me.shedaniel.architect.plugin

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

class ArchitectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(
            mapOf(
                "plugin" to "java",
                "plugin" to "eclipse",
                "plugin" to "idea"
            )
        )
        project.extensions.create("architect", ArchitectPluginExtension::class.java, project)

        project.afterEvaluate {
            project.extensions.getByType(JavaPluginExtension::class.java).apply {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        project.tasks.register("remapMcp", RemapMCPTask::class.java) {
            it.group = "Architect"
        }
        
        project.tasks.register("remapMcpFakeMod", RemapMCPTask::class.java) {
            it.fakeMod = true
            it.group = "Architect"
        }

        project.tasks.register("transformArchitectJar", TransformTask::class.java) {
            it.group = "Architect"
        }
    }
}