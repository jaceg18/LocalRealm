package com.jaceg18.localrealm;

import com.jaceg18.localrealm.core.manager.BuildOptionsManager;
import com.jaceg18.localrealm.core.manager.ServerManager;
import com.jaceg18.localrealm.core.service.ServerService;
import com.jaceg18.localrealm.core.build.Server;
import com.jaceg18.localrealm.core.build.Util;
import com.jaceg18.localrealm.core.ui.UiUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.input.*;
import javafx.scene.Cursor;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import com.jaceg18.localrealm.core.service.NetworkService;
import com.jaceg18.localrealm.core.service.NetworkService.NetworkConfig;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO Remove the awkward double console thing, makes no sense to do the same actions twice for two different instances.
// TODO The user should direct us to the server jar when adding a server, not to the folder.
// TODO This class does way to much, fix that. While considering what's most efficient for future growth.

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
    @FXML protected TreeView<Path> fileView;
    @FXML private TableView<Map.Entry<String, String>> buildTable;
    @FXML private TableColumn<Map.Entry<String, String>, String> keyCol;
    @FXML private TableColumn<Map.Entry<String, String>, String> valCol;
    @FXML private TextField consoleField;
    @FXML private TableView<Map.Entry<String, String>> buildOptionsTable;
    @FXML private TableColumn<Map.Entry<String, String>, String> buildNameCol;
    @FXML private TableColumn<Map.Entry<String, String>, String> buildUrlCol;
    
    // External Join UI components
    @FXML private Button enableExternalJoinBtn;
    @FXML private Button disableExternalJoinBtn;
    @FXML private ProgressBar networkProgressBar;
    @FXML private VBox networkResultBox;
    @FXML private Label networkStatusLabel;
    @FXML private Label networkJoinAddressLabel;
    @FXML private TextField joinAddressField;
    @FXML private Button copyJoinAddressBtn;
    @FXML private VBox networkErrorBox;
    @FXML private Label networkErrorLabel;
    @FXML private VBox vpnFallbackBox;
    @FXML private Label vpnReasonLabel;
    @FXML private Button showVpnHelpBtn;
    @FXML private VBox vpnHelpBox;
    
    private Path currentFile;

    private ObservableList<Server> serverList;
    private ServerService serverService;
    private NetworkConfig currentNetworkConfig;

    @FXML
    public void initialize() {
        setupSpinners();
        setupBuildOptions();

        progressBar.setProgress(0);
        consoleArea.setEditable(true);
        serverConsoleArea.setEditable(true);

        consoleArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleConsoleInput);
        serverConsoleArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleServerConsoleInput);


        serverService = new ServerService(
                this::appendToConsole,
                this::appendToServerConsole,
                this::updateStatus
        );


        serverList = FXCollections.observableArrayList();
        serverListView.setItems(serverList);
        refreshServers();

        appendToConsole("LocalRealm initialized. Ready to build.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serverService.shutdownHookStop();
            cleanupNetworkMapping(); // Cleanup UPnP mapping on shutdown
        }));

        setupFileTree();
        setupBuildOptionsTable();
        setupExternalJoin();
    }
    
    private void setupExternalJoin() {
        // Hide all result/error panels initially
        networkResultBox.setVisible(false);
        networkErrorBox.setVisible(false);
        vpnFallbackBox.setVisible(false);
        vpnHelpBox.setVisible(false);
        networkProgressBar.setVisible(false);
    }


    private TreeItem<Path> createNode(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);

        if (Files.isDirectory(path)) {
            try {
                boolean hasChildren = Files.list(path).findAny().isPresent();
                if (hasChildren) {
                    TreeItem<Path> placeholder = new TreeItem<>(null);
                    item.getChildren().add(placeholder);
                }
            } catch (IOException ignored) {
                // File isn't a directory, dirty but nothing to do here.
            }
            
            item.expandedProperty().addListener((obs, was, isNow) -> {
                if (isNow) {
                    boolean needsLoad = item.getChildren().isEmpty() || (item.getChildren().size() == 1 && item.getChildren().getFirst().getValue() == null);
                    if (needsLoad) {
                        item.getChildren().clear();
                        try (var stream = Files.list(path)) {
                            stream.sorted()
                                    .map(this::createNode)
                                    .forEach(item.getChildren()::add);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });
        }

        return item;
    }

    private void setupFileTree() {
        fileView.setCellFactory(tv -> {
            TreeCell<Path> cell = new TreeCell<>() {
                @Override
                protected void updateItem(Path item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item.getFileName().toString());
                    }
                }
            };
            cell.setPrefHeight(32);
            cell.setMinHeight(32);
            return cell;
        });

        fileView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<Path> selectedItem = fileView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && !selectedItem.isLeaf()) {
                    selectedItem.setExpanded(!selectedItem.isExpanded());
                }
            }
        });

        fileView.setShowRoot(false);

        serverListView.getSelectionModel().selectedItemProperty().addListener((o, oI, nI) -> {
            if (nI != null) {
                Server server = serverListView.getSelectionModel().getSelectedItem();
                TreeItem<Path> root = createNode(server.path());
                root.setExpanded(true);
                fileView.setRoot(root);
                buildTable.getItems().clear();
                currentFile = null;
            }
        });

        keyCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getKey()));
        
        valCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getValue()));
        
        valCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valCol.setOnEditCommit(event -> {
            Map.Entry<String, String> entry = event.getRowValue();
            entry.setValue(event.getNewValue());
        });

        fileView.getSelectionModel().selectedItemProperty().addListener((o, oI, nI) -> {
            if (nI != null) {
                TreeItem<Path> selectedItem = fileView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    Path selectedPath = selectedItem.getValue();
                    
                    if (selectedPath != null && Files.isRegularFile(selectedPath)) {
                        currentFile = selectedPath;
                        try {
                            Map<String, String> fileContents = Util.getElementsFromFile(selectedPath);
                            if (fileContents != null && !fileContents.isEmpty()) {
                                buildTable.getItems().setAll(fileContents.entrySet());
                            } else {
                                buildTable.getItems().clear();
                            }
                        } catch (IOException e) {
                            buildTable.getItems().clear();
                            currentFile = null;
                            UiUtil.showError("Error Reading File", 
                                "Failed to read file: " + selectedPath.getFileName() + "\n" + e.getMessage());
                        }
                    } else {
                        buildTable.getItems().clear();
                        currentFile = null;
                    }
                }
            } else {
                buildTable.getItems().clear();
                currentFile = null;
            }
        });

        setupDragAndDrop();
        buildTable.setEditable(true);
    }

    private void setupDragAndDrop() {
        fileView.setOnDragOver(event -> {
            if (event.getGestureSource() != fileView && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        fileView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (db.hasFiles()) {
                TreeItem<Path> selectedItem = fileView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    Path targetPath = selectedItem.getValue();
                    
                    if (targetPath != null && Files.isDirectory(targetPath)) {
                        for (File file : db.getFiles()) {
                            try {
                                Path sourcePath = file.toPath();
                                Path destination = targetPath.resolve(sourcePath.getFileName());
                                Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
                                success = true;
                                
                                TreeItem<Path> parent = selectedItem;
                                if (!parent.isExpanded()) {
                                    parent.setExpanded(true);
                                }
                                
                                TreeItem<Path> newItem = createNode(destination);
                                parent.getChildren().add(newItem);
                                parent.getChildren().sort((a, b) -> {
                                    if (a.getValue() == null || b.getValue() == null) return 0;
                                    return a.getValue().getFileName().toString()
                                        .compareToIgnoreCase(b.getValue().getFileName().toString());
                                });
                            } catch (IOException ex) {
                                UiUtil.showError("Error Copying File", 
                                    "Failed to copy file: " + file.getName() + "\n" + ex.getMessage());
                            }
                        }
                    } else {
                        UiUtil.showError("Invalid Target", "Please select a directory to drop files into.");
                    }
                } else {
                    UiUtil.showError("No Target", "Please select a directory in the file tree to drop files into.");
                }
            }
            
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    private void saveFile() {
        if (currentFile == null) {
            UiUtil.showError("No File Selected", "Please select a file to save.");
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            for (Map.Entry<String, String> entry : buildTable.getItems()) {
                data.put(entry.getKey(), entry.getValue());
            }
            
            Util.saveElementsToFile(currentFile, data);
            UiUtil.showInfo("File Saved", "File saved successfully: " + currentFile.getFileName());
        } catch (IOException e) {
            UiUtil.showError("Error Saving File", 
                "Failed to save file: " + currentFile.getFileName() + "\n" + e.getMessage());
        }
    }

    @FXML
    public void sendConsole(){
        if (consoleField.getText().isEmpty()) return;
        serverService.sendCommand(consoleField.getText());
        consoleField.setText("");
    }

    @FXML
    public void openFile(){
        if (currentFile == null || currentFile.toFile().isDirectory()) {
            UiUtil.showError("No File Selected", "Please select a file to open.");
            return;
        }
            if (Desktop.isDesktopSupported()) {
                try {

                    Desktop.getDesktop().open(currentFile.toFile());
                } catch (IOException e) {
                    UiUtil.showError("Error Opening File",
                            "Failed to open file: " + currentFile.getFileName() + "\n" + e.getMessage());
                }
            }
    }

    private void setupBuildOptionsTable() {
        buildNameCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getKey()));
        
        buildUrlCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getValue()));
        
        buildNameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        buildNameCol.setOnEditCommit(event -> {
            Map.Entry<String, String> entry = event.getRowValue();
            String oldName = entry.getKey();
            String newName = event.getNewValue();
            String url = entry.getValue();
            
            if (!oldName.equals(newName) && !newName.isEmpty()) {
                try {
                    BuildOptionsManager.updateBuildOption(oldName, newName, url);
                    Util.reloadBuildOptions();
                    refreshBuildOptions();
                    refreshBuildOptionsTable();
                } catch (IOException e) {
                    UiUtil.showError("Error Updating Build Option", 
                        "Failed to update build option: " + e.getMessage());
                    refreshBuildOptionsTable();
                }
            }
        });
        
        buildUrlCol.setCellFactory(TextFieldTableCell.forTableColumn());
        buildUrlCol.setOnEditCommit(event -> {
            Map.Entry<String, String> entry = event.getRowValue();
            String name = entry.getKey();
            String newUrl = event.getNewValue();
            
            if (!newUrl.isEmpty()) {
                try {
                    BuildOptionsManager.updateBuildOption(name, name, newUrl);
                    Util.reloadBuildOptions();
                    refreshBuildOptions();
                    refreshBuildOptionsTable();
                } catch (IOException e) {
                    UiUtil.showError("Error Updating Build Option", 
                        "Failed to update build option: " + e.getMessage());
                    refreshBuildOptionsTable();
                }
            }
        });
        
        buildOptionsTable.setEditable(true);
        refreshBuildOptionsTable();
    }

    private void refreshBuildOptionsTable() {
        buildOptionsTable.getItems().setAll(Util.getBuildOptions().entrySet());
    }

    @FXML
    private void addBuildOption() {
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Add Build Option");
        nameDialog.setHeaderText("Enter Build Name");
        nameDialog.setContentText("Name:");
        
        nameDialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) {
                UiUtil.showError("Invalid Name", "Build name cannot be empty.");
                return;
            }
            
            if (Util.getBuildOptions().containsKey(name.trim())) {
                UiUtil.showError("Duplicate Name", "A build option with this name already exists.");
                return;
            }
            
            TextInputDialog urlDialog = new TextInputDialog();
            urlDialog.setTitle("Add Build Option");
            urlDialog.setHeaderText("Enter Download URL");
            urlDialog.setContentText("URL:");
            
            urlDialog.showAndWait().ifPresent(url -> {
                if (url.trim().isEmpty()) {
                    UiUtil.showError("Invalid URL", "URL cannot be empty.");
                    return;
                }
                
                try {
                    BuildOptionsManager.addBuildOption(name.trim(), url.trim());
                    Util.reloadBuildOptions();
                    refreshBuildOptions();
                    refreshBuildOptionsTable();
                    UiUtil.showInfo("Build Option Added", "Build option '" + name.trim() + "' has been added.");
                } catch (IOException e) {
                    UiUtil.showError("Error Adding Build Option", 
                        "Failed to add build option: " + e.getMessage());
                }
            });
        });
    }

    @FXML
    private void removeBuildOption() {
        Map.Entry<String, String> selected = buildOptionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiUtil.showError("No Selection", "Please select a build option to remove.");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Build Option");
        confirm.setHeaderText("Remove Build Option");
        confirm.setContentText("Are you sure you want to remove '" + selected.getKey() + "'?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            
            try {
                BuildOptionsManager.removeBuildOption(selected.getKey());
                Util.reloadBuildOptions();
                refreshBuildOptions();
                refreshBuildOptionsTable();
                UiUtil.showInfo("Build Option Removed", "Build option '" + selected.getKey() + "' has been removed.");
            } catch (IOException e) {
                UiUtil.showError("Error Removing Build Option", 
                    "Failed to remove build option: " + e.getMessage());
            }
        });
    }


    private void setupSpinners() {
        var minFactory = new IntegerSpinnerValueFactory(1, 128, 2, 1);
        var maxFactory = new IntegerSpinnerValueFactory(1, 128, 4, 1);

        java.util.function.UnaryOperator<TextFormatter.Change> digitsOnly = c ->
                c.getControlNewText().matches("\\d*") ? c : null;

        var minFormat = new TextFormatter<>(new IntegerStringConverter(), minFactory.getValue(), digitsOnly);
        var maxFormat = new TextFormatter<>(new IntegerStringConverter(), maxFactory.getValue(), digitsOnly);

        minFactory.valueProperty().bindBidirectional(minFormat.valueProperty());
        maxFactory.valueProperty().bindBidirectional(maxFormat.valueProperty());

        minAlocSpinner.setValueFactory(minFactory);
        maxAlocSpinner.setValueFactory(maxFactory);

        minAlocSpinner.setEditable(true);
        maxAlocSpinner.setEditable(true);

        minAlocSpinner.getEditor().setTextFormatter(minFormat);
        maxAlocSpinner.getEditor().setTextFormatter(maxFormat);


        minAlocSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null && maxAlocSpinner.getValue() != null && nv > maxAlocSpinner.getValue()) {
                Platform.runLater(() -> maxAlocSpinner.getValueFactory().setValue(nv));
            }
        });


        maxAlocSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null && minAlocSpinner.getValue() != null && nv < minAlocSpinner.getValue()) {
                Platform.runLater(() -> minAlocSpinner.getValueFactory().setValue(nv));
            }
        });
    }

    private void setupBuildOptions() {
        refreshBuildOptions();
    }

    private void refreshBuildOptions() {
        buildBox.getItems().clear();
        Util.getBuildOptions().keySet().forEach(buildBox.getItems()::add);
        if (!buildBox.getItems().isEmpty()) {
            buildBox.getSelectionModel().selectFirst();
        }
    }

    private void handleConsoleInput(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String command = lastLine(consoleArea.getText());
            if (!command.isEmpty() && serverService.isServerRunning()) {
                serverService.sendCommand(command);
                event.consume();
            }
        }
    }

    private void handleServerConsoleInput(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String command = lastLine(serverConsoleArea.getText());
            if (!command.isEmpty() && serverService.isServerRunning()) {
                serverService.sendCommand(command);
                event.consume();
            }
        }
    }

    private static String lastLine(String text) {
        String[] lines = text.split("\n", -1);
        return (lines.length > 0) ? lines[lines.length - 1].trim() : "";
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
            UiUtil.showError("Error Loading Servers", "Failed to load server list: " + e.getMessage());
        }
    }

    // TODO This needs to change, we should have the user direct us to the JAR not the folder. We can't expect 'server.jar' everytime.
    @FXML
    public void addServer() {
        Stage stage = (Stage) addServerBtn.getScene().getWindow();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Server Folder");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File folder = chooser.showDialog(stage);
        if (folder == null) return;

        Path serverPath = folder.toPath();
        if (!Files.exists(serverPath.resolve("server.jar"))) {
            UiUtil.showError("Invalid Server", "The selected folder does not contain server.jar");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(folder.getName());
        dialog.setTitle("Add Server");
        dialog.setHeaderText("Enter a name for this server:");
        dialog.setContentText("Server Name:");

        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;

            try {
                ServerManager.saveServer(new Server(name.trim(), serverPath));
                refreshServers();
                UiUtil.showInfo("Server Added", "Server '" + name.trim() + "' has been added successfully.");
            } catch (Exception e) {
                UiUtil.showError("Error Adding Server", "Failed to add server: " + e.getMessage());
            }
        });
    }

    @FXML
    public void removeServer() {
        Server selected = serverListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiUtil.showError("No Selection", "Please select a server to remove.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Server");
        confirm.setHeaderText("Remove Server");
        confirm.setContentText("Are you sure you want to remove '" + selected + "'?");

        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            try {
                ServerManager.removeServer(selected.name());
                refreshServers();
                UiUtil.showInfo("Server Removed", "Server '" + selected.name() + "' has been removed.");
            } catch (Exception e) {
                UiUtil.showError("Error Removing Server", "Failed to remove server: " + e.getMessage());
            }
        });
    }

    @FXML
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

        int minMem = minAlocSpinner.getValue();
        int maxMem = maxAlocSpinner.getValue();

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
            updateStatus("Build Complete");

            try {
                ServerManager.saveServer(new Server(folder.getName(), folderPath));
                appendToConsole("[INFO] Server automatically saved to server list!");
                refreshServers();
            } catch (Exception ex) {
                appendToConsole("[WARN] Could not auto-save server: " + ex.getMessage());
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
            updateStatus("Build Failed");

            Throwable ex = task.getException();
            String msg = (ex != null) ? ex.getMessage() : "An unknown error occurred";
            appendToConsole("[ERROR] Build failed: " + msg);
            if (ex != null) ex.printStackTrace();

            UiUtil.showError("Build Failed", "Failed to build server: " + msg);
        });

        progressBar.progressProperty().bind(task.progressProperty());
        new Thread(task, "build-task").start();
    }

    @FXML
    public void runSelectedServer() {
        Server selected = serverListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiUtil.showError("No Selection", "Please select a server to run.");
            return;
        }

        Path serverPath = selected.path();
        if (!Files.exists(serverPath.resolve("server.jar"))) {
            UiUtil.showError("Server not found", "server.jar not found in: " + serverPath);
            return;
        }

        serverConsoleLabel.setText("Server Console: " + selected);
        appendToServerConsole("=== Starting Server: " + selected + " ===");
        appendToServerConsole("[INFO] Server path: " + serverPath);

        try {
            serverService.runServer(
                    serverPath,
                    minAlocSpinner.getValue(),
                    maxAlocSpinner.getValue(),
                    noGuiCB.isSelected()
            );
        } catch (RuntimeException ex) {
            UiUtil.showError("Failed to Start Server", ex.getMessage());
        }
    }

    @FXML
    public void stopServer() {
        serverService.stopServer();
        // Cleanup network mapping when server stops
        cleanupNetworkMapping();
        // Reset External Join UI
        Platform.runLater(() -> {
            if (currentNetworkConfig != null) {
                disableExternalJoin();
            }
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
    
    // ========== External Join Methods ==========
    
    @FXML
    public void enableExternalJoin() {
        if (!serverService.isServerRunning()) {
            UiUtil.showError("Server Not Running", "Please start a server before enabling external join.");
            return;
        }
        
        Task<NetworkConfig> task = new Task<>() {
            @Override
            protected NetworkConfig call() throws Exception {
                updateMessage("Finding local network interface...");
                updateProgress(0.1, 1.0);
                
                // 1. Find local LAN IP
                String localLanIp = NetworkService.findLocalLanIp();
                
                updateMessage("Discovering UPnP router...");
                updateProgress(0.3, 1.0);
                
                // 2. Discover UPnP IGD
                Map<String, String> igdInfo = NetworkService.discoverUpnpIgd();
                String controlUrl = igdInfo.get("controlUrl");
                String serviceType = igdInfo.get("serviceType");
                
                updateMessage("Finding available port...");
                updateProgress(0.4, 1.0);
                
                // 3. Pick port (try 25565 first, then random)
                int internalPort = 25565; // MC default
                int externalPort = NetworkService.findAvailableExternalPort(25565);
                
                updateMessage("Mapping port on router...");
                updateProgress(0.5, 1.0);
                
                // 4. Try mapping with preferred port first
                boolean mapped = NetworkService.addPortMapping(controlUrl, serviceType, localLanIp, internalPort, 25565);
                
                if (!mapped) {
                    // Try random port
                    updateMessage("Port 25565 in use, trying random port...");
                    mapped = NetworkService.addPortMapping(controlUrl, serviceType, localLanIp, internalPort, externalPort);
                    if (mapped) {
                        externalPort = NetworkService.findAvailableExternalPort(externalPort);
                    } else {
                        throw new IOException("Failed to create port mapping - UPnP may be disabled or unsupported");
                    }
                } else {
                    externalPort = 25565;
                }
                
                updateMessage("Fetching public IP address...");
                updateProgress(0.9, 1.0);
                
                // 5. Get public IP
                String publicIp = NetworkService.fetchPublicIp();
                
                updateProgress(1.0, 1.0);
                
                return new NetworkConfig(localLanIp, internalPort, externalPort, publicIp, 
                                       controlUrl, serviceType, true);
            }
        };
        
        // Setup UI state during task
        task.setOnRunning(e -> {
            javafx.scene.Scene scene = enableExternalJoinBtn.getScene();
            if (scene != null) {
                scene.setCursor(Cursor.WAIT);
            }
            enableExternalJoinBtn.setDisable(true);
            disableExternalJoinBtn.setDisable(true);
            networkProgressBar.setVisible(true);
            networkProgressBar.setProgress(-1); // Indeterminate
            networkResultBox.setVisible(false);
            networkErrorBox.setVisible(false);
            vpnFallbackBox.setVisible(false);
        });
        
        task.setOnSucceeded(e -> {
            javafx.scene.Scene scene = enableExternalJoinBtn.getScene();
            if (scene != null) {
                scene.setCursor(Cursor.DEFAULT);
            }
            enableExternalJoinBtn.setDisable(false);
            networkProgressBar.setVisible(false);
            
            NetworkConfig config = task.getValue();
            currentNetworkConfig = config;
            
                // Test reachability (weak local test - not authoritative)
                boolean isReachable = NetworkService.testPortReachability(config.publicIp(), config.externalPort(), 2000);
                
                // Use the reachability result
                if (isReachable) {
                // Success: show join address
                networkStatusLabel.setText("External join enabled!");
                networkJoinAddressLabel.setText("Share this address with friends:");
                joinAddressField.setText(config.getJoinAddress());
                networkResultBox.setVisible(true);
                networkErrorBox.setVisible(false);
                vpnFallbackBox.setVisible(false);
                disableExternalJoinBtn.setDisable(false);
                
                appendToConsole("[NETWORK] External join enabled: " + config.getJoinAddress());
                appendToConsole("[NETWORK] Note: Local reachability test is not authoritative. External players may still have issues if ISP blocks inbound or CGNAT is present.");
            } else {
                // Mapping succeeded but unreachable
                networkErrorLabel.setText("Port mapping succeeded but connection test failed. This usually means:\n" +
                                        "• ISP/router blocks inbound connections (CGNAT)\n" +
                                        "• Firewall is blocking the port\n" +
                                        "• Direct join may not be possible on this network");
                networkErrorBox.setVisible(true);
                networkResultBox.setVisible(false);
                
                // Show VPN fallback
                vpnReasonLabel.setText("Your router successfully mapped the port, but external connections cannot reach it.\n" +
                                     "This is common with CGNAT (Carrier-Grade NAT) or ISP restrictions.");
                vpnFallbackBox.setVisible(true);
                disableExternalJoinBtn.setDisable(false);
                
                appendToConsole("[NETWORK] Port mapping created but unreachable - consider VPN");
            }
        });
        
        task.setOnFailed(e -> {
            javafx.scene.Scene scene = enableExternalJoinBtn.getScene();
            if (scene != null) {
                scene.setCursor(Cursor.DEFAULT);
            }
            enableExternalJoinBtn.setDisable(false);
            networkProgressBar.setVisible(false);
            
            Throwable ex = task.getException();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            
            // Check if it's a mapping failure
            if (errorMsg.contains("UPnP") || errorMsg.contains("discover") || errorMsg.contains("mapping")) {
                networkErrorLabel.setText("UPnP port mapping failed:\n" + errorMsg + 
                                        "\n\nThis usually means:\n" +
                                        "• UPnP is disabled on your router\n" +
                                        "• Your router doesn't support UPnP\n" +
                                        "• Direct join is not possible on this network");
                networkErrorBox.setVisible(true);
                
                // Show VPN fallback
                vpnReasonLabel.setText("Unable to automatically configure port mapping. UPnP may be disabled or unsupported.");
                vpnFallbackBox.setVisible(true);
            } else {
                networkErrorLabel.setText("Network configuration failed: " + errorMsg);
                networkErrorBox.setVisible(true);
            }
            
            networkResultBox.setVisible(false);
            
            appendToConsole("[NETWORK] External join setup failed: " + errorMsg);
        });
        
        new Thread(task, "network-setup").start();
    }
    
    @FXML
    public void disableExternalJoin() {
        if (currentNetworkConfig != null && currentNetworkConfig.mappingActive()) {
            boolean removed = NetworkService.removePortMapping(
                currentNetworkConfig.igdControlUrl(),
                currentNetworkConfig.igdServiceType(),
                currentNetworkConfig.externalPort()
            );
            
            if (removed) {
                appendToConsole("[NETWORK] Port mapping removed successfully");
                UiUtil.showInfo("External Join Disabled", "Port mapping has been removed from your router.");
            } else {
                appendToConsole("[NETWORK] Warning: Failed to remove port mapping (may have expired)");
                UiUtil.showInfo("External Join Disabled", "Port mapping removal attempted (may need manual cleanup).");
            }
            
            currentNetworkConfig = null;
        }
        
        // Reset UI
        enableExternalJoinBtn.setDisable(false);
        disableExternalJoinBtn.setDisable(true);
        networkResultBox.setVisible(false);
        networkErrorBox.setVisible(false);
        vpnFallbackBox.setVisible(false);
        vpnHelpBox.setVisible(false);
    }
    
    @FXML
    public void copyJoinAddress() {
        if (joinAddressField.getText() != null && !joinAddressField.getText().isEmpty()) {
            StringSelection selection = new StringSelection(joinAddressField.getText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            UiUtil.showInfo("Copied", "Join address copied to clipboard!");
        }
    }
    
    @FXML
    public void showVpnHelp() {
        vpnHelpBox.setVisible(!vpnHelpBox.isVisible());
    }
    
    private void cleanupNetworkMapping() {
        if (currentNetworkConfig != null && currentNetworkConfig.mappingActive()) {
            NetworkService.removePortMapping(
                currentNetworkConfig.igdControlUrl(),
                currentNetworkConfig.igdServiceType(),
                currentNetworkConfig.externalPort()
            );
        }
    }
}
