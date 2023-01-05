package dev.architectury.plugin

import dev.architectury.plugin.utils.GradleSupport
import dev.architectury.transformer.Transform
import dev.architectury.transformer.Transformer
import dev.architectury.transformer.shadowed.impl.com.google.gson.Gson
import dev.architectury.transformer.transformers.BuiltinProperties
import dev.architectury.transformer.util.Logger
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Path
import java.util.*
import javax.inject.Inject
import kotlin.properties.Delegates

abstract class TransformingTask : Jar() {
    @InputFile
    val input: RegularFileProperty = GradleSupport.getFileProperty(project)

    @Internal
    val transformers: ListProperty<Transformer> = project.objects.listProperty(Transformer::class.java)

    @Internal
    var platform: String? = null

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun doTask() {
        workerExecutor.noIsolation().submit(TransformAction::class.java) {
            val extension = project.extensions.getByType(ArchitectPluginExtension::class.java)
            val properties = mutableMapOf<String, String>()
            extension.properties(platform ?: throw NullPointerException("No Platform specified")).toMap(properties)
            properties[BuiltinProperties.LOCATION] = project.file(".gradle").absolutePath

            it.input.set(this.input)
            it.output.set(archiveFile)
            it.transformers.set(transformers)
            it.properties.set(properties)
        }
    }

    operator fun invoke(transformer: Transformer) {
        transformers.add(transformer)
    }

    operator fun plusAssign(transformer: Transformer) {
        transformers.add(transformer)
    }

    fun add(transformer: Transformer, config: MutableMap<String, Any>.(file: Path) -> Unit) {
        transformers.add(project.provider {
            val properties = mutableMapOf<String, Any>()
            config(properties, input.asFile.get().toPath())
            transformer.supplyProperties(Gson().toJsonTree(properties).asJsonObject)
            transformer
        })
    }

    abstract class TransformParams : WorkParameters {
        abstract val input: RegularFileProperty
        abstract val output: RegularFileProperty
        abstract val transformers: ListProperty<Transformer>
        abstract val properties: MapProperty<String, String>
    }

    abstract class TransformAction : WorkAction<TransformParams> {
        override fun execute() {
            val input = parameters.input.asFile.get().toPath()
            val output = parameters.output.asFile.get().toPath()
            val transformers = parameters.transformers.get()
            val properties = parameters.properties.get()

            val logger = Logger(
                properties.getOrDefault(BuiltinProperties.LOCATION, System.getProperty("user.dir")),
                properties.getOrDefault(BuiltinProperties.VERBOSE, "false") == "true"
            )
            logger.debug("")
            logger.debug("============================")
            logger.debug("Transforming from $input to $output")
            logger.debug("============================")
            logger.debug("")
            Transform.runTransformers(input, output, transformers, properties)
        }
    }
}

fun Project.projectUniqueIdentifier(): String {
    val cache = File(project.file(".gradle"), "architectury-cache")
    cache.mkdirs()
    val uniqueIdFile = File(cache, "projectID")
    var id by Delegates.notNull<String>()
    if (uniqueIdFile.exists()) {
        id = uniqueIdFile.readText()
    } else {
        id = UUID.randomUUID().toString().filterNot { it == '-' }
        uniqueIdFile.writeText(id)
    }
    var name = project.name
    if (project.rootProject != project) name = project.rootProject.name + "_" + name
    return "architectury_inject_${name}_$id".filter { Character.isJavaIdentifierPart(it) }
}

class Epic : RuntimeException()
