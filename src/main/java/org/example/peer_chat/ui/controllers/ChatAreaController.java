package org.example.peer_chat.ui.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.peer_chat.*;
import org.example.peer_chat.ui.controllers.VideoCallModalController;
import org.example.peer_chat.ui.controllers.InfoPanelController;
import org.example.peer_chat.ui.controllers.ReceiveCallController;
import org.example.peer_chat.ui.controllers.VoiceCallController;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.io.File;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChatAreaController {

    @FXML private TextField messageField;
    @FXML private VBox messagesBox;
    @FXML private Button sendButton;
    @FXML private Button voiceButton;
    @FXML private Label contactName;
    @FXML private Label contactStatus;
    @FXML private Label contactAvatar;

    // Root of embedded info panel (fx:include)
    @FXML private StackPane infoPanelRoot; // root StackPane from fx:include
    @FXML private InfoPanelController infoPanelRootController; // auto-wired: fx:id + "Controller"

    private PeerHandle peer;
    private ChatDb chatDb;
    private String currentUser;
    private String selectedContact;
    
    // Lưu reference đến VoiceCall window để cập nhật khi call được accept
    private Stage activeVoiceCallStage;
    private VoiceCallController activeVoiceCallController;
    
    // Lưu reference đến VideoCall window để cập nhật khi call được accept
    private Stage activeVideoCallStage;
    private VideoCallModalController activeVideoCallController;
    private boolean isCurrentCallVideo = false; // Track loại call hiện tại
    

    public void setCurrentCallVideo(boolean isVideo) {
        this.isCurrentCallVideo = isVideo;
    }

    public void closeCallWindows() {
        if (activeVoiceCallStage != null && activeVoiceCallStage.isShowing()) {
            activeVoiceCallStage.close();
            activeVoiceCallStage = null;
            activeVoiceCallController = null;
        }
        if (activeVideoCallStage != null && activeVideoCallStage.isShowing()) {
            activeVideoCallStage.close();
            activeVideoCallStage = null;
            activeVideoCallController = null;
        }
        isCurrentCallVideo = false;
    }

    public void init(PeerHandle peer, String currentUser, String selectedContact, ChatDb chatDb) {
        this.peer = peer;
        this.currentUser = currentUser;
        this.selectedContact = selectedContact;
        this.chatDb = chatDb;

        contactName.setText(selectedContact);
        contactStatus.setText("Online");
        contactAvatar.setText("🐱");

        loadMessages();
    }

    private void loadMessages() {
        messagesBox.getChildren().clear();

        if (chatDb == null || currentUser == null || selectedContact == null) return;

        List<ChatItem> items = new ArrayList<>();

        // Load tin nhắn
        items.addAll(chatDb.loadConversationAsc(currentUser, selectedContact, 500));

        // Load lịch sử cuộc gọi
        items.addAll(chatDb.loadCallHistory(currentUser, selectedContact, 100));

        // Sắp xếp theo timestamp
        items.sort(Comparator.comparingLong(ChatItem::getTimestamp));

        // Render
        for (ChatItem item : items) {
            if (item instanceof Message msg) {
                appendMessage(msg);
            } else if (item instanceof CallRecord cr) {
                appendCallRecord(cr);
            }
        }
    }
    private void appendCallRecord(CallRecord cr) {
        String label ="📞 Cuộc gọi thoại";
        label += cr.isSuccess() ? " thành công" : " bị từ chối";
        label += " (" + cr.getDuration() + " giây)";

        Label bubble = new Label(label);
        bubble.setWrapText(true);
        bubble.getStyleClass().add("bubble-call");

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("chat-row");

        messagesBox.getChildren().add(row);
    }

    private void appendMessage(Message msg) {
        boolean isSent = msg.getFromUser().equals(currentUser);

        Label bubble = new Label(msg.getContent());
        bubble.setWrapText(true);
        bubble.getStyleClass().add(isSent ? "bubble-sent" : "bubble-received");

        HBox row = new HBox(bubble);
        row.setFillHeight(false);
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getStyleClass().add("chat-row");

        messagesBox.getChildren().add(row);
    }

    @FXML
    private void onSendMessage() {
        String messageText = messageField.getText();
        if (!messageText.trim().isEmpty()) {
            // fromUser = currentUser, toUser = selectedContact, content = text, isFile = false, filePath = null
            Message msg = new Message(currentUser, selectedContact, messageText, false, null);
            appendMessage(msg);
            messageField.clear();
            if (peer != null) {
                peer.sendToByName(selectedContact, messageText);
            }
        }
    }

    @FXML
    private void onEnterPressed(KeyEvent event) {
        if (event.getCode().toString().equals("ENTER")) {
            onSendMessage();
        }
    }

    @FXML
    private void onStartVoiceMessage() {
        // Open voice recorder modal
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/voice-recorder.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Ghi âm");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public VoiceCallController getActiveVoiceCallController() {
        return activeVoiceCallController;
    }

    public VideoCallModalController getActiveVideoCallController() {
        return activeVideoCallController;
    }
    /**
     * Chỉ dùng cho gọi video: mở video-call-modal.fxml với VideoCallModalController.
     */
    private void openCallModal(String title, String type) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/video-call-modal.fxml"));
            Parent root = loader.load();

            VideoCallModalController controller = loader.getController();
            controller.init(type);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===== Callbacks from MainController / core layer =====

    public void onIncomingMessage(String sender, String msg) {
        if (selectedContact == null || !sender.equals(selectedContact)) return;
        Message m = new Message(sender, currentUser, msg, false, null);
        appendMessage(m);
    }

    public void onIncomingFile(String sender, String filename, String absolutePath, long size) {
        if (selectedContact == null || !sender.equals(selectedContact)) return;
        Message m = new Message(sender, currentUser, filename, true, absolutePath);

        Label linkLabel = new Label(filename);
        linkLabel.getStyleClass().add("file-link");
        linkLabel.setOnMouseClicked(e -> {
            try {
                java.awt.Desktop.getDesktop().open(new File(absolutePath));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        boolean isSent = sender.equals(currentUser);
        HBox row = new HBox(new Label((isSent ? "Me" : sender) + ": "), linkLabel);
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getStyleClass().add("chat-row");
        messagesBox.getChildren().add(row);
    }

    public void showIncomingCall(String callerName, String callerIp, int callerVoicePort, PeerHandle peerHandle) {
        // Bên B (người nhận): mở ReceiveCall.fxml để hiển thị "A đang gọi..." với nút Chấp nhận / Hủy
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/ReceiveCall.fxml"));
            Parent root = loader.load();

            ReceiveCallController controller = loader.getController();
            controller.init(peerHandle, callerName, callerIp, callerVoicePort);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Cuộc gọi đến từ " + callerName);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===== Header call buttons =====

    @FXML
    public void onCallVoice() throws SocketException, LineUnavailableException {
        if (peer != null && selectedContact != null) {
            peer.startVoiceCall(selectedContact);
        }
        // Bên A (người gọi): mở trực tiếp giao diện VoiceCall với trạng thái "Đang gọi..."
        openVoiceCallForCaller();
    }

    @FXML
    private void onCallVideo() throws SocketException, LineUnavailableException {
        if (peer != null && selectedContact != null) {
            peer.startVideoCall(selectedContact);
            isCurrentCallVideo = true; // Đánh dấu là video call
        }
        // Bên A (người gọi): mở trực tiếp giao diện VideoCall với trạng thái "Đang gọi..."
        openVideoCallForCaller();
    }

    @FXML
    private void onOpenInfoPanel() {
        // Khởi tạo dữ liệu cho panel mỗi lần mở
        if (infoPanelRootController != null) {
            infoPanelRootController.init(contactName.getText(), contactStatus.getText(), contactAvatar.getText(), 1);
        }

        infoPanelRoot.setVisible(true);
        infoPanelRoot.setManaged(true);
    }

    private void openVoiceCallForCaller() {
        if (peer == null || selectedContact == null) return;
        try {
            // Nếu đã có window đang mở, đóng nó trước
            if (activeVoiceCallStage != null && activeVoiceCallStage.isShowing()) {
                activeVoiceCallStage.close();
            }
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/voice-call-view.fxml"));
            Parent root = loader.load();

            VoiceCallController controller = loader.getController();
            controller.initOutgoing(peer, selectedContact);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Gọi thoại với " + selectedContact);
            stage.setScene(new Scene(root));
            
            // Lưu reference để có thể cập nhật sau
            activeVoiceCallStage = stage;
            activeVoiceCallController = controller;
            
            // Đóng window khi user đóng
            stage.setOnCloseRequest(e -> {
                activeVoiceCallStage = null;
                activeVoiceCallController = null;
            });
            
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void openVideoCallForCaller() {
        if (peer == null || selectedContact == null) return;
        try {
            // Nếu đã có window đang mở, đóng nó trước
            if (activeVideoCallStage != null && activeVideoCallStage.isShowing()) {
                activeVideoCallStage.close();
            }
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/video-call-modal.fxml"));
            Parent root = loader.load();

            VideoCallModalController controller = loader.getController();
            controller.init("video");
            controller.setPeerHandle(peer);
            controller.setOutgoingCall(selectedContact);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Gọi video với " + selectedContact);
            stage.setScene(new Scene(root));
            
            // Lưu reference để có thể cập nhật sau
            activeVideoCallStage = stage;
            activeVideoCallController = controller;
            
            // Đóng window khi user đóng
            stage.setOnCloseRequest(e -> {
                activeVideoCallStage = null;
                activeVideoCallController = null;
                isCurrentCallVideo = false;
            });
            
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Được gọi từ MainController khi call được accept (nhận CALL_ACCEPT).
     * Cập nhật UI từ "Đang gọi..." sang "Đang trong cuộc gọi" với timer.
     * Kiểm tra loại call (voice/video) để hiển thị UI phù hợp.
     */
    public void onCallAccepted(String peerName) {
        // Kiểm tra nếu đang có video call window mở
        if (activeVideoCallController != null && activeVideoCallStage != null && activeVideoCallStage.isShowing()) {
            // Cập nhật UI video call hiện tại
            activeVideoCallController.transitionToInCall();
        }
        // Kiểm tra nếu đang có voice call window mở
        else if (activeVoiceCallController != null && activeVoiceCallStage != null && activeVoiceCallStage.isShowing()) {
            // Cập nhật UI voice call hiện tại
            activeVoiceCallController.transitionToInCall();
        } 
        // Nếu không có window đang mở, kiểm tra loại call từ isCurrentCallVideo
        else {
            if (isCurrentCallVideo) {
                // Mở video call window mới (trường hợp bên B accept video call)
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/video-call-modal.fxml"));
                    Parent root = loader.load();

                    VideoCallModalController controller = loader.getController();
                    controller.init("video");
                    controller.setPeerHandle(peer);
                    controller.showInCall(peerName);

                    Stage stage = new Stage();
                    stage.initModality(Modality.APPLICATION_MODAL);
                    stage.setTitle("Cuộc gọi video với " + peerName);
                    stage.setScene(new Scene(root));
                    
                    activeVideoCallStage = stage;
                    activeVideoCallController = controller;
                    
                    stage.setOnCloseRequest(e -> {
                        activeVideoCallStage = null;
                        activeVideoCallController = null;
                        isCurrentCallVideo = false;
                    });
                    
                    stage.show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // Mở voice call window mới (trường hợp bên B accept voice call)
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/voice-call-view.fxml"));
                    Parent root = loader.load();

                    VoiceCallController controller = loader.getController();
                    controller.showInCall(peerName);

                    Stage stage = new Stage();
                    stage.initModality(Modality.APPLICATION_MODAL);
                    stage.setTitle("Cuộc gọi với " + peerName);
                    stage.setScene(new Scene(root));
                    
                    activeVoiceCallStage = stage;
                    activeVoiceCallController = controller;
                    
                    stage.setOnCloseRequest(e -> {
                        activeVoiceCallStage = null;
                        activeVoiceCallController = null;
                    });
                    
                    stage.show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Add other methods for handling voice messages or attachments as needed
}
