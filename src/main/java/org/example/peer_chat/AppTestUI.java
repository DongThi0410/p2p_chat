package org.example.peer_chat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.net.URL;

import org.example.peer_chat.ChatDb;
import org.example.peer_chat.PeerHandle;
import org.example.peer_chat.ui.controllers.LoginController;
import org.example.peer_chat.ui.controllers.MainController;

public class AppTestUI extends Application {

    private Stage stage;
    private Parent root;
    private ChatDb chatDb;

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.stage = primaryStage;

        // init shared chat database once
        this.chatDb = new ChatDb("chat_history.db");

        // Load Login FXML
        URL fxmlUrl = getClass().getResource("/ui/login-view.fxml");
        if (fxmlUrl == null) {
            // Try alternative path for test context
            fxmlUrl = AppTestUI.class.getClassLoader().getResource("ui/login-view.fxml");
            if (fxmlUrl == null) {
                throw new IOException("Cannot find login-view.fxml. Check if the file exists in src/main/resources/ui/");
            }
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        root = loader.load(); // Load the FXML file

        // Wire login success -> open main view
        LoginController loginController = loader.getController();
        loginController.setChatDb(chatDb);
        loginController.setOnLogin(this::openMainView);

        Scene scene = new Scene(root, 1000, 700);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Navigate to main chat UI after login.
     */
    private void openMainView(String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main-view.fxml"));
            Parent mainRoot = loader.load();

            MainController mainController = loader.getController();
            // create a new PeerHandle for this logged-in user
            PeerHandle peer = new PeerHandle(username, chatDb);

            mainController.init(peer, username, chatDb);
            mainController.setOnLogoutCallback(() -> {
                // Xử lý peer offline nếu cần
                if (peer != null) peer.removePeer(username);

                // Quay về login
                Platform.runLater(() -> {
                    try {
                        showLoginScene();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            });
            Scene scene = new Scene(mainRoot, 1000, 700);
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            // any lower-level init error (e.g. audio) just log for now
            e.printStackTrace();
        }
    }
    private void showLoginScene() throws IOException {
        URL fxmlUrl = getClass().getResource("/ui/login-view.fxml");
        if (fxmlUrl == null) {
            fxmlUrl = AppTestUI.class.getClassLoader().getResource("ui/login-view.fxml");
            if (fxmlUrl == null) throw new IOException("Cannot find login-view.fxml");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        LoginController loginController = loader.getController();
        loginController.setChatDb(chatDb);
        loginController.setOnLogin(this::openMainView);

        stage.setScene(new Scene(root, 1000, 700));
        stage.centerOnScreen();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
