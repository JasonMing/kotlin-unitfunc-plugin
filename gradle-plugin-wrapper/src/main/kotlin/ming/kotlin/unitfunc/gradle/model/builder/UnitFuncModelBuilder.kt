package ming.kotlin.unitfunc.gradle.model.builder

import ming.kotlin.unitfunc.gradle.UnitFuncExtension
import ming.kotlin.unitfunc.gradle.model.UnitFunc
import ming.kotlin.unitfunc.gradle.model.impl.UnitFuncImpl
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.tooling.provider.model.ToolingModelBuilder

/**
 * [ToolingModelBuilder] for [UnitFunc] models.
 * This model builder is registered for Kotlin Unit Func sub-plugin.
 *
 * @author MiNG
 * @since 1.0.0
 */
class UnitFuncModelBuilder : ToolingModelBuilder {

    companion object {
        private val SUPPORT_MODEL_NAME = UnitFunc::class.java.name
    }

    override fun canBuild(modelName: String) =
        modelName == SUPPORT_MODEL_NAME

    override fun buildAll(modelName: String, project: Project) =
        when (modelName) {
            SUPPORT_MODEL_NAME ->
                with(project.extensions.getByType<UnitFuncExtension>()) {
                    UnitFuncImpl(project.name, enabled)
                }
            else -> error("incorrect model \"$modelName\", expected: \"$SUPPORT_MODEL_NAME\"")
        }
}