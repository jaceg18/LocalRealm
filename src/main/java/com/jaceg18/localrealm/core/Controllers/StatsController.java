package com.jaceg18.localrealm.core.Controllers;

import com.jaceg18.localrealm.core.service.ServerService;
import javafx.application.Platform;
import javafx.scene.control.Label;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Fast-ish stats updater for a locally launched MC server process.
 * - No blocking OS calls on the JavaFX thread.
 * - Memory: Windows uses PowerShell Get-Process WorkingSet64, Unix uses ps rss.
 * - CPU: uses ProcessHandle totalCpuDuration delta / elapsed delta (cheap, cross-platform-ish).
 * - Threads: uses /proc/<pid>/status on Linux; otherwise "?" (you can extend to JMX later).
 * <p>
 * NOTE: CPU% here is process CPU usage (not system CPU). On some platforms/JDKs it may return empty -> "N/A".
 */
public final class StatsController {

    private final Label statStatusLabel;
    private final Label statUptimeLabel;
    private final Label statMemoryLabel;
    private final Label statCpuLabel;
    private final Label statThreadsLabel;

    private final ServerService serverService;

    private volatile long serverStartTime;
    private volatile int serverMaxMemoryGB;
    private Thread statsUpdateThread;
    private volatile boolean statsRunning;

    private volatile long lastSampleWallNanos = 0L;
    private volatile long lastSampleCpuNanos = 0L;

    private static final long HEAVY_PROBE_INTERVAL_NANOS = 1_000_000_000L;

    private volatile long lastHeavyProbeNanos = 0L;
    private volatile long lastMemoryUsedMB = -1;
    private volatile long lastThreads = -1;

    private final boolean isWindows;

    public StatsController(Label statStatusLabel,
                           Label statUptimeLabel,
                           Label statMemoryLabel,
                           Label statCpuLabel,
                           Label statThreadsLabel,
                           ServerService serverService) {
        this.statStatusLabel = statStatusLabel;
        this.statUptimeLabel = statUptimeLabel;
        this.statMemoryLabel = statMemoryLabel;
        this.statCpuLabel = statCpuLabel;
        this.statThreadsLabel = statThreadsLabel;
        this.serverService = serverService;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        this.isWindows = os.contains("win");
    }

