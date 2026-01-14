package com.jaceg18.localrealm;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

/*
 TODO Fix versioning by allowing users to add, remove, and select other build options.
 TODO Add plugin and mod-list management
 TODO Add live server stats, including ram usage, cpu usage, player count
 TODO Allow editing of server properties and files within the UI.
 TODO Add server restarting button, 'duh', no one likes clicking stop than start.
 TODO Allow for periodic backups of selectable files and data.
 TODO Add 'non-hidden' outside the application server running.
 TODO Add icons and beautify the UI
 TODO Add socials, dev-log links, and donation links.

 // Just a few of many plans, there are significant improvements to be expected before a non-snapshot version is shipped.
 */

public class App extends Application {

    public static final String VERSION = "1.1-SNAPSHOT";

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("view.fxml"));
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());
        var iconStream = App.class.getResourceAsStream("/icons/localrealm.png");
        if (iconStream == null) {
            throw new IllegalStateException("Icon not found: /icons/localrealm.png");
        }
        stage.getIcons().add(new Image(iconStream));

        stage.setTitle("LocalRealm " + VERSION);
        stage.setOnHidden(e -> {Platform.exit();System.exit(0);});
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}