package com.jaceg18.localrealm.core.build;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaceg18.localrealm.annotation.Provisional;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Provisional(reason = "Short term sturdy solution", expiresBy = "2.4.0", replacement = "Full API extension")
public class KeyValueLoader {

    private KeyValueLoader(){}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Yaml YAML = new Yaml();

    public static Map<String, String> loadKeyValues(Path file) throws IOException {
        String ext = extensionOf(file).toLowerCase(Locale.ROOT);

        return switch (ext) {
            case "properties" -> loadProperties(file);
            case "yml", "yaml" -> loadYaml(file);
            case "json" -> loadJson(file);
            case "txt" -> loadLooseText(file);
            default -> Collections.emptyMap();
        };
    }


    private static Map<String, String> loadProperties(Path file) throws IOException {
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(r);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            out.put(name, props.getProperty(name));
        }
        return out;
    }


    private static Map<String, String> loadYaml(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            Object root = YAML.load(in);
            if (root == null) return new LinkedHashMap<>();
            if (!(root instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("YAML root must be a map/object");
            }
            Map<String, String> out = new LinkedHashMap<>();
            flattenToStrings(out, "", map);
            return out;
        }
    }

    private static Map<String, String> loadJson(Path file) throws IOException {
        JsonNode root;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = JSON.readTree(r);
        }

        Map<String, String> out = new LinkedHashMap<>();

        if (root.isObject()) {
            Map<String, Object> map = JSON.convertValue(root, new TypeReference<>() {});
            flattenObjectMap(out, "", map);
            return out;
        }

        if (root.isArray()) {
            int i = 0;
            for (JsonNode n : root) {
                if (n.isValueNode()) {
                    out.put(n.asText(), "true");
                } else {
                    out.put("[" + i + "]", n.toString());
                }
                i++;
            }
            return out;
        }

        throw new IllegalArgumentException("JSON root must be an object or array");
    }


    private static Map<String, String> loadLooseText(Path file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.startsWith("//") || line.startsWith(";")) continue;

            int colon = line.indexOf(':');
            int eq = line.indexOf('=');

            int idx;
            if (colon == -1) idx = eq;
            else if (eq == -1) idx = colon;
            else idx = Math.min(colon, eq);

            if (idx <= 0) continue;

            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if (!key.isEmpty()) out.put(key, value);
        }
        return out;
    }


    @SuppressWarnings("unchecked")
    private static void flattenObjectMap(Map<String, String> out, String prefix, Map<String, Object> map) {
        for (var e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();

            switch (v) {
                case Map<?, ?> nested -> {
                    flattenObjectMap(out, key, (Map<String, Object>) nested);
                }
                case List<?> list -> {
                    for (int i = 0; i < list.size(); i++) {
                        Object item = list.get(i);
                        String k2 = key + "[" + i + "]";
                        if (item instanceof Map<?, ?> m) {
                            flattenObjectMap(out, k2, (Map<String, Object>) m);
                        } else {
                            out.put(k2, String.valueOf(item));
                        }
                    }
                }
                default -> {
                    out.put(key, String.valueOf(v));
                }
            }
        }
    }

    private static void flattenToStrings(Map<String, String> out, String prefix, Map<?, ?> map) {
        for (var e : map.entrySet()) {
            String k = String.valueOf(e.getKey());
            String key = prefix.isEmpty() ? k : prefix + "." + k;
            Object v = e.getValue();

            if (v instanceof Map<?, ?> nested) {
                flattenToStrings(out, key, nested);
            } else if (v instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    String k2 = key + "[" + i + "]";
                    if (item instanceof Map<?, ?> m) {
                        flattenToStrings(out, k2, m);
                    } else {
                        out.put(k2, String.valueOf(item));
                    }
                }
            } else {
                out.put(key, String.valueOf(v));
            }
        }
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot == -1) ? "" : name.substring(dot + 1);
    }
}
