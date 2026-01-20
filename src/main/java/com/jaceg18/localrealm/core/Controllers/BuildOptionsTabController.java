package com.jaceg18.localrealm.core.Controllers;

import com.jaceg18.localrealm.core.build.Util;
import com.jaceg18.localrealm.core.manager.BuildOptionsManager;
import com.jaceg18.localrealm.core.ui.UiUtil;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;

import java.io.IOException;
import java.util.Map;

public final class BuildOptionsTabController {

    private final TableView<Map.Entry<String, String>> buildOptionsTable;
    private final TableColumn<Map.Entry<String, String>, String> buildNameCol;
    private final TableColumn<Map.Entry<String, String>, String> buildUrlCol;
    private final ChoiceBox<String> buildBox;

    public BuildOptionsTabController(TableView<Map.Entry<String, String>> buildOptionsTable,
                                    TableColumn<Map.Entry<String, String>, String> buildNameCol,
                                    TableColumn<Map.Entry<String, String>, String> buildUrlCol,
                                    ChoiceBox<String> buildBox) {
        this.buildOptionsTable = buildOptionsTable;
        this.buildNameCol = buildNameCol;
        this.buildUrlCol = buildUrlCol;
        this.buildBox = buildBox;
    }

    public void init() {
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

    private void refreshBuildOptions() {
        buildBox.getItems().clear();
        Util.getBuildOptions().keySet().forEach(buildBox.getItems()::add);
        if (!buildBox.getItems().isEmpty()) {
            buildBox.getSelectionModel().selectFirst();
        }
    }

    public void addBuildOption() {
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

    public void removeBuildOption() {
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
}

