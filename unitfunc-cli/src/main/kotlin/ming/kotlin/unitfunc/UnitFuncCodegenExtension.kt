package ming.kotlin.unitfunc

import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.INFO
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.WARNING
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.FunctionGenerationStrategy.CodegenBased
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.SamType
import org.jetbrains.kotlin.codegen.asmType
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.codegen.generateMethod
import org.jetbrains.kotlin.codegen.isJvmStaticInObjectOrClassOrInterface
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality.FINAL
import org.jetbrains.kotlin.descriptors.SourceElement.NO_SOURCE
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations.Companion.EMPTY
import org.jetbrains.kotlin.descriptors.impl.FunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.kotlin.computeJvmDescriptor
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.AsmTypes.OBJECT_TYPE
import org.jetbrains.kotlin.resolve.jvm.AsmTypes.UNIT_TYPE
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin.Companion.NO_ORIGIN
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.typeUtil.isDouble
import org.jetbrains.kotlin.types.typeUtil.isInt
import org.jetbrains.kotlin.types.typeUtil.isLong
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.org.objectweb.asm.Handle
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_FINAL
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.jetbrains.org.objectweb.asm.Opcodes.H_INVOKESTATIC
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.Method
import java.lang.invoke.LambdaMetafactory
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.DoubleConsumer
import java.util.function.IntConsumer
import java.util.function.LongConsumer
import java.util.function.ObjDoubleConsumer
import java.util.function.ObjIntConsumer
import java.util.function.ObjLongConsumer

/**
 *
 * @author MiNG
 * @since 1.0.0
 */
class UnitFuncCodegenExtension(private val messageCollector: MessageCollector) : ClassBuilderInterceptorExtension, ExpressionCodegenExtension {

    companion object {

        private val LAMBDA_METAFACTORY_HANDLE = Handle(
            H_INVOKESTATIC,
            type<LambdaMetafactory>().internalName,
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false
        )

        private fun render(declarationDescriptor: DeclarationDescriptor) =
            DescriptorRenderer.DEBUG_TEXT.render(declarationDescriptor)

        private fun render(type: KotlinType) =
            DescriptorRenderer.DEBUG_TEXT.renderType(type)

        private inline fun <reified T> type(): Type =
            Type.getType(T::class.java)

        private fun functionMethodType(arity: Int, returnType: Type = OBJECT_TYPE): Type {
            require(arity >= 0) { "arity must be non negative" }
            return Type.getMethodType(returnType, *(0 until arity).map { OBJECT_TYPE }.toTypedArray())
        }
    }

    override fun interceptClassBuilderFactory(interceptedFactory: ClassBuilderFactory, bindingContext: BindingContext, diagnostics: DiagnosticSink) =
        object : ClassBuilderFactory by interceptedFactory {
            override fun newClassBuilder(origin: JvmDeclarationOrigin) =
                object : DelegatingClassBuilder() {

                    private val delegate = interceptedFactory.newClassBuilder(origin)
                    override fun getDelegate() = delegate

                    override fun newMethod(origin: JvmDeclarationOrigin, access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?) =
                        origin.descriptor
                            // is function
                            .let { it as? FunctionDescriptor }
                            // is class member (TODO: should handle top-level functions)
                            ?.takeIf { it.containingDeclaration is ClassDescriptor }
                            // match the pre-condition for generating
                            ?.takeIf { it.hasValidUnitFunctionParameter() && it.isGeneralFunction() }
                            ?.let {
                                messageCollector.report(WARNING, "marking ${render(it)} synthetic")
                                super.newMethod(origin, access or ACC_SYNTHETIC, name, desc, signature, exceptions)
                            }
                            ?: super.newMethod(origin, access, name, desc, signature, exceptions)
                }
        }

