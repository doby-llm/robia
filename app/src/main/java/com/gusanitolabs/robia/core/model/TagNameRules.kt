package com.gusanitolabs.robia.core.model

import java.text.Normalizer
import java.util.Locale

/** Name comparison shared by tags and colours: accents, case and extra spaces do not make a name different. */
object TagNameRules {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    fun normalize(name: String): String {
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
        return combiningMarks.replace(decomposed, "")
            .trim()
            .replace(whitespace, " ")
            .lowercase(Locale.ROOT)
    }

    /**
     * Key for auto-merge only. Accents are kept, so 'Mantel' and 'Mäntel' stay different tags: a merge drops a row and its
     * description, so it must not treat two different words as one.
     */
    fun dedupeKey(name: String): String = name.trim().replace(whitespace, " ").lowercase(Locale.ROOT)

    /** True when [candidate] equals any of [existingLabels] after normalisation. Empty candidates never conflict. */
    fun conflicts(candidate: String, existingLabels: Iterable<String>): Boolean {
        val key = normalize(candidate)
        return key.isNotEmpty() && existingLabels.any { label -> normalize(label) == key }
    }
}
