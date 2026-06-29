package com.lfernandes.modusweb.utils;

import java.text.Normalizer;
import java.util.Locale;

/** Utilitário para geração de slugs URL-safe */
public final class SlugUtils {

    private SlugUtils() {}

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
    }
}