    public void start() {
        if (statsUpdateThread != null && statsUpdateThread.isAlive()) return;

        statsUpdateThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500);
                    if (statsRunning && serverService != null) {
                        updateServerStats();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    System.err.println("Stats loop error: " + t);
                }
            }
        }, "StatsController-Update");
        statsUpdateThread.setDaemon(true);
        statsUpdateThread.start();
    }

    public void onServerStart(long startTime, int maxMemoryGB) {
        this.serverStartTime = startTime;
        this.serverMaxMemoryGB = maxMemoryGB;
        this.statsRunning = true;

        lastSampleWallNanos = 0L;
        lastSampleCpuNanos = 0L;
        lastHeavyProbeNanos = 0L;
        lastMemoryUsedMB = -1;
        lastThreads = -1;
    }

    public void onServerStop() {
        statsRunning = false;
        serverMaxMemoryGB = 0;

        Platform.runLater(() -> {
            statStatusLabel.setText("Stopped");
            statStatusLabel.setStyle("-fx-text-fill: #ef4444;");
            statUptimeLabel.setText("--:--:--");
            statMemoryLabel.setText("0 MB / 0 MB");
            statCpuLabel.setText("0.0%");
            statThreadsLabel.setText("0");
        });
    }

    private void updateServerStats() {
        if (!serverService.isServerRunning()) {
            renderStoppedAndHalt();
            return;
        }

        Process process = serverService.getServerProcess();
        if (process == null || !process.isAlive()) {
            renderStoppedAndHalt();
            return;
        }

        final long pid = process.pid();
        final int maxMemoryGB = this.serverMaxMemoryGB;

        final String uptimeText = formatUptime(System.currentTimeMillis() - serverStartTime);
        final long maxMemoryMB = maxMemoryGB * 1024L;

        final String cpuText = computeCpuPercentText(process.toHandle());
        final long nowNanos = System.nanoTime();

        if (nowNanos - lastHeavyProbeNanos >= HEAVY_PROBE_INTERVAL_NANOS) {
            lastHeavyProbeNanos = nowNanos;

            long mem = readProcessMemoryMB(pid);
            if (mem >= 0) lastMemoryUsedMB = mem;

            long thr = readProcessThreadCount(pid);
            if (thr >= 0) lastThreads = thr;
        }

        final long memoryUsedMB = lastMemoryUsedMB;
        final long threads = lastThreads;

        final String memoryText;
        if (maxMemoryMB <= 0) {
            memoryText = (memoryUsedMB >= 0)
                    ? String.format("%d MB / ?", memoryUsedMB)
                    : "? MB / ?";
        } else {
            memoryText = (memoryUsedMB >= 0)
                    ? String.format("%d MB / %d MB", memoryUsedMB, maxMemoryMB)
                    : String.format("? MB / %d MB", maxMemoryMB);
        }

        final String threadsText = (threads >= 0) ? Long.toString(threads) : "?";

        Platform.runLater(() -> {
            statStatusLabel.setText("Running");
            statStatusLabel.setStyle("-fx-text-fill: #22c55e;");

            statUptimeLabel.setText(uptimeText);
            statMemoryLabel.setText(memoryText);
            statCpuLabel.setText(cpuText);
            statThreadsLabel.setText(threadsText);
        });
    }

    private void renderStoppedAndHalt() {
        statsRunning = false;

        Platform.runLater(() -> {
            statStatusLabel.setText("Stopped");
            statStatusLabel.setStyle("-fx-text-fill: #ef4444;");
            statUptimeLabel.setText("--:--:--");
            statMemoryLabel.setText("0 MB / 0 MB");
            statCpuLabel.setText("0.0%");
            statThreadsLabel.setText("0");
        });
    }

    private static String formatUptime(long millis) {
        if (millis < 0) millis = 0;
        Duration d = Duration.ofMillis(millis);
        long hours = d.toHours();
        long minutes = d.minusHours(hours).toMinutes();
        long seconds = d.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * CPU% based on ProcessHandle.totalCpuDuration delta over wall-clock delta.
     * This avoids OS-specific commands and is usually cheap.
     */
    private String computeCpuPercentText(ProcessHandle handle) {
        try {
            var cpuOpt = handle.info().totalCpuDuration();
            if (cpuOpt.isEmpty()) return "N/A";

            long cpuNanos = cpuOpt.get().toNanos();
            long wallNanos = System.nanoTime();

            if (lastSampleWallNanos == 0L) {
                lastSampleWallNanos = wallNanos;
                lastSampleCpuNanos = cpuNanos;
                return "0.0%";
            }

            long dWall = wallNanos - lastSampleWallNanos;
            long dCpu = cpuNanos - lastSampleCpuNanos;

            lastSampleWallNanos = wallNanos;
            lastSampleCpuNanos = cpuNanos;

            if (dWall <= 0L || dCpu < 0L) return "N/A";

            double pct = (double) dCpu / (double) dWall * 100.0;

            if (pct < 0) pct = 0;
            if (pct > 999.9) pct = 999.9;

            return String.format(Locale.US, "%.1f%%", pct);
        } catch (Throwable t) {
            return "N/A";
        }
    }

    /**
     * Returns RSS/WorkingSet in MB, or -1 if unavailable.
     * Windows: PowerShell (Get-Process).WorkingSet64
     * Unix: ps rss (KB)
     */
    private long readProcessMemoryMB(long pid) {
        try {
            if (isWindows) {

                String out = runCommandCaptureStdout(1500,
                        "powershell", "-NoProfile", "-Command",
                        "(Get-Process -Id " + pid + ").WorkingSet64");

                if (out == null) return -1;
                out = out.trim();
                if (out.isEmpty()) return -1;

                Long bytes = firstLongInText(out);
                if (bytes == null || bytes <= 0) return -1;
                return bytes / (1024 * 1024);
            } else {
                String out = runCommandCaptureStdout(1200,
                        "ps", "-p", String.valueOf(pid), "-o", "rss=", "--no-headers");

                if (out == null) return -1;
                out = out.trim();
                if (out.isEmpty()) return -1;

                Long kb = firstLongInText(out);
                if (kb == null || kb <= 0) return -1;
                return kb / 1024;
            }
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Returns process thread count if available, otherwise -1.
     * - Linux: /proc/<pid>/status -> Threads:
     * - Other OS: not implemented here (you can extend via native APIs or JMX inside the server)
     */
    private long readProcessThreadCount(long pid) {
        try {
            if (!isWindows && isProcFsLikely()) {
                java.nio.file.Path p = java.nio.file.Paths.get("/proc", String.valueOf(pid), "status");
                if (!java.nio.file.Files.isReadable(p)) return -1;
                for (String line : java.nio.file.Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (line.startsWith("Threads:")) {
                        Long v = firstLongInText(line);
                        return (v != null && v >= 0) ? v : -1;
                    }
                }
            }
            return -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean isProcFsLikely() {
        return java.nio.file.Files.isDirectory(java.nio.file.Paths.get("/proc"));
    }

    private static String runCommandCaptureStdout(long timeoutMillis, String... cmd) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
            byte[] bytes = p.getInputStream().readAllBytes();

            boolean done = p.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return null;
            }

            int exit = p.exitValue();
            String out = new String(bytes, StandardCharsets.UTF_8);
            if (exit != 0) {
                System.err.println("Command failed (" + exit + "): " + String.join(" ", cmd) + " | out=" + out.trim());
                return null;
            }
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) {
                try {
                    p.getInputStream().close();
                } catch (Exception ignored) {
                }
                try {
                    p.getOutputStream().close();
                } catch (Exception ignored) {
                }
                try {
                    p.getErrorStream().close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Long firstLongInText(String text) {
        if (text == null) return null;
        long sign = 1;
        long value = 0;
        boolean inNumber = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (!inNumber) {
                if (c == '-') {
                    if (i + 1 < text.length() && Character.isDigit(text.charAt(i + 1))) {
                        sign = -1;
                        inNumber = true;
                        value = 0;
                    }
                } else if (Character.isDigit(c)) {
                    sign = 1;
                    inNumber = true;
                    value = c - '0';
                }
            } else {
                if (Character.isDigit(c)) {
                    value = value * 10 + (c - '0');
                    if (value < 0) return null;
                } else {
                    return sign * value;
                }
            }
        }
        return inNumber ? (sign * value) : null;
    }
}
