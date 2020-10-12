package ming.kotlin.unitfunc.gradle.model.impl

import ming.kotlin.unitfunc.gradle.model.UnitFunc
import java.io.Serializable

data class UnitFuncImpl(
    override val name: String,
    override val enabled: Boolean
) : UnitFunc, Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    override val modelVersion = serialVersionUID
}