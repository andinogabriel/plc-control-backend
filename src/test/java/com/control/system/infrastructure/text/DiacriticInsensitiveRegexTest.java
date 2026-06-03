package com.control.system.infrastructure.text;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DiacriticInsensitiveRegexTest {

    private boolean matches(final String term, final String stored) {
        final String regex = DiacriticInsensitiveRegex.containsPattern(term);
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(stored).find();
    }

    @Test
    void returnsNullForBlankInput() {
        assertThat(DiacriticInsensitiveRegex.containsPattern("  ")).isNull();
        assertThat(DiacriticInsensitiveRegex.containsPattern(null)).isNull();
    }

    @Test
    void matchesIgnoringAccentsAndCase() {
        assertThat(matches("gabriel", "Gabriel Andinó")).isTrue();
        assertThat(matches("andino", "Gabriel Andinó")).isTrue();
        assertThat(matches("ANDINO", "gabriel andinó")).isTrue();
    }

    @Test
    void matchesAccentedQueryAgainstUnaccentedStored() {
        assertThat(matches("Andinó", "andino")).isTrue();
    }

    @Test
    void behavesAsContainsNotEquals() {
        assertThat(matches("brie", "Gabriel")).isTrue();
        assertThat(matches("xyz", "Gabriel")).isFalse();
    }

    @Test
    void treatsRegexMetacharactersAsLiterals() {
        // '.' must not act as "any char": searching "a.b" should not match "axb".
        assertThat(matches("a.b", "axb")).isFalse();
        assertThat(matches("a.b", "a.b stored")).isTrue();
    }

    @Test
    void matchesEmailContains() {
        assertThat(matches("example", "Gabriel@Example.COM")).isTrue();
    }
}