    override fun generateClassSyntheticParts(codegen: ImplementationBodyCodegen) {

        // all functions which contain `(...) -> Unit` parameter
        val allMatchedFunctions = with(codegen.descriptor.unsubstitutedMemberScope) {
            getFunctions()
                .filter {
                    it.hasValidUnitFunctionParameter() && it.isGeneralFunction()
                }
                .onEach {
                    messageCollector.report(INFO, "discover function: ${render(it)}")
                }
        }

        with(ClassGeneratingContext(codegen)) {
            allMatchedFunctions.forEach {
                generateJvmStubForUnitFunctionParameter(it as FunctionDescriptorImpl)
            }
        }
    }

    private fun ClassGeneratingContext.generateJvmStubForUnitFunctionParameter(originFunction: FunctionDescriptorImpl) {
        with(codegen) {

            val classDescriptor = descriptor

            val subsititudedParameters = arrayOfNulls<FunctionSamBinding>(originFunction.valueParameters.size)

            // susititude function type to SAM interface type in parameters
            val generatingFunction = originFunction.copy { i, it ->
                subsititudeKotlinFunctionType(this, it)
                    .also {
                        subsititudedParameters[i] = it
                    }
                    .samType
                    .type
            }

            // get or generate adapter methods
            val samAdapters = subsititudedParameters
                .map { samBinding ->
                    if (samBinding != null) {
                        generatedAdapterMethods.computeIfAbsent(samBinding.samType.type.asmType(typeMapper)) {
                            generateAdapterMethod(samBinding)
                        }
                    } else null
                }

            // generate sam stub method
            functionCodegen.generateMethod(NO_ORIGIN, generatingFunction, object : CodegenBased(state) {
                override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
                    with(codegen.v) {

                        val isStatic = generatingFunction.isJvmStaticInObjectOrClassOrInterface()
                        val firstArgIndex: Int
                        val invokeStaticOrInstance: (String, String, String, Boolean) -> Unit

                        // dispatch reciever if required
                        if (isStatic) {
                            firstArgIndex = 0
                            invokeStaticOrInstance = ::invokestatic
                        } else {
                            firstArgIndex = 1
                            invokeStaticOrInstance = ::invokevirtual // TODO: optimize for final method
                            // <- this
                            load(0, OBJECT_TYPE)
                        }

                        // dispatch arguments
                        generatingFunction.valueParameters.forEachIndexed { i, p ->
                            val samBinding = subsititudedParameters[i]
                            val samAdapter = samAdapters[i]
                            // <- argN
                            load(firstArgIndex + i, p.type.asmType(typeMapper))
                            when {
                                (samBinding == null) && (samAdapter == null) -> return@forEachIndexed
                                (samBinding != null) && (samAdapter != null) ->
                                    // -> argN: SamType
                                    // <- argN: FunctionN
                                    codegen.invokeSamAdapter(classDescriptor, samAdapter, samBinding)
                                else -> error("\"sameType\" and \"samAdapters\" must be both empty or not")
                            }
                        }

                        // call origin method
                        // -> this, argN...
                        // <- return
                        invokeStaticOrInstance(
                            typeMapper.classInternalName(classDescriptor),
                            originFunction.name.identifier,
                            originFunction.computeJvmDescriptor(withName = false),
                            false
                        )

                        // return
                        areturn(typeMapper.mapReturnType(originFunction))
                    }
                }
            })
        }
    }

    private fun ImplementationBodyCodegen.subsititudeKotlinFunctionType(functionDescriptor: FunctionDescriptor, parameterType: KotlinType): FunctionSamBinding {
        check(parameterType.isUnitFunction()) {
            "${render(parameterType)} in ${render(functionDescriptor)} must be unit function"
        }
        val functionParameterCount = parameterType.arguments.size - 1
        val samType = when (functionParameterCount) {
            // `() -> Unit` => Runnable
            0 -> kotlinType<Runnable>()
            // `(T) -> Unit` => Consumer<T>
            1 -> {
                val (arg1) = parameterType.arguments
                when {
                    // `(Int) -> Unit` => IntConsumer
                    arg1.type.isInt() -> kotlinType<IntConsumer>()
                    // `(Long) -> Unit` => LongConsumer
                    arg1.type.isLong() -> kotlinType<LongConsumer>()
                    // `(Double) -> Unit` => DoubleConsumer
                    arg1.type.isDouble() -> kotlinType<DoubleConsumer>()
                    // `(*) -> Unit` => Consumer<T>
                    else -> kotlinType<Consumer<*>>(arg1)
                }
            }
            // `(T, U) -> Unit` => BiConsumer<T, U>
            2 -> {
                val (arg1, arg2) = parameterType.arguments
                when {
                    // `(*, Int) -> Unit` => ObjIntConsumer
                    arg2.type.isInt() -> kotlinType<ObjIntConsumer<*>>(arg1)
                    // `(*, Long) -> Unit` => ObjLongConsumer
                    arg2.type.isLong() -> kotlinType<ObjLongConsumer<*>>(arg1)
                    // `(*, Double) -> Unit` => ObjDoubleConsumer
                    arg2.type.isDouble() -> kotlinType<ObjDoubleConsumer<*>>(arg1)
                    // `(*, *) -> Unit` => Consumer<T>
                    else -> kotlinType<BiConsumer<*, *>>(arg1, arg2)
                }
            }
            // no matched java functional type
            else -> error("unsupported parameter type: ${render(parameterType)} in ${render(functionDescriptor)}")
        }

        return FunctionSamBinding(parameterType, SamType.create(samType), functionParameterCount)
    }

    private fun ImplementationBodyCodegen.generateAdapterMethod(samBinding: FunctionSamBinding): Method {

        val samTypeSimpleName = samBinding.samType.javaClassDescriptor.name.identifier.split('.').last()
        val functionAdapterMethodName = "unitfunc$$samTypeSimpleName"

        val samMethod = samBinding.samType.originalAbstractMethod

        // f: SamType
        val samInterfaceType = samBinding.samType.type.asmType(typeMapper)
        // arg1: T1, ..., argN: Tn
        val parameterTypes = samMethod
            .valueParameters
            .map {
                it.type.asmType(typeMapper)
            }
            .toTypedArray()
        // (f: SamType, arg1: T1, ..., argN: Tn): Unit
        val method = Method(functionAdapterMethodName, UNIT_TYPE, arrayOf(samInterfaceType, *parameterTypes))

        v.generateMethod(functionAdapterMethodName, ACC_SYNTHETIC or ACC_PRIVATE or ACC_STATIC or ACC_FINAL, method, null, NO_ORIGIN, state) {
            // <- f: SamType
            load(0, OBJECT_TYPE)
            // <- arg1, ..., argN
            samMethod.valueParameters.forEachIndexed { i, it ->
                // <- argN
                load(i + 1, it.type.asmType(typeMapper))
            }
            // f(arg1, ..., argN)
            // -> f, arg1, ..., argN
            invokeinterface(samInterfaceType.internalName, samMethod.name.identifier, samMethod.computeJvmDescriptor(withName = false))
            getstatic(UNIT_TYPE.internalName, "INSTANCE", UNIT_TYPE.descriptor)
            // areturn(OBJECT_TYPE) // ARETURN auto-generated
        }

        return method
    }

    private fun ExpressionCodegen.invokeSamAdapter(classDescriptor: ClassDescriptor, samAdapter: Method, samBinding: FunctionSamBinding) {
        v.invokedynamic(
            // the invocation method name
            OperatorNameConventions.INVOKE.identifier,
            // generated callsite signauture `(samType):kotlinFunctionType`
            Type.getMethodDescriptor(asmType(samBinding.functionType), asmType(samBinding.samType.type)),
            LAMBDA_METAFACTORY_HANDLE,
            // bsm args
            arrayOf(
                // arg 3: sam method type -- FunctionN.invoke(Any...)Any
                functionMethodType(samBinding.arity),
                // arg 4: target method handle
                Handle(
                    H_INVOKESTATIC,
                    asmType(classDescriptor.defaultType).internalName,
                    samAdapter.name,
                    samAdapter.descriptor,
                    false
                ),
                // arg 5: target method type -- impl(T)Unit
                // NOTE: return type must be UNIT_TYPE, because SAM adapter method is always return kotlin.Unit
                functionMethodType(samBinding.arity, UNIT_TYPE)
            )
        )
    }

    private inline fun FunctionDescriptor.copy(parameterTypeSubstitutor: FunctionDescriptor.(Int, KotlinType) -> KotlinType) =
        newCopyBuilder()
            .setModality(FINAL)
            .build()!!
            .apply {
                this as FunctionDescriptorImpl
                isInline = false
                isInfix = false
                isOperator = false
                isTailrec = false
                initialize(
                    extensionReceiverParameter,
                    dispatchReceiverParameter,
                    typeParameters,
                    valueParameters.mapIndexed { i, it ->
                        it.copy {
                            parameterTypeSubstitutor(i, type)
                        }
                    },
                    returnType,
                    modality,
                    visibility
                )
            }

    private inline fun ValueParameterDescriptor.copy(functionTypeSubstitutor: ValueParameterDescriptor.() -> KotlinType): ValueParameterDescriptor =
        ValueParameterDescriptorImpl(
            containingDeclaration = containingDeclaration,
            original = null,
            index = index,
            annotations = annotations,
            name = name,
            outType = if (type.isUnitFunction()) functionTypeSubstitutor() else type, // replace type of unit function type parameters
            declaresDefaultValue = declaresDefaultValue(),
            isCrossinline = false,
            isNoinline = false,
            varargElementType = varargElementType, // TODO: Handle varargElementType
            source = NO_SOURCE
        )

    private inline fun <reified T> ImplementationBodyCodegen.kotlinType(vararg reifiedTypes: TypeProjection): KotlinType {
        val classDescriptor = state.jvmBackendClassResolver.resolveToClassDescriptors(Type.getType(T::class.java)).single()
        return KotlinTypeFactory.simpleNotNullType(EMPTY, classDescriptor, reifiedTypes.asList())
    }

    private fun FunctionDescriptor.hasValidUnitFunctionParameter() =
        valueParameters.any {
            it.type.isUnitFunction() && it.type.isJdkSupportFunctionType()
        }

    private fun FunctionDescriptor.isGeneralFunction(): Boolean {
        if (!visibility.isPublicAPI) {
            messageCollector.report(WARNING, "${render(this)} is not public")
            return false
        }
        if (isActual || isExpect) {
            messageCollector.report(WARNING, "${render(this)} is multiplatform expect/actual")
            return false
        }
        if (isExternal) {
            messageCollector.report(WARNING, "${render(this)} is external")
            return false
        }
        if (isSuspend) {
            messageCollector.report(WARNING, "${render(this)} is suspend")
            return false
        }
        return true
    }

    private fun KotlinType.isUnitFunction() =
        isFunctionType // strictly function type, not include KFunction and SuspendFunction
                && getReturnTypeFromFunctionType().isUnit()

    private fun KotlinType.isJdkSupportFunctionType(): Boolean {
        require(isFunctionType) { "${render(this)} must be function type" }
        // when(type.arguments.size) {
        //     0 -> Never happens, kotlin functions always have return type!
        //     1 -> Runnable
        //     2 -> Consumer<T>
        //     3 -> BiConumser<T, U>
        //     else -> No matching functional interface in JDK
        // }
        return (arguments.size - 1 < 3)
            .also {
                if (!it) {
                    messageCollector.report(WARNING, "${render(this)} no suitable jdk functional interface")
                }
            }
    }

    private fun MemberScope.getFunctions() =
        getFunctionNames()
            .flatMap { getContributedFunctions(it, NoLookupLocation.FROM_BACKEND) }

    private data class ClassGeneratingContext(
        val codegen: ImplementationBodyCodegen,
        val generatedAdapterMethods: MutableMap<Type, Method> = mutableMapOf()
    )

    private data class FunctionSamBinding(
        val functionType: KotlinType,
        val samType: SamType,
        val arity: Int
    )
}