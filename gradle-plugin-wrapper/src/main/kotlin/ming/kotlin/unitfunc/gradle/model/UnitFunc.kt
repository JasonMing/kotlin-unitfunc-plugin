package ming.kotlin.unitfunc.gradle.model

/**
 * Entry point for Unit Func models.
 * Represents the description of annotations interpreted by 'kotlin-unitfunc' plugin.
 *
 * @author MiNG
 * @since 1.0.0
 */
interface UnitFunc {

    /**
     * Return a number representing the version of this API.
     * Always increasing if changed.
     *
     * @return the version of this model.
     */
    val modelVersion: Long

    /**
     * Returns the module (Gradle project) name.
     *
     * @return the module name.
     */
    val name: String

    /**
     * Indicates should the transformation be applied.
     *
     * @return the plugin is enabled or not.
     */
    val enabled: Boolean
}