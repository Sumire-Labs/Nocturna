package org.embeddedt.embeddium;

import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class LanguageResourcesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]|\\{\\d+\\}");

    @Test
    void importedTranslationsKeepKnownKeysAndFormattingArguments() throws Exception {
        for (String namespace : new String[] {"embeddium", "sodium"}) {
            var base = readLanguage(namespace, "en_us");
            for (String language : new String[] {"es_es", "ko_kr", "ru_ru"}) {
                var translated = readLanguage(namespace, language);
                assertFalse(translated.isEmpty());
                for (var entry : translated.entrySet()) {
                    String context = namespace + "/" + language + ": " + entry.getKey();
                    assertTrue(base.containsKey(entry.getKey()), context);
                    assertEquals(placeholders(base.get(entry.getKey())), placeholders(entry.getValue()), context);
                }
            }
        }
    }

    private static Map<String, String> readLanguage(String namespace, String language) throws Exception {
        String resource = "/assets/" + namespace + "/lang/" + language + ".json";
        var input = LanguageResourcesTest.class.getResourceAsStream(resource);
        assertNotNull(input, resource);
        try (var reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            var entries = new HashMap<String, String>();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                assertNull(entries.put(key, reader.nextString()), "Duplicate key in " + resource + ": " + key);
            }
            reader.endObject();
            return entries;
        }
    }

    private static java.util.List<String> placeholders(String value) {
        var matches = new ArrayList<String>();
        var matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        matches.sort(String::compareTo);
        return matches;
    }
}
