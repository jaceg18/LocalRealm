package com.jaceg18.localrealm.core.build;

public record Plugin(
    int id,
    String name,
    String description,
    String author,
    String iconUrl,
    String downloadUrl,
    String version,
    double price,
    int downloads,
    double rating
) {
    public String getDisplayName() {
        return name + (version != null && !version.isEmpty() ? " v" + version : "");
    }
}

