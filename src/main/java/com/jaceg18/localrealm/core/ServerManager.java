package com.jaceg18.localrealm.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerManager {

    private static final Path SERVERS_FILE = Paths.get(System.getProperty("user.home"), ".localrealm", "servers.txt");

    public static void saveServer(Server server) throws IOException {
        Files.createDirectories(SERVERS_FILE.getParent());
        Files.writeString(SERVERS_FILE, server.name() + "|" + server.path().toAbsolutePath() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public static List<Server> loadServers() throws IOException {
        if (!Files.exists(SERVERS_FILE)) return Collections.emptyList();
        List<Server> servers = new ArrayList<>();
        Files.readAllLines(SERVERS_FILE).forEach(l -> {
            if (!(l = l.trim()).contains("|")) return;
            String[] parts = l.split("\\|", 2);
            if (parts.length == 2) servers.add(new Server(parts[0], Paths.get(parts[1])));
        });

        return servers;
    }

    public static void removeServer(String name) throws IOException {
        if (!Files.exists(SERVERS_FILE)) return;
        List<String> newLines = new ArrayList<>();
        Files.readAllLines(SERVERS_FILE).forEach(l -> {if (!l.trim().startsWith(name + "|")) newLines.add(l);});
        Files.write(SERVERS_FILE, newLines);
    }

}
