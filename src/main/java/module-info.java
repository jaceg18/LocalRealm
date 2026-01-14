module com.jaceg18.localrealm {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires java.net.http;

    opens com.jaceg18.localrealm to javafx.fxml;
    exports com.jaceg18.localrealm;
    exports com.jaceg18.localrealm.core;
    opens com.jaceg18.localrealm.core to javafx.fxml;
    exports com.jaceg18.localrealm.core.build;
    opens com.jaceg18.localrealm.core.build to javafx.fxml;
}