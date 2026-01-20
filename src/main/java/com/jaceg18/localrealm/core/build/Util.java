package com.jaceg18.localrealm.core.build;

import com.jaceg18.localrealm.core.manager.BuildOptionsManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {
    private static Map<String, String> BUILD_OPTIONS_CACHE = null;

    public static Map<String, String> getBuildOptions() {
        if (BUILD_OPTIONS_CACHE == null) {
            try {
                BUILD_OPTIONS_CACHE = BuildOptionsManager.loadBuildOptions();
            } catch (IOException e) {
                BUILD_OPTIONS_CACHE = new HashMap<>();
                BUILD_OPTIONS_CACHE.put("Paper 1.21.8", "https://fill-data.papermc.io/v1/objects/8de7c52c3b02403503d16fac58003f1efef7dd7a0256786843927fa92ee57f1e/paper-1.21.8-60.jar");
            }
        }
        return BUILD_OPTIONS_CACHE;
    }

    public static void reloadBuildOptions() {
        BUILD_OPTIONS_CACHE = null;
    }

    public static Map<String, String> getElementsFromFile(Path file) throws IOException {
        return KeyValueLoader.loadKeyValues(file);

    }

    public static void saveElementsToFile(Path file, Map<String, String> elements) throws IOException {
        List<String> lines = new ArrayList<>();
        elements.forEach((key, value) -> lines.add(key + ":" + value));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static List<String> buildCmd(String fileName, int minAloc, int maxAloc, boolean noGui) {
        List<String> cmd = new ArrayList<>(List.of("java", "-Xms" + minAloc + "G", "-Xmx" + maxAloc + "G", "-jar", fileName));
        if (noGui) {
            cmd.add("nogui");
        }
        return cmd;
    }

    public static Process doServerProcess(Path folder, String fileName, int minAloc, int maxAloc, boolean noGui) throws IOException {
        Files.createDirectories(folder);
        ProcessBuilder pb = new ProcessBuilder(buildCmd(fileName, minAloc, maxAloc, noGui));
        pb.directory(folder.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    public static void autoEula(Path folder) throws IOException {
        Path eulaFile = folder.resolve("eula.txt");

        if (!Files.exists(eulaFile)) {
            throw new IOException("eula.txt does not exist yet");
        }

        String content = Files.readString(eulaFile, StandardCharsets.UTF_8);
        content = content.replace("eula=false", "eula=true");
        if (!content.contains("eula=true")) {
            content = "eula=true\n" + content;
        }
        Files.writeString(eulaFile, content, StandardCharsets.UTF_8);
    }

    public static Path downloadToFolder(String url, Path folder, String fileName)
            throws Exception {

        Files.createDirectories(folder);

        Path output = folder.resolve(fileName);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "ServerCreator")
                .GET()
                .build();

        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Download failed: HTTP " + response.statusCode());
        }

        Path temp = Files.createTempFile(folder, "dl-", ".tmp");
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.move(temp, output,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

        return output;
    }

}
