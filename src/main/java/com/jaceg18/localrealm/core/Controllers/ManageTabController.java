package com.jaceg18.localrealm.core.Controllers;


import com.jaceg18.localrealm.core.build.Server;
import com.jaceg18.localrealm.core.manager.ServerManager;
import com.jaceg18.localrealm.core.service.ServerService;
import com.jaceg18.localrealm.core.ui.UiUtil;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class ManageTabController {

    private final ListView<Server> serverListView;
    private final ObservableList<Server> serverList;

    private final Button addServerBtn;
    private final Button removeServerBtn;
    private final Button refreshServersBtn;
    private final Button runBtn;
    private final Button stopBtn;

    private final Label serverConsoleLabel;
    private final TextField consoleField;

    private final CheckBox noGuiCB;
    private final Spinner<Integer> minSpinner;
    private final Spinner<Integer> maxSpinner;

    private final ServerService serverService;
    private final Consumer<String> appendToServerConsole;
    private final Consumer<String> appendToConsole;

    private volatile long serverStartTime;
    private volatile int serverMaxMemoryGB;

    public ManageTabController(ListView<Server> serverListView,
                               ObservableList<Server> serverList,
                               Button addServerBtn,
                               Button removeServerBtn,
                               Button refreshServersBtn,
                               Button runBtn,
                               Button stopBtn,
                               Label serverConsoleLabel,
                               TextField consoleField,
                               CheckBox noGuiCB,
                               Spinner<Integer> minSpinner,
                               Spinner<Integer> maxSpinner,
                               ServerService serverService,
                               Consumer<String> appendToServerConsole,
                               Consumer<String> appendToConsole) {
        this.serverListView = serverListView;
        this.serverList = serverList;
        this.addServerBtn = addServerBtn;
        this.removeServerBtn = removeServerBtn;
        this.refreshServersBtn = refreshServersBtn;
        this.runBtn = runBtn;
        this.stopBtn = stopBtn;
        this.serverConsoleLabel = serverConsoleLabel;
        this.consoleField = consoleField;
        this.noGuiCB = noGuiCB;
        this.minSpinner = minSpinner;
        this.maxSpinner = maxSpinner;
        this.serverService = serverService;
        this.appendToServerConsole = appendToServerConsole;
        this.appendToConsole = appendToConsole;
    }

    public long getServerStartTime() { return serverStartTime; }
    public int getServerMaxMemoryGB() { return serverMaxMemoryGB; }

    public void refreshServers() {
        try {
            List<Server> servers = ServerManager.loadServers();
            serverList.clear();
            serverList.addAll(servers);
        } catch (Exception e) {
            UiUtil.showError("Error Loading Servers", "Failed to load server list: " + e.getMessage());
        }
    }

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
        appendToServerConsole.accept("=== Starting Server: " + selected + " ===");
        appendToServerConsole.accept("[INFO] Server path: " + serverPath);

        try {
            serverStartTime = System.currentTimeMillis();
            serverMaxMemoryGB = maxSpinner.getValue();

            serverService.runServer(
                    serverPath,
                    minSpinner.getValue(),
                    maxSpinner.getValue(),
                    noGuiCB.isSelected()
            );
        } catch (RuntimeException ex) {
            serverMaxMemoryGB = 0;
            UiUtil.showError("Failed to Start Server", ex.getMessage());
        }
    }

    public void stopServer() {
        serverService.stopServer();
        serverMaxMemoryGB = 0;
    }

    public void sendConsole() {
        if (consoleField.getText().isEmpty()) return;
        serverService.sendCommand(consoleField.getText());
        consoleField.setText("");
    }
}
