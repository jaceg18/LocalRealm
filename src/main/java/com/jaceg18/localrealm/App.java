package com.jaceg18.localrealm;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    public static final String VERSION = "v1.0";

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