package com.jaceg18.localrealm;

import com.jaceg18.localrealm.annotation.Provisional;
import com.jaceg18.localrealm.core.Build.Util;
import com.jaceg18.localrealm.core.Server;
import com.jaceg18.localrealm.core.ServerManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

public class Controller {
    @FXML protected Spinner<Integer> minAlocSpinner;
    @FXML protected Spinner<Integer> maxAlocSpinner;
    @FXML protected ChoiceBox<String> buildBox;
    @FXML protected CheckBox autoEulaCB, noGuiCB;
    @FXML protected ProgressBar progressBar;
    @FXML protected TextArea consoleArea;
    @FXML protected Label statusLabel;
    @FXML protected ScrollPane consoleScrollPane;
    @FXML protected Button clearConsoleBtn;

    @FXML protected TabPane mainTabPane;
    @FXML protected ListView<Server> serverListView;
    @FXML protected Button addServerBtn, removeServerBtn, runSelectedServerBtn, refreshServersBtn;
    @FXML protected TextArea serverConsoleArea;
    @FXML protected ScrollPane serverConsoleScrollPane;
    @FXML protected Button clearServerConsoleBtn;
    @FXML protected Label serverConsoleLabel;

    private Process runningServerProcess;
    private PrintWriter serverInputWriter;
    private ObservableList<Server> serverList;

