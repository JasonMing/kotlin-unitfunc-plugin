package ming.kotlin.unitfunc

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm


/**
 *
 * @author MiNG
 * @since 1.0.0
 */
@AutoService(CommandLineProcessor::class)
class UnitFuncCommandLineProcessor : CommandLineProcessor {

    companion object {
        val PLUGIN_ID = "ming.kotlin.unitfunc"
    }

    override val pluginId = PLUGIN_ID

    override val pluginOptions = emptyList<CliOption>()
}

@AutoService(ComponentRegistrar::class)
class UnitFuncComponentRegistrar : ComponentRegistrar {

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {

        // StorageComponentContainerContributor.registerExtension(project, CliUnitFuncComponentContainerContributor(/*annotations*/))

        val messageCollector = configuration.getNotNull(MESSAGE_COLLECTOR_KEY)

        val extension = UnitFuncCodegenExtension(messageCollector)
        ClassBuilderInterceptorExtension.registerExtension(project, extension)
        ExpressionCodegenExtension.registerExtension(project, extension)
    }
}

// class CliUnitFuncComponentContainerContributor(
// ) : StorageComponentContainerContributor {
//     override fun registerModuleComponents(container: StorageComponentContainer, platform: TargetPlatform, moduleDescriptor: ModuleDescriptor) {
//
//         if (!platform.isJvm()) {
//             return
//         }
//
//         // TODO: don't known usage
//         // container.useInstance(CliNoArgDeclarationChecker(annotations))
//     }
// }