package com.jaceg18.localrealm;

import com.jaceg18.localrealm.core.Controllers.*;
import com.jaceg18.localrealm.core.build.Server;
import com.jaceg18.localrealm.core.service.ServerService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Map;

public class Controller {


    // ===== Build tab =====
    @FXML
    protected Spinner<Integer> minAlocSpinner;
    @FXML
    protected Spinner<Integer> maxAlocSpinner;
    @FXML
    protected ChoiceBox<String> buildBox;
    @FXML
    protected CheckBox autoEulaCB, noGuiCB;
    @FXML
    protected ProgressBar progressBar;
    @FXML
    protected TextArea consoleArea;
    @FXML
    protected Label statusLabel;
    @FXML
    protected ScrollPane consoleScrollPane;
    @FXML
    protected Button clearConsoleBtn;

    // ===== Manage tab =====
    @FXML
    protected TabPane mainTabPane;
    @FXML
    protected ListView<Server> serverListView;
    @FXML
    protected Button addServerBtn, removeServerBtn, runSelectedServerBtn, refreshServersBtn;
    @FXML
    protected TextArea serverConsoleArea;
    @FXML
    protected ScrollPane serverConsoleScrollPane;
    @FXML
    protected Button clearServerConsoleBtn;
    @FXML
    protected Label serverConsoleLabel;
    @FXML
    protected TextField consoleField;
    @FXML
    protected Button stopServerBtn;

    // ===== Settings tab =====
    @FXML
    protected TreeView<Path> fileView;
    @FXML
    private TableView<Map.Entry<String, String>> buildTable;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> keyCol;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> valCol;
    @FXML
    protected Button saveFileBtn;
    @FXML
    protected Button openFileBtn;

    // ===== Build Options tab =====
    @FXML
    private TableView<Map.Entry<String, String>> buildOptionsTable;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> buildNameCol;
    @FXML
    private TableColumn<Map.Entry<String, String>, String> buildUrlCol;
    @FXML
    protected Button addBuildOptionBtn;
    @FXML
    protected Button removeBuildOptionBtn;

    // ===== External Join tab =====
    @FXML
    private Button enableExternalJoinBtn;
    @FXML
    private Button disableExternalJoinBtn;
    @FXML
    private ProgressBar networkProgressBar;
    @FXML
    private VBox networkResultBox;
    @FXML
    private Label networkStatusLabel;
    @FXML
    private Label networkJoinAddressLabel;
    @FXML
    private TextField joinAddressField;
    @FXML
    private VBox networkErrorBox;
    @FXML
    private Label networkErrorLabel;
    @FXML
    private VBox vpnFallbackBox;
    @FXML
    private Label vpnReasonLabel;
    @FXML
    private VBox vpnHelpBox;
    @FXML
    private Button copyJoinAddressBtn;
    @FXML
    private Button showVpnHelpBtn;

    // ===== Plugin Marketplace tab =====
    @FXML
    private javafx.scene.layout.GridPane pluginGridPane;
    @FXML
    private TextField pluginSearchField;
    @FXML
    private Button pluginSearchBtn;
    @FXML
    private Button pluginRefreshBtn;
    @FXML
    private Button pluginInstallBtn;
    @FXML
    private ProgressBar pluginProgressBar;
    @FXML
    private Label pluginStatusLabel;
    @FXML
    private TextArea pluginDescriptionArea;
    @FXML
    private javafx.scene.image.ImageView pluginIconView;
    @FXML
    private Label pluginNameLabel;
    @FXML
    private Label pluginAuthorLabel;
    @FXML
    private Label pluginDownloadsLabel;
    @FXML
    private Label pluginRatingLabel;
    @FXML
    private Label pluginPriceLabel;
    @FXML
    private ListView<Server> serverSelectListView;

    // ===== Stats labels =====
    @FXML
    private Label statStatusLabel;
    @FXML
    private Label statUptimeLabel;
    @FXML
    private Label statMemoryLabel;
    @FXML
    private Label statCpuLabel;
    @FXML
    private Label statThreadsLabel;

    // ===== Shared state/services =====
    private ObservableList<Server> serverList;
    private ServerService serverService;

    private final ConsoleSupport consoleSupport = new ConsoleSupport();

    private BuildTabController buildTab;
    private ManageTabController manageTab;
    private SettingsTabController settingsTab;
    private BuildOptionsTabController buildOptionsTab;
    private ExternalJoinController externalJoin;
    private StatsController stats;
    private com.jaceg18.localrealm.core.Controllers.PluginMarketplaceController pluginMarketplace;

