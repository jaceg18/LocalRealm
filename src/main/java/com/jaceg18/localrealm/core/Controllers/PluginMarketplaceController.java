package com.jaceg18.localrealm.core.Controllers;

import com.jaceg18.localrealm.annotation.Provisional;
import com.jaceg18.localrealm.core.build.Plugin;
import com.jaceg18.localrealm.core.build.Server;
import com.jaceg18.localrealm.core.service.PluginService;
import com.jaceg18.localrealm.core.ui.UiUtil;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@Provisional(reason="Substitute for future overhaul", expiresBy = "2.0.0")
public class PluginMarketplaceController {
    
    private final GridPane pluginGridPane;
    private final TextField searchField;
    private final Button searchBtn;
    private final Button refreshBtn;
    private final Button installBtn;
    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final TextArea descriptionArea;
    private final ImageView pluginIconView;
    private VBox iconContainer;
    private final Label pluginNameLabel;
    private final Label pluginAuthorLabel;
    private final Label pluginDownloadsLabel;
    private final Label pluginRatingLabel;
    private final Label pluginPriceLabel;
    private final ListView<Server> serverSelectListView;
    
    private final PluginService pluginService;
    private final ObservableList<Server> serverList;
    private final Consumer<String> appendToConsole;
    
    private Plugin selectedPlugin;
    
    public PluginMarketplaceController(
            GridPane pluginGridPane,
            TextField searchField,
            Button searchBtn,
            Button refreshBtn,
            Button installBtn,
            ProgressBar progressBar,
            Label statusLabel,
            TextArea descriptionArea,
            ImageView pluginIconView,
            Label pluginNameLabel,
            Label pluginAuthorLabel,
            Label pluginDownloadsLabel,
            Label pluginRatingLabel,
            Label pluginPriceLabel,
            ListView<Server> serverSelectListView,
            ObservableList<Server> serverList,
            Consumer<String> appendToConsole) {
        this.pluginGridPane = pluginGridPane;
        this.searchField = searchField;
        this.searchBtn = searchBtn;
        this.refreshBtn = refreshBtn;
        this.installBtn = installBtn;
        this.progressBar = progressBar;
        this.statusLabel = statusLabel;
        this.descriptionArea = descriptionArea;
        this.pluginIconView = pluginIconView;
        this.pluginNameLabel = pluginNameLabel;
        this.pluginAuthorLabel = pluginAuthorLabel;
        this.pluginDownloadsLabel = pluginDownloadsLabel;
        this.pluginRatingLabel = pluginRatingLabel;
        this.pluginPriceLabel = pluginPriceLabel;
        this.serverSelectListView = serverSelectListView;
        this.serverList = serverList;
        this.appendToConsole = appendToConsole;
        this.pluginService = new PluginService();
        
        setupUI();
    }
    
