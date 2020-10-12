package ming.kotlin.unitfunc.gradle

import com.google.auto.service.AutoService
import ming.kotlin.unitfunc.gradle.model.builder.UnitFuncModelBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.hasPlugin
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject

/**
 *
 * @author MiNG
 * @since 1.0.0
 */
class UnitFuncGradlePlugin @Inject constructor(
    private val registry: ToolingModelBuilderRegistry
) : Plugin<Project> {

    companion object {

        fun getUnitFuncExtension(project: Project) = project.extensions.getByType<UnitFuncExtension>()
    }

    override fun apply(target: Project) {
        with(target) {
            extensions.create<UnitFuncExtension>("unitfunc")
            registry.register(UnitFuncModelBuilder())
        }
    }
}

/**
 *
 * @author MiNG
 * @since 1.0.0
 */
@AutoService(KotlinGradleSubplugin::class)
class UnitFuncGradleSubplugin : KotlinGradleSubplugin<AbstractCompile> {

    companion object {
        const val UNITFUNC_ARTIFACT_NAME = "kotlin-unitfunc" // points to myself, the cli module will be assembled into this package later
    }

    override fun isApplicable(project: Project, task: AbstractCompile) =
        with(project) {
            plugins.hasPlugin(UnitFuncGradlePlugin::class)
                    && extensions.getByType<UnitFuncExtension>().enabled
        }

    @OptIn(ExperimentalStdlibApi::class)
    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?
    ): List<SubpluginOption> = emptyList()

    override fun getCompilerPluginId() =
        "ming.kotlin.unitfunc"

    override fun getPluginArtifact() =
        SubpluginArtifact("ming.kotlin", "$UNITFUNC_ARTIFACT_NAME-compiler-plugin", "1.0.0-SNAPSHOT")
}