    @FXML
    public void initialize() {
        serverService = new ServerService(
                this::appendToConsole,
                this::appendToServerConsole,
                this::updateStatus
        );

        // Server list model
        serverList = FXCollections.observableArrayList();
        serverListView.setItems(serverList);

        // Wire modules
        buildTab = new BuildTabController(
                buildBox, minAlocSpinner, maxAlocSpinner,
                autoEulaCB, progressBar, statusLabel,
                serverService, this::appendToConsole, this::updateStatus,
                this::refreshServers
        );

        manageTab = new ManageTabController(
                serverListView, serverList,
                addServerBtn, removeServerBtn, refreshServersBtn,
                runSelectedServerBtn, stopServerBtn,
                serverConsoleLabel,
                consoleField,
                noGuiCB, minAlocSpinner, maxAlocSpinner,
                serverService,
                this::appendToServerConsole,
                this::appendToConsole
        );

        settingsTab = new SettingsTabController(
                fileView, buildTable, keyCol, valCol,
                serverListView,
                saveFileBtn, openFileBtn
        );

        buildOptionsTab = new BuildOptionsTabController(
                buildOptionsTable, buildNameCol, buildUrlCol,
                buildBox
        );

        externalJoin = new ExternalJoinController(
                enableExternalJoinBtn, disableExternalJoinBtn,
                networkProgressBar, networkResultBox, networkErrorBox,
                vpnFallbackBox, vpnHelpBox,
                networkStatusLabel, networkJoinAddressLabel, networkErrorLabel, vpnReasonLabel,
                joinAddressField,
                serverService,
                this::appendToConsole
        );

        stats = new StatsController(
                statStatusLabel, statUptimeLabel, statMemoryLabel, statCpuLabel, statThreadsLabel,
                serverService
        );

        pluginMarketplace = new com.jaceg18.localrealm.core.Controllers.PluginMarketplaceController(
                pluginGridPane, pluginSearchField, pluginSearchBtn, pluginRefreshBtn,
                pluginInstallBtn, pluginProgressBar, pluginStatusLabel,
                pluginDescriptionArea, pluginIconView,
                pluginNameLabel, pluginAuthorLabel, pluginDownloadsLabel,
                pluginRatingLabel, pluginPriceLabel,
                serverSelectListView, serverList, this::appendToConsole
        );

        // Key handling
        consoleArea.addEventFilter(KeyEvent.KEY_PRESSED, e ->
                consoleSupport.handleEnterToSend(e, consoleArea, serverService, this::appendToConsole)
        );
        serverConsoleArea.addEventFilter(KeyEvent.KEY_PRESSED, e ->
                consoleSupport.handleEnterToSend(e, serverConsoleArea, serverService, this::appendToServerConsole)
        );

        // Final setup calls
        buildTab.init();
        manageTab.refreshServers();     // loads list
        settingsTab.init();
        buildOptionsTab.init();
        externalJoin.init();
        stats.start();

        appendToConsole("LocalRealm initialized. Ready to build.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serverService.shutdownHookStop();
            externalJoin.cleanupNetworkMapping();
        }));
    }

    // ===== FXML handlers simply delegate =====

    @FXML
    public void buildServer() {
        buildTab.buildServer();
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
        manageTab.refreshServers();
    }

    @FXML
    public void addServer() {
        manageTab.addServer();
    }

    @FXML
    public void removeServer() {
        manageTab.removeServer();
    }

    @FXML
    public void runSelectedServer() {
        manageTab.runSelectedServer();
        stats.onServerStart(manageTab.getServerStartTime(), manageTab.getServerMaxMemoryGB());
    }

    @FXML
    public void stopServer() {
        manageTab.stopServer();
        externalJoin.cleanupNetworkMapping();
        stats.onServerStop();
    }

    @FXML
    public void sendConsole() {
        manageTab.sendConsole();
    }

    @FXML
    public void saveFile() {
        settingsTab.saveFile();
    }

    @FXML
    public void openFile() {
        settingsTab.openFile();
    }

    @FXML
    public void addBuildOption() {
        buildOptionsTab.addBuildOption();
    }

    @FXML
    public void removeBuildOption() {
        buildOptionsTab.removeBuildOption();
    }

    @FXML
    public void enableExternalJoin() {
        externalJoin.enableExternalJoin();
    }

    @FXML
    public void disableExternalJoin() {
        externalJoin.disableExternalJoin();
    }

    @FXML
    public void copyJoinAddress() {
        externalJoin.copyJoinAddress();
    }

    @FXML
    public void showVpnHelp() {
        externalJoin.toggleVpnHelp();
    }

    // ===== Plugin Marketplace handlers =====
    @FXML
    public void searchPlugins() {
        pluginMarketplace.searchPlugins();
    }

    @FXML
    public void refreshPlugins() {
        pluginMarketplace.showPopular();
    }

    @FXML
    public void installPlugin() {
        pluginMarketplace.installSelectedPlugin();
    }

    @FXML
    public void showPopular() {
        pluginMarketplace.showPopular();
    }

    @FXML
    public void showNewest() {
        pluginMarketplace.showNewest();
    }

    // ===== shared UI helpers =====

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
}
