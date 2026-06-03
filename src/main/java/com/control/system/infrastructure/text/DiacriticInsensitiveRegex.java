package com.control.system.infrastructure.text;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds a MongoDB-compatible "contains" regex that is insensitive to diacritics and case,
 * matching directly against the stored (accented) field. This keeps the domain entity free
 * of denormalised search columns: the matching logic lives entirely here.
 *
 * <p>Example: {@code "Andino"} -> {@code [aá..][nñ]d[ií..]n[oó..]}, queried with the {@code i}
 * option, so it matches stored {@code "Andinó"}. The pattern is built only from fixed
 * character classes and escaped literals, so user input cannot inject regex or cause ReDoS.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DiacriticInsensitiveRegex {

    /** Lower-case base letter -> all accepted (accented) variants for that letter. */
    private static final Map<Character, String> EQUIVALENTS = Map.of(
        'a', "aáàäâã",
        'e', "eéèëê",
        'i', "iíìïî",
        'o', "oóòöôõ",
        'u', "uúùüû",
        'n', "nñ",
        'c', "cç"
    );

    private static final String REGEX_SPECIALS = "\\.[]{}()*+-?^$|";

    /**
     * @return the regex source for a case/accent-insensitive contains, or {@code null} when
     *         the term is blank. Use it with the {@code "i"} option on the Mongo query.
     */
    public static String containsPattern(final String term) {
        if (StringUtils.isBlank(term)) {
            return null;
        }
        final String base = stripAccentsLower(term);
        final StringBuilder pattern = new StringBuilder(base.length() * 4);
        for (final char c : base.toCharArray()) {
            final String variants = EQUIVALENTS.get(c);
            if (variants != null) {
                pattern.append('[').append(variants).append(']');
            } else if (REGEX_SPECIALS.indexOf(c) >= 0) {
                pattern.append('\\').append(c);
            } else {
                pattern.append(c);
            }
        }
        return pattern.toString();
    }

    private static String stripAccentsLower(final String input) {
        final String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        final String stripped = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", StringUtils.EMPTY);
        return StringUtils.lowerCase(StringUtils.trim(stripped));
    }
}
