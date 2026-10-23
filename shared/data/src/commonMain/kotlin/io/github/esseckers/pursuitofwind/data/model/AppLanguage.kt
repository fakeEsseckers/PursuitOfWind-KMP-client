package io.github.esseckers.pursuitofwind.data.model

/**
 * The language the app shows. [SYSTEM] follows the phone; the others are the languages the app is translated into,
 * with their BCP 47 [tag].
 */
public enum class AppLanguage(
    public val tag: String?,
) {
    SYSTEM(null),
    ENGLISH("en"),
    RUSSIAN("ru"),
    ;

    public companion object {
        /** The language for a stored [tag]; an unknown or missing tag follows the phone. */
        public fun of(tag: String?): AppLanguage = entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
    }
}
