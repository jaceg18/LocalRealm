package com.jaceg18.localrealm.core;

import com.jaceg18.localrealm.core.build.Util;
import javafx.concurrent.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServerService {

    private Process runningServerProcess;
    private PrintWriter serverInputWriter;

    private final Consumer<String> consoleOut;
    private final Consumer<String> serverConsoleOut;
    private final Consumer<String> statusOut;

    public ServerService(Consumer<String> consoleOut,
                         Consumer<String> serverConsoleOut,
                         Consumer<String> statusOut) {
        this.consoleOut = consoleOut;
        this.serverConsoleOut = serverConsoleOut;
        this.statusOut = statusOut;
    }

    public boolean isServerRunning() {
        return runningServerProcess != null && runningServerProcess.isAlive();
    }

    public void sendCommand(String command) {
        if (serverInputWriter != null && isServerRunning()) {
            serverInputWriter.println(command);
            serverInputWriter.flush();
            serverConsoleOut.accept("> " + command);
        }
    }

    public void stopServer() {
        sendCommand("stop");
    }

    public Task<Void> buildServerTask(String selectedBuildKey, String url, Path folderPath, String fileName, int minMem, int maxMem, boolean autoEulaSelected) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                Files.createDirectories(folderPath);

                updateMessage("Downloading server jar...");
                updateProgress(0.2, 1.0);
                consoleOut.accept("=== Starting Build Process ===");
                consoleOut.accept("[INFO] Memory: " + minMem + "GB min, " + maxMem + "GB max");
                statusOut.accept("Building...");

                consoleOut.accept("[INFO] Downloading server jar from: " + selectedBuildKey);
                Util.downloadToFolder(url, folderPath, fileName);
                consoleOut.accept("[INFO] Download complete!");

                updateMessage("Starting server to generate eula.txt...");
                updateProgress(0.5, 1.0);
                consoleOut.accept("[INFO] Starting server to generate eula.txt...");

                boolean initNoGui = true;
                Process initProcess = Util.doServerProcess(folderPath, fileName, minMem, maxMem, initNoGui);

                captureProcessOutput(initProcess, true, false);

                Path eulaFile = folderPath.resolve("eula.txt");
                int waitCount = 0;

                while (!Files.exists(eulaFile) && waitCount < 60) {
                    Thread.sleep(500);
                    waitCount++;
                    if (!initProcess.isAlive()) {
                        int exitCode = initProcess.exitValue();
                        if (exitCode != 0) {
                            consoleOut.accept("[WARN] Init process exited with code: " + exitCode);
                        }
                        break;
                    }
                }

                Thread.sleep(1000);

                if (initProcess.isAlive()) {
                    consoleOut.accept("[INFO] Stopping initialization server...");
                    initProcess.destroy();
                    try {
                        if (!initProcess.waitFor(3, TimeUnit.SECONDS)) {
                            initProcess.destroyForcibly();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        initProcess.destroyForcibly();
                    }
                }

                if (autoEulaSelected) {
                    updateMessage("Setting up EULA...");
                    updateProgress(0.8, 1.0);
                    consoleOut.accept("[INFO] Setting up EULA...");

                    waitCount = 0;
                    while (!Files.exists(eulaFile) && waitCount < 10) {
                        Thread.sleep(200);
                        waitCount++;
                    }

                    if (Files.exists(eulaFile)) {
                        Util.autoEula(folderPath);
                        consoleOut.accept("[INFO] EULA accepted automatically!");
                    } else {
                        consoleOut.accept("[WARN] eula.txt not found, skipping auto-EULA");
                    }
                }

                updateMessage("Complete!");
                updateProgress(1.0, 1.0);
                consoleOut.accept("[SUCCESS] Build process complete!");
                statusOut.accept("Build Complete");

                return null;
            }
        };
    }

    public void runServer(Path serverPath, int minMem, int maxMem, boolean nogui) {
        try {
            if (isServerRunning()) {
                runningServerProcess.destroyForcibly();
            }

            File serverFolder = serverPath.toFile();
            String fileName = "server.jar";

            serverConsoleOut.accept("[INFO] Starting server with " + minMem + "GB min, " + maxMem + "GB max...");

            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-Xms" + minMem + "G",
                    "-Xmx" + maxMem + "G",
                    "-jar", fileName
            );

            if (nogui) pb.command().add("nogui");

            runningServerProcess = pb
                    .directory(serverFolder)
                    .redirectErrorStream(true)
                    .start();

            serverInputWriter = new PrintWriter(
                    new OutputStreamWriter(runningServerProcess.getOutputStream(), StandardCharsets.UTF_8),
                    true
            );

            captureProcessOutput(runningServerProcess, false, true);

            statusOut.accept("Server Running: " + serverPath.getFileName());
            serverConsoleOut.accept("[INFO] Server started. Type commands and press Enter.");
        } catch (Exception ex) {
            String error = "Failed to start server: " + ex.getMessage();
            serverConsoleOut.accept("[ERROR] " + error);
            statusOut.accept("Server Failed");
            throw new RuntimeException(error, ex);
        }
    }

    public void shutdownHookStop() {
        stopServer();
        consoleOut.accept("Shutting down any running servers.");
        serverConsoleOut.accept("Shutting down server.");
    }

    private void captureProcessOutput(Process process, boolean isInit, boolean useServerConsole) {
        new Thread(() -> {
            Consumer<String> out = useServerConsole ? serverConsoleOut : consoleOut;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (process.isAlive() || isInit) {
                        out.accept(line);
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
                if (process.isAlive() || isInit) {
                    out.accept("[ERROR] Error reading process output: " + e.getMessage());
                }
            } finally {
                if (!isInit && process != null) {
                    out.accept("[INFO] Server process ended.");
                    statusOut.accept("Server Stopped");
                }
            }
        }, "server-output-reader").start();
    }
}
