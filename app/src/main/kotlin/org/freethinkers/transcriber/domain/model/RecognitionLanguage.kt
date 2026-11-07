package org.freethinkers.transcriber.domain.model

enum class RecognitionLanguage(val tag: String, val displayName: String) {
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English"),
    ;

    companion object {
        fun fromTag(tag: String): RecognitionLanguage? = entries.firstOrNull { it.tag == tag }
    }
}
