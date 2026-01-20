package com.jaceg18.localrealm.core.Controllers;


import com.jaceg18.localrealm.core.build.Server;
import com.jaceg18.localrealm.core.build.Util;
import com.jaceg18.localrealm.core.ui.UiUtil;
import javafx.scene.control.Button;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class SettingsTabController {

    private final TreeView<Path> fileView;
    private final TableView<Map.Entry<String, String>> buildTable;
    private final TableColumn<Map.Entry<String, String>, String> keyCol;
    private final TableColumn<Map.Entry<String, String>, String> valCol;
    private final ListView<Server> serverListView;

    private final Button saveFileBtn;
    private final Button openFileBtn;

    private Path currentFile;

    public SettingsTabController(TreeView<Path> fileView,
                                 TableView<Map.Entry<String, String>> buildTable,
                                 TableColumn<Map.Entry<String, String>, String> keyCol,
                                 TableColumn<Map.Entry<String, String>, String> valCol,
                                 ListView<Server> serverListView,
                                 Button saveFileBtn,
                                 Button openFileBtn) {
        this.fileView = fileView;
        this.buildTable = buildTable;
        this.keyCol = keyCol;
        this.valCol = valCol;
        this.serverListView = serverListView;
        this.saveFileBtn = saveFileBtn;
        this.openFileBtn = openFileBtn;
    }

    public void init() {
        setupFileTree();
        setupDragAndDrop();
        buildTable.setEditable(true);
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
                        Path name = item.getFileName();
                        setText(name != null ? name.toString() : item.toString());
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

        serverListView.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            if (newV != null) {
                TreeItem<Path> root = createNode(newV.path());
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
        valCol.setOnEditCommit(event -> event.getRowValue().setValue(event.getNewValue()));

        fileView.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            if (newV == null) {
                buildTable.getItems().clear();
                currentFile = null;
                return;
            }

            Path selectedPath = newV.getValue();
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
        });
    }

    private TreeItem<Path> createNode(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);

        if (Files.isDirectory(path)) {
            try {
                boolean hasChildren = Files.list(path).findAny().isPresent();
                if (hasChildren) item.getChildren().add(new TreeItem<>(null));
            } catch (IOException ignored) {
            }

            item.expandedProperty().addListener((obs, was, isNow) -> {
                if (!isNow) return;

                boolean needsLoad = item.getChildren().isEmpty()
                        || (item.getChildren().size() == 1 && item.getChildren().getFirst().getValue() == null);

                if (!needsLoad) return;

                item.getChildren().clear();
                try (var stream = Files.list(path)) {
                    stream.sorted()
                            .map(this::createNode)
                            .forEach(item.getChildren()::add);
                } catch (IOException ignored) {
                }
            });
        }

        return item;
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
                if (selectedItem == null) {
                    UiUtil.showError("No Target", "Please select a directory in the file tree to drop files into.");
                } else {
                    Path targetPath = selectedItem.getValue();
                    if (targetPath != null && Files.isDirectory(targetPath)) {
                        for (File file : db.getFiles()) {
                            try {
                                Path destination = targetPath.resolve(file.getName());
                                Files.copy(file.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                                success = true;

                                if (!selectedItem.isExpanded()) selectedItem.setExpanded(true);
                                selectedItem.getChildren().add(createNode(destination));
                                selectedItem.getChildren().sort((a, b) -> {
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
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    public void saveFile() {
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

    public void openFile() {
        if (currentFile == null || currentFile.toFile().isDirectory()) {
            UiUtil.showError("No File Selected", "Please select a file to open.");
            return;
        }
        if (!Desktop.isDesktopSupported()) return;

        try {
            Desktop.getDesktop().open(currentFile.toFile());
        } catch (IOException e){
            UiUtil.showError("Failed Opening File", "Failed to open file: " + currentFile.getFileName() + "\n" + e.getMessage());
        }
    }
}
