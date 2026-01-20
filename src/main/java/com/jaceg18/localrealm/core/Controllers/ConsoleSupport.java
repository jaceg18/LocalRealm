package com.jaceg18.localrealm.core.Controllers;

import com.jaceg18.localrealm.core.service.ServerService;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;

public final class ConsoleSupport {

    public void handleEnterToSend(KeyEvent event,
                           TextArea area,
                           ServerService serverService,
                           Consumer<String> feedbackAppender) {
        if (event.getCode() != KeyCode.ENTER) return;

        String command = lastLine(area.getText());
        if (!command.isEmpty() && serverService.isServerRunning()) {
            serverService.sendCommand(command);
            event.consume();
        }
    }

    static String lastLine(String text) {
        String[] lines = text.split("\n", -1);
        return (lines.length > 0) ? lines[lines.length - 1].trim() : "";
    }
}