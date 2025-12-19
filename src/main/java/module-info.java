module org.example.peer_chat {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;
    requires java.sql;
    requires java.desktop;
    requires testfx.core;
    requires junit;
    // webcam-capture: automatic module name derived from JAR file name
    requires webcam.capture;
    requires javafx.graphics;

    opens org.example.peer_chat to javafx.fxml, testfx.core;
    opens org.example.peer_chat.ui.controllers to javafx.fxml, webcam.capture;

    exports org.example.peer_chat;
}