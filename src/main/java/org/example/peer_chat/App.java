package org.example.peer_chat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.sound.sampled.LineUnavailableException;


public class App extends Application {

    private PeerHandle peer;
    private String myName;

    private VBox messagesBox;
    private TextField msgField;
    private ScrollPane scrollPane;
    private ListView<String> peerListView;

    ChatDb db = new ChatDb("chat_history.db");

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        showLogin(stage);
    }

    private void showLogin(Stage stage) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label l = new Label("Enter your name:");
        TextField nameField = new TextField();
        nameField.setPromptText("alice");

        Button login = new Button("Start");
        Label status = new Label();

        root.getChildren().addAll(l, nameField, login, status);

        login.setOnAction(e -> {
            String n = nameField.getText().trim();
            if (n.isEmpty()) { status.setText("Enter name"); return; }
            try {
                peer = new PeerHandle(n, db);
            } catch (IOException | LineUnavailableException ex) {
                status.setText("Init error: " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            myName = n;

            peer.setListener(new MessageListener() {
                @Override
                public void onMessage(String sender, String msg) {
                    Platform.runLater(() -> appendMessage(sender + ": " + msg));
                }

                @Override
                public void onFileReceived(String sender, String filename, String absolutePath, long size) {
                    Platform.runLater(() -> appendMessage(sender + ": 📂 " + filename));
                }

                @Override
                public void onIncomingCall(String callerName, String callerIp, int callerVoicePort) {
                    Platform.runLater(() -> showIncomingCallPopup(callerName, callerIp, callerVoicePort));
                }
            });

            showChatScreen(stage);
        });

        Scene s = new Scene(root, 360, 200);
        stage.setScene(s);
        stage.setTitle("P2P Voice Chat - Login");
        stage.show();
    }

    private void showChatScreen(Stage loginStage) {
        loginStage.close();

        Stage primaryStage = new Stage();
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        // left: peers
        peerListView = new ListView<>();
        peerListView.setPrefWidth(200);
        root.setLeft(peerListView);

        // center: messages
        messagesBox = new VBox(6);
        messagesBox.setPadding(new Insets(6));
        scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        root.setCenter(scrollPane);

        // bottom: controls
        HBox bottom = new HBox(8);
        bottom.setPadding(new Insets(6));
        bottom.setAlignment(Pos.CENTER_LEFT);

        msgField = new TextField();
        msgField.setPrefWidth(420);
        msgField.setPromptText("Message");
        msgField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) sendText();
        });

        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> sendText());

        Button fileBtn = new Button("Send File");
        fileBtn.setOnAction(e -> sendFile());

        Button callBtn = new Button("Call");
        callBtn.setOnAction(e -> {
            String t = peerListView.getSelectionModel().getSelectedItem();
            if (t == null) appendMessage("[Error] select peer");
            else peer.startVoiceCall(t);
        });

        Button hang = new Button("Hangup");
        hang.setOnAction(e -> peer.stopVoiceCall());

        Button refresh = new Button("Refresh");
        refresh.setOnAction(this::refreshPeerList);

        bottom.getChildren().addAll(msgField, sendBtn, fileBtn, callBtn, hang, refresh);
        root.setBottom(bottom);

        // selection loads history
        peerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) loadHistoryForPeer(newV);
        });

        Scene s = new Scene(root, 1000, 650);
        primaryStage.setScene(s);
        primaryStage.setTitle("P2P Voice Chat - " + myName);
        primaryStage.show();

        startAutoRefresh();
    }

    private void appendMessage(String txt) {
        Label lbl = new Label(txt);
        lbl.setWrapText(true);
        messagesBox.getChildren().add(lbl);
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void sendText() {
        String target = peerListView.getSelectionModel().getSelectedItem();
        String msg = msgField.getText().trim();
        if (target == null || msg.isEmpty()) {
            appendMessage("[Error] select peer and type message");
            return;
        }

        new Thread(() -> {
            peer.sendToByName(target, msg);
            db.insertMessage(new Message(myName, target, msg, false, null));
            Platform.runLater(() -> appendMessage("Me: " + msg));
            Platform.runLater(() -> msgField.clear());
        }).start();
    }

    private void sendFile() {
        String target = peerListView.getSelectionModel().getSelectedItem();
        if (target == null) { appendMessage("[Error] select peer"); return; }

        FileChooser chooser = new FileChooser();
        File f = chooser.showOpenDialog(null);
        if (f == null) return;

        new Thread(() -> {
            peer.sendFileByName(target, f.getAbsolutePath());
            db.insertMessage(new Message(myName, target, f.getName(), true, f.getAbsolutePath()));
            Platform.runLater(() -> appendMessage("Me: Sent file " + f.getName()));
        }).start();
    }

    private void loadHistoryForPeer(String peerName) {
        new Thread(() -> {
            var msgs = db.loadConversationAsc(myName, peerName, 500);
            Platform.runLater(() -> {
                messagesBox.getChildren().clear();
                for (Message m : msgs) {
                    String s = m.getFromUser().equals(myName) ? "Me" : m.getFromUser();
                    if (m.isFile()) {
                        Hyperlink link = new Hyperlink(m.getFilePath());
                        link.setOnAction(ev -> {
                            try { java.awt.Desktop.getDesktop().open(new File(m.getFilePath())); }
                            catch (Exception ex) { ex.printStackTrace(); }
                        });
                        HBox row = new HBox(6, new Label(s + ":"), link);
                        messagesBox.getChildren().add(row);
                    } else {
                        messagesBox.getChildren().add(new Label(s + ": " + m.getContent()));
                    }
                }
            });
        }).start();
    }

    private void refreshPeerList(javafx.event.ActionEvent e) {
        List<String> peers = peer.getPeerList();
        Platform.runLater(() -> {
            String selected = peerListView.getSelectionModel().getSelectedItem();
            peerListView.getItems().setAll(peers);
            if (selected != null && peers.contains(selected)) peerListView.getSelectionModel().select(selected);
        });
    }

    private void startAutoRefresh() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1500);
                    List<String> peers = peer.getPeerList();
                    Platform.runLater(() -> {
                        String sel = peerListView.getSelectionModel().getSelectedItem();
                        peerListView.getItems().setAll(peers);
                        if (sel != null && peers.contains(sel)) peerListView.getSelectionModel().select(sel);
                    });
                } catch (InterruptedException ignored) {}
            }
        }, "ui-peer-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void showIncomingCallPopup(String callerName, String callerIp, int callerVoicePort) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Incoming Call");

        Label label = new Label("📞 Incoming call from " + callerName);
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button accept = new Button("Accept");
        Button reject = new Button("Reject");

        accept.setOnAction(e -> {
            popup.close();
            // accept: notify PeerHandle to accept (sends CALL_ACCEPT back) and start voice engine locally
            peer.acceptCall(callerName, callerIp, callerVoicePort);
        });

        reject.setOnAction(e -> {
            popup.close();
            // simple reject: do nothing (could send CALL_REJECT)
        });

        HBox buttons = new HBox(10, accept, reject);
        buttons.setAlignment(Pos.CENTER);

        VBox root = new VBox(16, label, buttons);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        popup.setScene(new Scene(root, 320, 150));
        popup.show();
    }

    @Override
    public void stop() throws Exception {
        if (peer != null) peer.shutdown();
        super.stop();
    }
}
