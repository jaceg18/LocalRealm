package com.jaceg18.localrealm.core.Controllers;

import com.jaceg18.localrealm.core.service.NetworkService;
import com.jaceg18.localrealm.core.service.NetworkService.NetworkConfig;
import com.jaceg18.localrealm.core.service.ServerService;
import com.jaceg18.localrealm.core.ui.UiUtil;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public final class ExternalJoinController {

    private final Button enableExternalJoinBtn;
    private final Button disableExternalJoinBtn;
    private final ProgressBar networkProgressBar;
    private final VBox networkResultBox;
    private final VBox networkErrorBox;
    private final VBox vpnFallbackBox;
    private final VBox vpnHelpBox;
    private final Label networkStatusLabel;
    private final Label networkJoinAddressLabel;
    private final Label networkErrorLabel;
    private final Label vpnReasonLabel;
    private final TextField joinAddressField;

    private final ServerService serverService;
    private final Consumer<String> appendToConsole;

    private NetworkConfig currentNetworkConfig;

    public ExternalJoinController(Button enableExternalJoinBtn,
                                  Button disableExternalJoinBtn,
                                  ProgressBar networkProgressBar,
                                  VBox networkResultBox,
                                  VBox networkErrorBox,
                                  VBox vpnFallbackBox,
                                  VBox vpnHelpBox,
                                  Label networkStatusLabel,
                                  Label networkJoinAddressLabel,
                                  Label networkErrorLabel,
                                  Label vpnReasonLabel,
                                  TextField joinAddressField,
                                  ServerService serverService,
                                  Consumer<String> appendToConsole) {
        this.enableExternalJoinBtn = enableExternalJoinBtn;
        this.disableExternalJoinBtn = disableExternalJoinBtn;
        this.networkProgressBar = networkProgressBar;
        this.networkResultBox = networkResultBox;
        this.networkErrorBox = networkErrorBox;
        this.vpnFallbackBox = vpnFallbackBox;
        this.vpnHelpBox = vpnHelpBox;
        this.networkStatusLabel = networkStatusLabel;
        this.networkJoinAddressLabel = networkJoinAddressLabel;
        this.networkErrorLabel = networkErrorLabel;
        this.vpnReasonLabel = vpnReasonLabel;
        this.joinAddressField = joinAddressField;
        this.serverService = serverService;
        this.appendToConsole = appendToConsole;
    }

    public void init() {
        networkResultBox.setVisible(false);
        networkErrorBox.setVisible(false);
        vpnFallbackBox.setVisible(false);
        vpnHelpBox.setVisible(false);
        networkProgressBar.setVisible(false);
    }

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
                
                String localLanIp = NetworkService.findLocalLanIp();
                
                updateMessage("Discovering UPnP router...");
                updateProgress(0.3, 1.0);
                
                Map<String, String> igdInfo = NetworkService.discoverUpnpIgd();
                String controlUrl = igdInfo.get("controlUrl");
                String serviceType = igdInfo.get("serviceType");
                
                updateMessage("Finding available port...");
                updateProgress(0.4, 1.0);
                
                int internalPort = 25565;
                int externalPort = NetworkService.findAvailableExternalPort(25565);
                
                updateMessage("Mapping port on router...");
                updateProgress(0.5, 1.0);
                
                boolean mapped = NetworkService.addPortMapping(controlUrl, serviceType, localLanIp, internalPort, 25565);
                
                if (!mapped) {
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
                
                String publicIp = NetworkService.fetchPublicIp();
                
                updateProgress(1.0, 1.0);
                
                return new NetworkConfig(localLanIp, internalPort, externalPort, publicIp, 
                                       controlUrl, serviceType, true);
            }
        };
        
        task.setOnRunning(e -> {
            javafx.scene.Scene scene = enableExternalJoinBtn.getScene();
            if (scene != null) {
                scene.setCursor(Cursor.WAIT);
            }
            enableExternalJoinBtn.setDisable(true);
            disableExternalJoinBtn.setDisable(true);
            networkProgressBar.setVisible(true);
            networkProgressBar.setProgress(-1);
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
            
            boolean isReachable = NetworkService.testPortReachability(config.publicIp(), config.externalPort(), 2000);
            
            if (isReachable) {
                networkStatusLabel.setText("External join enabled!");
                networkJoinAddressLabel.setText("Share this address with friends:");
                joinAddressField.setText(config.getJoinAddress());
                networkResultBox.setVisible(true);
                networkErrorBox.setVisible(false);
                vpnFallbackBox.setVisible(false);
                disableExternalJoinBtn.setDisable(false);
                
                appendToConsole.accept("[NETWORK] External join enabled: " + config.getJoinAddress());
                appendToConsole.accept("[NETWORK] Note: Local reachability test is not authoritative. External players may still have issues if ISP blocks inbound or CGNAT is present.");
            } else {
                networkErrorLabel.setText("Port mapping succeeded but connection test failed. This usually means:\n" +
                                        "• ISP/router blocks inbound connections (CGNAT)\n" +
                                        "• Firewall is blocking the port\n" +
                                        "• Direct join may not be possible on this network");
                networkErrorBox.setVisible(true);
                networkResultBox.setVisible(false);
                
                vpnReasonLabel.setText("Your router successfully mapped the port, but external connections cannot reach it.\n" +
                                     "This is common with CGNAT (Carrier-Grade NAT) or ISP restrictions.");
                vpnFallbackBox.setVisible(true);
                disableExternalJoinBtn.setDisable(false);
                
                appendToConsole.accept("[NETWORK] Port mapping created but unreachable - consider VPN");
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
            
            if (errorMsg.contains("UPnP") || errorMsg.contains("discover") || errorMsg.contains("mapping")) {
                networkErrorLabel.setText("UPnP port mapping failed:\n" + errorMsg + 
                                        "\n\nThis usually means:\n" +
                                        "• UPnP is disabled on your router\n" +
                                        "• Your router doesn't support UPnP\n" +
                                        "• Direct join is not possible on this network");
                networkErrorBox.setVisible(true);
                
                vpnReasonLabel.setText("Unable to automatically configure port mapping. UPnP may be disabled or unsupported.");
                vpnFallbackBox.setVisible(true);
            } else {
                networkErrorLabel.setText("Network configuration failed: " + errorMsg);
                networkErrorBox.setVisible(true);
            }
            
            networkResultBox.setVisible(false);
            
            appendToConsole.accept("[NETWORK] External join setup failed: " + errorMsg);
        });
        
        new Thread(task, "network-setup").start();
    }

    public void disableExternalJoin() {
        if (currentNetworkConfig != null && currentNetworkConfig.mappingActive()) {
            boolean removed = NetworkService.removePortMapping(
                currentNetworkConfig.igdControlUrl(),
                currentNetworkConfig.igdServiceType(),
                currentNetworkConfig.externalPort()
            );
            
            if (removed) {
                appendToConsole.accept("[NETWORK] Port mapping removed successfully");
                UiUtil.showInfo("External Join Disabled", "Port mapping has been removed from your router.");
            } else {
                appendToConsole.accept("[NETWORK] Warning: Failed to remove port mapping (may have expired)");
                UiUtil.showInfo("External Join Disabled", "Port mapping removal attempted (may need manual cleanup).");
            }
            
            currentNetworkConfig = null;
        }
        
        enableExternalJoinBtn.setDisable(false);
        disableExternalJoinBtn.setDisable(true);
        networkResultBox.setVisible(false);
        networkErrorBox.setVisible(false);
        vpnFallbackBox.setVisible(false);
        vpnHelpBox.setVisible(false);
    }

    public void copyJoinAddress() {
        if (joinAddressField.getText() != null && !joinAddressField.getText().isEmpty()) {
            java.awt.datatransfer.StringSelection selection = 
                new java.awt.datatransfer.StringSelection(joinAddressField.getText());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            UiUtil.showInfo("Copied", "Join address copied to clipboard!");
        }
    }

    public void toggleVpnHelp() {
        vpnHelpBox.setVisible(!vpnHelpBox.isVisible());
    }

    public void cleanupNetworkMapping() {
        if (currentNetworkConfig != null && currentNetworkConfig.mappingActive()) {
            NetworkService.removePortMapping(
                currentNetworkConfig.igdControlUrl(),
                currentNetworkConfig.igdServiceType(),
                currentNetworkConfig.externalPort()
            );
        }
    }
}