    @FXML
    public void initialize(){
        var minFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 128, 2, 1);
        var maxFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 128, 4, 1);
        // TODO Extract limits from the settings menu. Let advanced users override them via a config file, so protections remain for novices without capping high-volume servers.
        // TODO This will and should be purely implemented into server management section, as it's not needed for initial server boot.


        UnaryOperator<TextFormatter.Change> uO = c -> c.getControlNewText().matches("\\d*") ? c : null;
        var minFormat = new TextFormatter<>(new IntegerStringConverter(), minFactory.getValue(), uO);
        var maxFormat = new TextFormatter<>(new IntegerStringConverter(), maxFactory.getValue(), uO);

        minFactory.valueProperty().bindBidirectional(minFormat.valueProperty());
        maxFactory.valueProperty().bindBidirectional(maxFormat.valueProperty());

        minAlocSpinner.setValueFactory(minFactory);
        maxAlocSpinner.setValueFactory(maxFactory);
        minAlocSpinner.setEditable(true);
        maxAlocSpinner.setEditable(true);
        minAlocSpinner.getEditor().setTextFormatter(minFormat);
        maxAlocSpinner.getEditor().setTextFormatter(maxFormat);

        minAlocSpinner.valueProperty().addListener((o, oV, nV) -> {if (nV != null && maxAlocSpinner.getValue() != null && nV > maxAlocSpinner.getValue()) Platform.runLater(() -> maxAlocSpinner.getValueFactory().setValue(nV));});
        maxAlocSpinner.valueProperty().addListener((o, oV, nV) -> {if (nV != null && minAlocSpinner.getValue() != null && nV > maxAlocSpinner.getValue()) Platform.runLater(() -> minAlocSpinner.getValueFactory().setValue(nV));});

        Util.BUILD_OPTIONS.keySet().forEach(k -> buildBox.getItems().add(k));
        buildBox.getSelectionModel().selectFirst();

        progressBar.setProgress(0);
        consoleArea.setEditable(true);
        serverConsoleArea.setEditable(true);

        consoleArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleConsoleInput);
        serverConsoleArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleServerConsoleInput);

        serverList = FXCollections.observableArrayList();
        serverListView.setItems(serverList);
        refreshServers();

        appendToConsole("LocalRealm initialized. Ready to build.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sendCommandToServer("stop");
            appendToConsole("Shutting down any running servers.");
            appendToServerConsole("Shutting down server.");
        }));



    }
    private void handleConsoleInput(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String text = consoleArea.getText();
            String[] lines = text.split("\n", -1);

            if (lines.length > 0) {
                String command = lines[lines.length - 1].trim();
                if (!command.isEmpty() && runningServerProcess != null && runningServerProcess.isAlive()) {
                    sendCommandToServer(command);
                    event.consume();
                }
            }
        }
    }
    private void handleServerConsoleInput(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String text = serverConsoleArea.getText();
            String[] lines = text.split("\n", -1);

            if (lines.length > 0) {
                String command = lines[lines.length - 1].trim();
                if (!command.isEmpty() && runningServerProcess != null && runningServerProcess.isAlive()) {
                    sendCommandToServer(command);
                    event.consume();
                }
            }
        }
    }

    @FXML
    public void clearConsole() {
        consoleArea.clear();
        appendToConsole("Console cleared.");
    }

    @FXML
    public void clearServerConsole() {
        serverConsoleArea.clear();
        appendToServerConsole("Console cleared.");
    }

    @FXML
    public void refreshServers() {
        try {
            List<Server> servers = ServerManager.loadServers();
            serverList.clear();
            serverList.addAll(servers);
        } catch (Exception e) {
            showError("Error Loading Servers", "Failed to load server list: " + e.getMessage());
        }
    }

    @FXML @Provisional(reason = "dumb to only look for server.jar, and not just have the user direct us to the jar file.", expiresBy = "v1.2.0")
    public void addServer() {
        Stage stage = (Stage) addServerBtn.getScene().getWindow();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Server Folder");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File folder = chooser.showDialog(stage);
        if (folder != null) {
            Path serverPath = folder.toPath();
            if (!Files.exists(serverPath.resolve("server.jar"))) {
                showError("Invalid Server", "The selected folder does not contain server.jar");
                return;
            }

            TextInputDialog dialog = new TextInputDialog(folder.getName());
            dialog.setTitle("Add Server");
            dialog.setHeaderText("Enter a name for this server:");
            dialog.setContentText("Server Name:");

            dialog.showAndWait().ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    try {
                        ServerManager.saveServer(new Server(name.trim(), serverPath));
                        refreshServers();
                        showInfo("Server Added", "Server '" + name + "' has been added successfully.");
                    } catch (Exception e) {
                        showError("Error Adding Server", "Failed to add server: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void sendCommandToServer(String command) {
        if (serverInputWriter != null && runningServerProcess != null && runningServerProcess.isAlive()) {
            serverInputWriter.println(command);
            serverInputWriter.flush();
            appendToServerConsole("> " + command);
        }
    }


    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    private void appendToConsole(String text) {
        Platform.runLater(() -> {
            consoleArea.appendText(text + "\n");
            consoleArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void appendToServerConsole(String text) {
        Platform.runLater(() -> {
            serverConsoleArea.appendText(text + "\n");
            serverConsoleArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void updateStatus(String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    @FXML
    public void buildServer(){
        String selectedBuild = buildBox.getValue();
        if (selectedBuild == null || selectedBuild.isEmpty() || !Util.BUILD_OPTIONS.containsKey(selectedBuild)){
            showError("No Build Selected", "Please select a build option before building.");
            return;
        }

        Stage stage = (Stage) buildBox.getScene().getWindow();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Server Folder");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File folder = chooser.showDialog(stage);
        if (folder != null){
            build(folder);
        }
    }

    public void build(File folder) {
        String selectedBuild = buildBox.getValue();
        String url = Util.BUILD_OPTIONS.get(selectedBuild);
        String fileName = "server.jar";
        Path folderPath = folder.toPath();
        int minMem = minAlocSpinner.getValue();
        int maxMem = maxAlocSpinner.getValue();

        progressBar.setProgress(-1);
        appendToConsole("=== Starting Build Process ===");
        appendToConsole("[INFO] Memory: " + minMem + "GB min, " + maxMem + "GB max");
        updateStatus("Building...");

        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Files.createDirectories(folderPath);

                updateMessage("Downloading server jar...");
                updateProgress(0.2, 1.0);
                appendToConsole("[INFO] Downloading server jar from: " + selectedBuild);
                Util.downloadToFolder(url, folderPath, fileName);
                appendToConsole("[INFO] Download complete!");

                updateMessage("Starting server to generate eula.txt...");
                updateProgress(0.5, 1.0);
                appendToConsole("[INFO] Starting server to generate eula.txt...");

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
                            appendToConsole("[WARN] Init process exited with code: " + exitCode);
                        }
                        break;
                    }
                }

                Thread.sleep(1000);

                if (initProcess.isAlive()) {
                    appendToConsole("[INFO] Stopping initialization server...");
                    initProcess.destroy();
                    try {
                        if (!initProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                            initProcess.destroyForcibly();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        initProcess.destroyForcibly();
                    }
                }

                if (autoEulaCB.isSelected()) {
                    updateMessage("Setting up EULA...");
                    updateProgress(0.8, 1.0);
                    appendToConsole("[INFO] Setting up EULA...");

                    waitCount = 0;
                    while (!Files.exists(eulaFile) && waitCount < 10) {
                        Thread.sleep(200);
                        waitCount++;
                    }

                    if (Files.exists(eulaFile)) {
                        Util.autoEula(folderPath);
                        appendToConsole("[INFO] EULA accepted automatically!");
                    } else {
                        appendToConsole("[WARN] eula.txt not found, skipping auto-EULA");
                    }
                }

                updateMessage("Complete!");
                updateProgress(1.0, 1.0);
                appendToConsole("[SUCCESS] Build process complete!");

                // Auto-save the server
                try {
                    String serverName = folder.getName();
                    ServerManager.saveServer(new Server(serverName, folderPath));
                    appendToConsole("[INFO] Server automatically saved to server list!");
                    Platform.runLater(() -> refreshServers());
                } catch (Exception e) {
                    appendToConsole("[WARN] Could not auto-save server: " + e.getMessage());
                }

                return null;
            }
        };

        downloadTask.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            updateStatus("Build Complete");
            showInfo("Build Complete","Server files have been created successfully!" + (autoEulaCB.isSelected() ? " Eula has automatically been approved." : " Please agree to Eula agreement in server files before starting."));
        });

        downloadTask.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            updateStatus("Build Failed");

            Throwable exception = downloadTask.getException();
            String message = exception != null ? exception.getMessage() : "An unknown error occurred";
            appendToConsole("[ERROR] Build failed: " + message);

            if (exception != null) {
                exception.printStackTrace();
            }
            showError("Build Failed", "Failed to build server: " + message);
        });

        progressBar.progressProperty().bind(downloadTask.progressProperty());
        new Thread(downloadTask).start();
    }

    @FXML
    public void stopServer(){
        sendCommandToServer("stop");
    }

    private void captureProcessOutput(Process process, boolean isInit, boolean useServerConsole) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (process.isAlive() || isInit) {
                        if (useServerConsole) {
                            appendToServerConsole(line);
                        } else {
                            appendToConsole(line);
                        }
                    } else {
                        break;
                    }
                }
            } catch (Exception e) {
                if (process.isAlive() || isInit) {
                    String error = "[ERROR] Error reading process output: " + e.getMessage();
                    if (useServerConsole) {
                        appendToServerConsole(error);
                    } else {
                        appendToConsole(error);
                    }
                }
            } finally {
                if (!isInit && process != null) {
                    String message = "[INFO] Server process ended.";
                    if (useServerConsole) {
                        appendToServerConsole(message);
                    } else {
                        appendToConsole(message);
                    }
                    updateStatus("Server Stopped");
                }
            }
        }).start();
    }
    private void runServer(Path serverPath, int minMem, int maxMem) {
        try {
            if (runningServerProcess != null && runningServerProcess.isAlive()) {
                runningServerProcess.destroyForcibly();
            }

            File serverFolder = serverPath.toFile();
            String fileName = "server.jar";

            if (true) {
                appendToServerConsole("[INFO] Starting server with " + minMem + "GB min, " + maxMem + "GB max...");
            } else {
                appendToConsole("[INFO] Starting server with " + minMem + "GB min, " + maxMem + "GB max...");
            }

            runningServerProcess = new ProcessBuilder("java",
                    "-Xms" + minMem + "G",
                    "-Xmx" + maxMem + "G",
                    "-jar", fileName,
                    "nogui")
                    .directory(serverFolder)
                    .redirectErrorStream(true)
                    .start();

            serverInputWriter = new PrintWriter(
                    new OutputStreamWriter(runningServerProcess.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            captureProcessOutput(runningServerProcess, false, true);

            updateStatus("Server Running: " + serverPath.getFileName());
            appendToServerConsole("[INFO] Server started. Type commands and press Enter.");
        } catch (Exception ex) {
            String error = "Failed to start server: " + ex.getMessage();
            appendToServerConsole("[ERROR] " + error);
            showError("Failed to Start Server", error);
        }
    }


    @FXML
    public void runSelectedServer(){
        Server selected = serverListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No Selection", "Please select a server to run.");
            return;
        }

        Path serverPath = selected.path();
        if (!Files.exists(serverPath.resolve("server.jar"))){
            showError("Server not found", "server.jar not found in: " + serverPath);
        }

        serverConsoleLabel.setText("Server Console: " + selected);
        appendToServerConsole("=== Starting Server: " + selected + " ===");
        appendToServerConsole("[INFO] Server path: " + serverPath);

        runServer(serverPath, minAlocSpinner.getValue(), maxAlocSpinner.getValue());
    }

    @FXML
    public void removeServer() {
        Server selected = serverListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No Selection", "Please select a server to remove.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Server");
        confirm.setHeaderText("Remove Server");
        confirm.setContentText("Are you sure you want to remove '" + selected + "'?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    ServerManager.removeServer(selected.name());
                    refreshServers();
                    showInfo("Server Removed", "Server '" + selected.name() + "' has been removed.");
                } catch (Exception e) {
                    showError("Error Removing Server", "Failed to remove server: " + e.getMessage());
                }
            }
        });
    }

}