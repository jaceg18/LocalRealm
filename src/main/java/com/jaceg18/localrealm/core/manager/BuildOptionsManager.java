package com.jaceg18.localrealm.core.manager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class BuildOptionsManager {

    private static final Path BUILD_OPTIONS_FILE = Paths.get(System.getProperty("user.home"), ".localrealm", "build-options.txt");

    public static Map<String, String> loadBuildOptions() throws IOException {
        Map<String, String> options = new LinkedHashMap<>();
        
        if (!Files.exists(BUILD_OPTIONS_FILE)) {
            options.put("Paper 1.21.8", "https://fill-data.papermc.io/v1/objects/8de7c52c3b02403503d16fac58003f1efef7dd7a0256786843927fa92ee57f1e/paper-1.21.8-60.jar");
            saveBuildOptions(options);
            return options;
        }
        
        Files.readAllLines(BUILD_OPTIONS_FILE).forEach(line -> {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) return;
            
            int separator = line.indexOf("|");
            if (separator > 0 && separator < line.length() - 1) {
                String name = line.substring(0, separator).trim();
                String url = line.substring(separator + 1).trim();
                if (!name.isEmpty() && !url.isEmpty()) {
                    options.put(name, url);
                }
            }
        });
        
        return options;
    }

    public static void saveBuildOptions(Map<String, String> options) throws IOException {
        Files.createDirectories(BUILD_OPTIONS_FILE.getParent());
        List<String> lines = new ArrayList<>();
        options.forEach((name, url) -> lines.add(name + "|" + url));
        Files.write(BUILD_OPTIONS_FILE, lines, StandardCharsets.UTF_8);
    }

    public static void addBuildOption(String name, String url) throws IOException {
        Map<String, String> options = loadBuildOptions();
        options.put(name, url);
        saveBuildOptions(options);
    }

    public static void removeBuildOption(String name) throws IOException {
        Map<String, String> options = loadBuildOptions();
        options.remove(name);
        saveBuildOptions(options);
    }

    public static void updateBuildOption(String oldName, String newName, String url) throws IOException {
        Map<String, String> options = loadBuildOptions();
        if (!oldName.equals(newName)) {
            options.remove(oldName);
        }
        options.put(newName, url);
        saveBuildOptions(options);
    }
}

