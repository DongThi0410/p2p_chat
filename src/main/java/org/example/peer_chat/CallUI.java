package org.example.peer_chat;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CallUI {

    public static void showIncomingCall(String callerName, Runnable onAccept, Runnable onReject) {

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("📞 Incoming Call");

        Label label = new Label("Cuộc gọi đến từ: " + callerName);
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button acceptBtn = new Button("Chấp nhận");
        acceptBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14;");
        acceptBtn.setOnAction(e -> {
            stage.close();
            onAccept.run();
        });

        Button rejectBtn = new Button("Từ chối");
        rejectBtn.setStyle("-fx-background-color: #E53935; -fx-text-fill: white; -fx-font-size: 14;");
        rejectBtn.setOnAction(e -> {
            stage.close();
            onReject.run();
        });

        VBox root = new VBox(20, label, acceptBtn, rejectBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 300, 200);
        stage.setScene(scene);
        stage.show();
    }
}
