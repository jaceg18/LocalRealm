package com.jaceg18.localrealm.core.Controllers;


import com.jaceg18.localrealm.core.build.Server;
import com.jaceg18.localrealm.core.build.Util;
import com.jaceg18.localrealm.core.manager.ServerManager;
import com.jaceg18.localrealm.core.service.ServerService;
import com.jaceg18.localrealm.core.ui.SpinnerSetup;
import com.jaceg18.localrealm.core.ui.UiUtil;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class BuildTabController {

    private final ChoiceBox<String> buildBox;
    private final Spinner<Integer> minSpinner;
    private final Spinner<Integer> maxSpinner;
    private final CheckBox autoEulaCB;
    private final ProgressBar progressBar;
    private final Label statusLabel;

    private final ServerService serverService;
    private final Consumer<String> appendToConsole;
    private final Consumer<String> updateStatus;
    private final Runnable refreshServers;

    public BuildTabController(ChoiceBox<String> buildBox,
                              Spinner<Integer> minSpinner,
                              Spinner<Integer> maxSpinner,
                              CheckBox autoEulaCB,
                              ProgressBar progressBar,
                              Label statusLabel,
                              ServerService serverService,
                              Consumer<String> appendToConsole,
                              Consumer<String> updateStatus,
                              Runnable refreshServers) {
        this.buildBox = buildBox;
        this.minSpinner = minSpinner;
        this.maxSpinner = maxSpinner;
        this.autoEulaCB = autoEulaCB;
        this.progressBar = progressBar;
        this.statusLabel = statusLabel;
        this.serverService = serverService;
        this.appendToConsole = appendToConsole;
        this.updateStatus = updateStatus;
        this.refreshServers = refreshServers;
    }

    public void init() {
        setupSpinners();
        refreshBuildOptions();

        progressBar.setProgress(0);
        updateStatus.accept("Ready");
    }

    public void buildServer() {
        String selectedBuild = buildBox.getValue();
        if (selectedBuild == null || selectedBuild.isEmpty() || !Util.getBuildOptions().containsKey(selectedBuild)) {
            UiUtil.showError("No Build Selected", "Please select a build option before building.");
            return;
        }

        Stage stage = (Stage) buildBox.getScene().getWindow();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Server Folder");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File folder = chooser.showDialog(stage);
        if (folder == null) return;

        startBuildTask(folder);
    }

    private void startBuildTask(File folder) {
        String selectedBuild = buildBox.getValue();
        String url = Util.getBuildOptions().get(selectedBuild);

        Path folderPath = folder.toPath();
        String fileName = "server.jar";

        int minMem = minSpinner.getValue();
        int maxMem = maxSpinner.getValue();

        progressBar.setProgress(-1);

        Task<Void> task = serverService.buildServerTask(
                selectedBuild,
                url,
                folderPath,
                fileName,
                minMem,
                maxMem,
                autoEulaCB.isSelected()
        );

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            updateStatus.accept("Build Complete");

            try {
                ServerManager.saveServer(new Server(folder.getName(), folderPath));
                appendToConsole.accept("[INFO] Server automatically saved to server list!");
                refreshServers.run();
            } catch (Exception ex) {
                appendToConsole.accept("[WARN] Could not auto-save server: " + ex.getMessage());
            }

            UiUtil.showInfo(
                    "Build Complete",
                    "Server files have been created successfully!"
                            + (autoEulaCB.isSelected()
                            ? " Eula has automatically been approved."
                            : " Please agree to Eula agreement in server files before starting.")
            );
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            updateStatus.accept("Build Failed");

            Throwable ex = task.getException();
            String msg = (ex != null) ? ex.getMessage() : "An unknown error occurred";
            appendToConsole.accept("[ERROR] Build failed: " + msg);
            if (ex != null) ex.printStackTrace();

            UiUtil.showError("Build Failed", "Failed to build server: " + msg);
        });

        progressBar.progressProperty().bind(task.progressProperty());
        new Thread(task, "build-task").start();
    }

    void refreshBuildOptions() {
        buildBox.getItems().clear();
        Util.getBuildOptions().keySet().forEach(buildBox.getItems()::add);
        if (!buildBox.getItems().isEmpty()) {
            buildBox.getSelectionModel().selectFirst();
        }
    }

    private void setupSpinners() {
        // keep your existing setupSpinners() content verbatim
        // (I’m not duplicating it here to avoid a 2000-line response,
        // but paste your method body exactly as-is)
        SpinnerSetup.applyMinMaxMemory(minSpinner, maxSpinner);
    }
}