    private void setupUI() {
        pluginGridPane.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(33.33);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(33.33);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(33.33);
        pluginGridPane.getColumnConstraints().addAll(col1, col2, col3);
        
        serverSelectListView.setItems(serverList);
        serverSelectListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            installBtn.setDisable(selectedPlugin == null || selected == null);
        });
        
        searchBtn.setOnAction(e -> searchPlugins());
        refreshBtn.setOnAction(e -> showPopular());
        installBtn.setOnAction(e -> installSelectedPlugin());
        installBtn.setDisable(true);
        
        showPopular();
    }
    
    public void searchPlugins() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            showPopular();
            return;
        }
        loadPlugins(query, null, 30);
    }
    
    public void showPopular() {
        loadPlugins(null, "-downloads", 30);
    }
    
    public void showNewest() {
        loadPlugins(null, "-updateDate", 30);
    }
    
    private void loadPlugins(String query, String sort, int size) {
        Platform.runLater(() -> {
            statusLabel.setText("Loading plugins...");
            progressBar.setVisible(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            searchBtn.setDisable(true);
            refreshBtn.setDisable(true);
        });
        
        Task<List<Plugin>> task = new Task<List<Plugin>>() {
            @Override
            protected List<Plugin> call() throws Exception {
                List<Plugin> plugins = pluginService.searchPlugins(query, sort, size).get();
                System.out.println("Loaded " + plugins.size() + " plugins (query: " + query + ", sort: " + sort + ")");
                if (!plugins.isEmpty()) {
                    System.out.println("First plugin: " + plugins.get(0).name() + " by " + plugins.get(0).author());
                    System.out.println("Icon URL: " + plugins.get(0).iconUrl());
                }
                return plugins;
            }
        };
        
        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                try {
                    List<Plugin> plugins = task.get();
                    displayPlugins(plugins);
                    statusLabel.setText("Loaded " + plugins.size() + " plugins");
                    progressBar.setVisible(false);
                } catch (Exception ex) {
                    statusLabel.setText("Error loading plugins");
                    UiUtil.showError("Error", "Failed to load plugins: " + ex.getMessage());
                    progressBar.setVisible(false);
                } finally {
                    searchBtn.setDisable(false);
                    refreshBtn.setDisable(false);
                }
            });
        });
        
        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Error loading plugins");
                UiUtil.showError("Error", "Failed to load plugins: " + task.getException().getMessage());
                progressBar.setVisible(false);
                searchBtn.setDisable(false);
                refreshBtn.setDisable(false);
            });
        });
        
        new Thread(task).start();
    }
    
    private void displayPlugins(List<Plugin> plugins) {
        Platform.runLater(() -> {
            pluginGridPane.getChildren().clear();
            pluginGridPane.getRowConstraints().clear();
            
            int col = 0;
            int row = 0;
            int cols = 3;
            
            for (Plugin plugin : plugins) {
                VBox card = createPluginCard(plugin);
                GridPane.setColumnIndex(card, col);
                GridPane.setRowIndex(card, row);
                pluginGridPane.getChildren().add(card);
                
                col++;
                if (col >= cols) {
                    col = 0;
                    row++;
                }
            }
        });
    }

    @Provisional(reason="Because why not")
    private VBox createPluginCard(Plugin plugin) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(140);
        card.setPrefHeight(140);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.06); -fx-background-radius: 12; -fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-radius: 12; -fx-border-width: 1; -fx-cursor: hand;");
        
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.10); -fx-background-radius: 12; -fx-border-color: rgba(255, 255, 255, 0.12); -fx-border-radius: 12; -fx-border-width: 1; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.06); -fx-background-radius: 12; -fx-border-color: rgba(255, 255, 255, 0.08); -fx-border-radius: 12; -fx-border-width: 1; -fx-cursor: hand;"));
        card.setOnMouseClicked(e -> selectPlugin(plugin));
        
        HBox header = new HBox(12);
        header.setAlignment(Pos.TOP_LEFT);
        
        VBox iconContainer = new VBox();
        iconContainer.setMinWidth(48);
        iconContainer.setPrefWidth(48);
        iconContainer.setMaxWidth(48);
        iconContainer.setMinHeight(48);
        iconContainer.setPrefHeight(48);
        iconContainer.setMaxHeight(48);
        iconContainer.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 8; -fx-border-width: 1;");
        
        if (plugin.iconUrl() != null && !plugin.iconUrl().isEmpty()) {
            WebView iconWebView = new WebView();
            iconWebView.setPrefWidth(48);
            iconWebView.setPrefHeight(48);
            iconWebView.setMaxWidth(48);
            iconWebView.setMaxHeight(48);
            iconWebView.setContextMenuEnabled(false);
            iconWebView.setStyle("-fx-background-color: transparent;");
            WebEngine iconEngine = iconWebView.getEngine();
            
            String iconUrl = plugin.iconUrl().replace("\"", "&quot;").replace("'", "&#39;");
            String iconHtml = "<html><head><style>html, body { margin: 0; padding: 0; background: transparent !important; } img { width: 48px; height: 48px; border-radius: 8px; object-fit: cover; }</style></head><body><img src=\"" + 
                             iconUrl + "\" /></body><script>document.documentElement.style.backgroundColor = 'transparent'; document.body.style.backgroundColor = 'transparent';</script></html>";
            iconEngine.loadContent(iconHtml);
            
            iconEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    Platform.runLater(() -> {
                        iconEngine.executeScript(
                            "document.documentElement.style.backgroundColor = 'transparent'; " +
                            "document.body.style.backgroundColor = 'transparent';"
                        );
                    });
                }
            });
            
            iconContainer.getChildren().add(iconWebView);
        }
        
        VBox infoBox = new VBox(4);
        infoBox.setAlignment(Pos.TOP_LEFT);
        
        Label nameLabel = new Label(plugin.name());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e6edf7;");
        nameLabel.setWrapText(false);
        
        Label authorLabel = new Label("by " + plugin.author());
        authorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(230, 237, 247, 0.65);");
        
        HBox statsBox = new HBox(8);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        
        if (plugin.downloads() > 0) {
            Label downloadsLabel = new Label(formatNumber(plugin.downloads()) + " downloads");
            downloadsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(230, 237, 247, 0.55);");
            statsBox.getChildren().add(downloadsLabel);
        }
        
        if (plugin.rating() > 0) {
            Label ratingLabel = new Label("★ " + String.format("%.1f", plugin.rating()));
            ratingLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255, 215, 0, 0.8);");
            statsBox.getChildren().add(ratingLabel);
        }
        
        if (plugin.price() > 0) {
            Label priceLabel = new Label("$" + String.format("%.2f", plugin.price()));
            priceLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255, 200, 87, 0.9); -fx-font-weight: bold;");
            statsBox.getChildren().add(priceLabel);
        }
        
        infoBox.getChildren().addAll(nameLabel, authorLabel, statsBox);
        header.getChildren().addAll(iconContainer, infoBox);
        
        card.getChildren().add(header);
        VBox.setVgrow(card, Priority.ALWAYS);
        
        return card;
    }
    
    private String formatNumber(int num) {
        if (num >= 1000000) {
            return String.format("%.1fM", num / 1000000.0);
        } else if (num >= 1000) {
            return String.format("%.1fK", num / 1000.0);
        }
        return String.valueOf(num);
    }
    
    private void selectPlugin(Plugin plugin) {
        selectedPlugin = plugin;
        updatePluginDetails(plugin);
        installBtn.setDisable(plugin == null || serverSelectListView.getSelectionModel().getSelectedItem() == null);
    }
    
    private void updatePluginDetails(Plugin plugin) {
        if (plugin == null) {
            pluginNameLabel.setText("Select a Plugin");
            pluginAuthorLabel.setText("");
            pluginDownloadsLabel.setText("");
            pluginRatingLabel.setText("");
            pluginPriceLabel.setText("");
            descriptionArea.clear();
            pluginIconView.setImage(null);
            return;
        }
        
        pluginNameLabel.setText(plugin.name());
        pluginAuthorLabel.setText("by " + plugin.author());
        pluginDownloadsLabel.setText(plugin.downloads() + " downloads");
        
        if (plugin.rating() > 0) {
            pluginRatingLabel.setText("★ " + String.format("%.1f", plugin.rating()));
        } else {
            pluginRatingLabel.setText("No rating");
        }
        
        if (plugin.price() > 0) {
            pluginPriceLabel.setText("$" + String.format("%.2f", plugin.price()));
        } else {
            pluginPriceLabel.setText("Free");
        }
        
        descriptionArea.setText(plugin.description() != null && !plugin.description().isEmpty() 
            ? plugin.description() : "No description available.");
        
        if (iconContainer == null) {
            if (pluginIconView.getParent() instanceof VBox) {
                iconContainer = (VBox) pluginIconView.getParent();
            }
        }
        
        if (iconContainer != null) {
            iconContainer.getChildren().clear();
            
            if (plugin.iconUrl() != null && !plugin.iconUrl().isEmpty()) {
                WebView iconWebView = new WebView();
                iconWebView.setPrefWidth(80);
                iconWebView.setPrefHeight(80);
                iconWebView.setMaxWidth(80);
                iconWebView.setMaxHeight(80);
                iconWebView.setContextMenuEnabled(false);
                iconWebView.setStyle("-fx-background-color: transparent;");
                WebEngine iconEngine = iconWebView.getEngine();
                
                String iconUrl = plugin.iconUrl().replace("\"", "&quot;").replace("'", "&#39;");
                String iconHtml = "<html><head><style>html, body { margin: 0; padding: 0; background: transparent !important; } img { width: 80px; height: 80px; border-radius: 8px; object-fit: cover; }</style></head><body><img src=\"" + 
                                 iconUrl + "\" /></body><script>document.documentElement.style.backgroundColor = 'transparent'; document.body.style.backgroundColor = 'transparent';</script></html>";
                iconEngine.loadContent(iconHtml);
                
                iconEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        Platform.runLater(() -> {
                            iconEngine.executeScript(
                                "document.documentElement.style.backgroundColor = 'transparent'; " +
                                "document.body.style.backgroundColor = 'transparent';"
                            );
                        });
                    }
                });
                
                iconContainer.getChildren().add(iconWebView);
            } else {
                ImageView placeholder = new ImageView();
                placeholder.setFitWidth(80);
                placeholder.setFitHeight(80);
                iconContainer.getChildren().add(placeholder);
            }
        }
    }
    
    public void installSelectedPlugin() {
        Plugin plugin = selectedPlugin;
        Server server = serverSelectListView.getSelectionModel().getSelectedItem();
        
        if (plugin == null || server == null) {
            UiUtil.showError("Error", "Please select both a plugin and a server");
            return;
        }
        
        installPlugin(plugin, server.path());
    }
    
    private void installPlugin(Plugin plugin, Path serverPath) {
        statusLabel.setText("Installing " + plugin.name() + "...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        installBtn.setDisable(true);
        
        Task<Path> downloadTask = new Task<Path>() {
            @Override
            protected Path call() throws Exception {
                String downloadUrl = pluginService.getLatestDownloadUrl(plugin.id()).get();
                return pluginService.downloadPlugin(downloadUrl, serverPath).get();
            }
        };
        
        downloadTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                try {
                    Path pluginFile = downloadTask.get();
                    statusLabel.setText("Installed " + plugin.name() + " successfully");
                    appendToConsole.accept("Plugin installed: " + plugin.name() + " -> " + pluginFile);
                    UiUtil.showInfo("Success", "Plugin '" + plugin.name() + "' has been installed to " + serverPath.resolve("plugins"));
                    progressBar.setVisible(false);
                } catch (Exception ex) {
                    statusLabel.setText("Installation failed");
                    UiUtil.showError("Error", "Failed to install plugin: " + ex.getMessage());
                    progressBar.setVisible(false);
                } finally {
                    installBtn.setDisable(false);
                }
            });
        });
        
        downloadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                statusLabel.setText("Installation failed");
                UiUtil.showError("Error", "Failed to install plugin: " + downloadTask.getException().getMessage());
                progressBar.setVisible(false);
                installBtn.setDisable(false);
            });
        });
        
        new Thread(downloadTask).start();
    }
}
