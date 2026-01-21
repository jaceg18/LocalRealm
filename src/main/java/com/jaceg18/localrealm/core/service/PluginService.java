package com.jaceg18.localrealm.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaceg18.localrealm.core.build.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PluginService {
    private static final String SPIGOT_RESOURCES = "https://api.spiget.org/v2/resources";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Search for plugins on SpigotMC
     */
    public CompletableFuture<List<Plugin>> searchPlugins(String query, String sort, int size) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build query string - Spiget API format
                // Note: Spiget API field expansion uses array syntax or multiple field= params
                StringBuilder queryBuilder = new StringBuilder();
                queryBuilder.append("?size=").append(size);
                
                // Add search if provided
                if (query != null && !query.isEmpty()) {
                    queryBuilder.append("&field=name&search=").append(java.net.URLEncoder.encode(query, "UTF-8"));
                }
                
                // Add sort (default to downloads if not provided)
                if (sort != null && !sort.isEmpty()) {
                    queryBuilder.append("&sort=").append(sort);
                } else {
                    queryBuilder.append("&sort=-downloads");
                }
                
                // Expand author and icon fields - use multiple field= params
                // Some APIs use field[]=author&field[]=icon but Spiget uses field=author&field=icon
                queryBuilder.append("&field=author&field=icon");
                
                URI uri = URI.create(SPIGOT_RESOURCES + queryBuilder.toString());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                // Debug: print response if needed
                if (response.statusCode() != 200) {
                    System.err.println("API Error: " + response.statusCode() + " - " + response.body());
                }
                
                if (response.statusCode() != 200) {
                    throw new IOException("API returned status code: " + response.statusCode());
                }

                List<Plugin> plugins = new ArrayList<>();
                JsonNode root = OBJECT_MAPPER.readTree(response.body());
                
                if (root.isArray()) {
                    for (JsonNode resource : root) {
                        try {
                            plugins.add(parsePlugin(resource));
                        } catch (Exception ex) {
                            // Skip plugins that can't be parsed
                            System.err.println("Failed to parse plugin: " + ex.getMessage());
                        }
                    }
                } else if (root.isObject()) {
                    try {
                        plugins.add(parsePlugin(root));
                    } catch (Exception ex) {
                        System.err.println("Failed to parse plugin: " + ex.getMessage());
                    }
                }

                return plugins;
            } catch (Exception e) {
                throw new RuntimeException("Failed to search plugins: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Get plugin details by ID
     */
    public CompletableFuture<Plugin> getPluginById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = URI.create(SPIGOT_RESOURCES + "/" + id);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "LocalRealm/1.3")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    throw new IOException("API returned status code: " + response.statusCode());
                }

                JsonNode resource = OBJECT_MAPPER.readTree(response.body());
                return parsePlugin(resource);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get plugin: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Get latest version download URL for a plugin
     * Note: Free plugins can be downloaded directly, premium plugins require authentication
     */
    public CompletableFuture<String> getLatestDownloadUrl(int pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // First check if plugin is premium
                URI resourceUri = URI.create(SPIGOT_RESOURCES + "/" + pluginId);
                HttpRequest resourceRequest = HttpRequest.newBuilder()
                        .uri(resourceUri)
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "LocalRealm/1.3")
                        .GET()
                        .build();

                HttpResponse<String> resourceResponse = HTTP_CLIENT.send(resourceRequest, HttpResponse.BodyHandlers.ofString());
                
                if (resourceResponse.statusCode() != 200) {
                    throw new IOException("Failed to get plugin info: " + resourceResponse.statusCode());
                }

                JsonNode resourceNode = OBJECT_MAPPER.readTree(resourceResponse.body());
                boolean isPremium = resourceNode.has("premium") && resourceNode.get("premium").asBoolean();
                
                if (isPremium) {
                    throw new RuntimeException("Premium plugins require authentication and cannot be downloaded automatically. Please download from SpigotMC website.");
                }
                
                // Get latest version
                URI versionUri = URI.create(SPIGOT_RESOURCES + "/" + pluginId + "/versions/latest");
                HttpRequest versionRequest = HttpRequest.newBuilder()
                        .uri(versionUri)
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "LocalRealm/1.3")
                        .GET()
                        .build();

                HttpResponse<String> versionResponse = HTTP_CLIENT.send(versionRequest, HttpResponse.BodyHandlers.ofString());
                
                if (versionResponse.statusCode() != 200) {
                    throw new IOException("Failed to get plugin version: " + versionResponse.statusCode());
                }

                JsonNode versionNode = OBJECT_MAPPER.readTree(versionResponse.body());
                int versionId = versionNode.get("id").asInt();
                
                // Direct download URL - note: some servers may require proper User-Agent
                String downloadUrl = "https://api.spiget.org/v2/resources/" + pluginId + "/versions/" + versionId + "/download";
                
                // Test if accessible
                HttpRequest testRequest = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build();
                
                HttpResponse<Void> testResponse = HTTP_CLIENT.send(testRequest, HttpResponse.BodyHandlers.discarding());
                
                if (testResponse.statusCode() == 403 || testResponse.statusCode() == 401) {
                    // Alternative: use direct download without version ID
                    return "https://api.spiget.org/v2/resources/" + pluginId + "/download";
                }
                
                return downloadUrl;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get download URL: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Download and install plugin to server's plugins folder
     */
    public CompletableFuture<Path> downloadPlugin(String downloadUrl, Path serverPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create plugins directory if it doesn't exist
                Path pluginsDir = serverPath.resolve("plugins");
                Files.createDirectories(pluginsDir);

                // Extract filename from URL or use a default
                String filename = extractFilename(downloadUrl);
                if (filename == null || !filename.endsWith(".jar")) {
                    filename = "plugin.jar";
                }

                Path pluginFile = pluginsDir.resolve(filename);

                // Download the plugin with proper headers to avoid 403
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .header("Accept", "*/*")
                        .header("Referer", "https://www.spigotmc.org/")
                        .GET()
                        .build();

                HttpResponse<Path> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(pluginFile));
                
                if (response.statusCode() != 200) {
                    throw new IOException("Download failed with status code: " + response.statusCode());
                }

                return pluginFile;
            } catch (Exception e) {
                throw new RuntimeException("Failed to download plugin: " + e.getMessage(), e);
            }
        });
    }

    private Plugin parsePlugin(JsonNode node) {
        int id = node.get("id").asInt();
        String name = node.has("name") ? node.get("name").asText() : "Unknown";
        String description = node.has("tag") ? node.get("tag").asText() : "";
        
        // Parse author - can be object or ID
        String author = "Unknown";
        if (node.has("author")) {
            JsonNode authorNode = node.get("author");
            if (authorNode.isObject()) {
                if (authorNode.has("name")) {
                    author = authorNode.get("name").asText();
                } else if (authorNode.has("username")) {
                    author = authorNode.get("username").asText();
                }
            } else if (authorNode.isTextual()) {
                // Sometimes author is just a string
                author = authorNode.asText();
            } else if (authorNode.isInt() || authorNode.isNumber()) {
                // Author is just an ID - this happens when field expansion doesn't work
                // For now, we'll try to extract name from other fields
                author = "Unknown (ID: " + authorNode.asInt() + ")";
            }
        }
        
        
        // Icon URL - Spiget API format
        // Spiget API provides icons via direct endpoint
        String iconUrl = "https://api.spiget.org/v2/resources/" + id + "/icon";
        
        // Try to get icon from JSON first if available
        if (node.has("icon")) {
            JsonNode iconNode = node.get("icon");
            if (iconNode.has("data")) {
                String iconData = iconNode.get("data").asText();
                // If it's already a data URI, use it directly
                if (iconData != null && !iconData.isEmpty()) {
                    if (iconData.startsWith("data:")) {
                        iconUrl = iconData;
                    } else {
                        // Otherwise construct data URI
                        iconUrl = "data:image/png;base64," + iconData;
                    }
                }
            } else if (iconNode.has("url") && iconNode.get("url").asText() != null && !iconNode.get("url").asText().isEmpty()) {
                iconUrl = iconNode.get("url").asText();
            }
        }
        
        // Price (premium plugins)
        double price = node.has("price") ? node.get("price").asDouble() : 0.0;
        
        // Downloads
        int downloads = node.has("downloads") ? node.get("downloads").asInt() : 0;
        
        // Rating
        double rating = node.has("rating") && node.get("rating").has("average")
            ? node.get("rating").get("average").asDouble() : 0.0;
        
        // Version
        String version = node.has("version") && node.get("version").has("name")
            ? node.get("version").get("name").asText() : "";

        return new Plugin(id, name, description, author, iconUrl, null, version, price, downloads, rating);
    }

    private String extractFilename(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                String[] parts = path.split("/");
                return parts[parts.length - 1];
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}

