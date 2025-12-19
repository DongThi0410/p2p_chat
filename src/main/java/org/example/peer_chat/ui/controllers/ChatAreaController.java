package org.example.peer_chat.ui.controllers;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.sound.sampled.LineUnavailableException;

import org.example.peer_chat.CallRecord;
import org.example.peer_chat.ChatDb;
import org.example.peer_chat.ChatItem;
import org.example.peer_chat.Message;
import org.example.peer_chat.PeerHandle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ChatAreaController {

    @FXML private TextField messageField;
    @FXML private VBox messagesBox;
    @FXML private Button sendButton;
    @FXML private Button voiceButton;
    @FXML private Button attachButton;
    @FXML private Button imageButton;
    @FXML private Label contactName;
    @FXML private Label contactStatus;
    @FXML private Label contactAvatar;
    @FXML private javafx.scene.control.ScrollPane messageScrollPane;

    // Root layout cho chat và placeholder
    @FXML private javafx.scene.layout.BorderPane chatRootPane;
    @FXML private VBox placeholderRoot;

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

        // Khi đã chọn một contact: ẩn placeholder, hiện khu vực chat chính
        if (chatRootPane != null) {
            chatRootPane.setVisible(true);
            chatRootPane.setManaged(true);
        }
        if (placeholderRoot != null) {
            placeholderRoot.setVisible(false);
            placeholderRoot.setManaged(false);
        }

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
                if (msg.isFile()) {
                    // Đây là file message - cần hiển thị như file/voice bubble
                    String filename = msg.getContent(); // content chứa tên file
                    String filePath = msg.getFilePath();
                    long size = 0;
                    if (filePath != null) {
                        File f = new File(filePath);
                        if (f.exists()) size = f.length();
                    }
                    // Sử dụng onIncomingFile để hiển thị đúng dạng (tự động phân biệt voice/image/file)
                    displayFileMessage(msg.getFromUser(), filename, filePath, size);
                } else {
                    // Tin nhắn text thường
                    appendMessage(msg);
                }
            } else if (item instanceof CallRecord cr) {
                appendCallRecord(cr);
            }
        }

        // Scroll xuống cuối sau khi load xong
        scrollToBottom();
    }

    /**
     * Scroll xuống cuối danh sách tin nhắn
     */
    private void scrollToBottom() {
        if (messageScrollPane != null) {
            // Delay nhỏ để đảm bảo layout đã được tính toán
            Platform.runLater(() -> {
                messageScrollPane.setVvalue(1.0);
            });
        }
    }

    /**
     * Hiển thị file message khi load từ database (không gửi lại qua network)
     */
    private void displayFileMessage(String sender, String filename, String filePath, long size) {
        // Kiểm tra nếu là voice message
        if (isVoiceFile(filename)) {
            int estimatedDuration = (int) Math.max(1, size / 88200);
            displayVoiceBubble(sender, filename, filePath, estimatedDuration);
            return;
        }

        boolean isSent = sender.equals(currentUser);
        boolean isImage = isImageFile(filename);

        // Tạo file bubble container
        VBox fileBubble = new VBox(6);
        fileBubble.getStyleClass().add(isSent ? "file-bubble-sent" : "file-bubble-received");

        if (isImage && filePath != null && new File(filePath).exists()) {
            // Hiển thị ảnh preview
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(new File(filePath).toURI().toString(), 200, 200, true, true);
                if (!img.isError()) {
                    javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(img);
                    imageView.setPreserveRatio(true);
                    imageView.setFitWidth(200);
                    imageView.getStyleClass().add("file-image-preview");
                    imageView.setOnMouseClicked(e -> openFile(filePath));
                    imageView.setCursor(javafx.scene.Cursor.HAND);
                    fileBubble.getChildren().add(imageView);
                } else {
                    addFileInfo(fileBubble, filename, size, filePath);
                }
            } catch (Exception ex) {
                addFileInfo(fileBubble, filename, size, filePath);
            }
        } else {
            addFileInfo(fileBubble, filename, size, filePath);
        }

        HBox row = new HBox(fileBubble);
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getStyleClass().add("chat-row");
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    /**
     * Hiển thị voice bubble khi load từ database
     */
    private void displayVoiceBubble(String sender, String filename, String filePath, int durationSeconds) {
        boolean isSent = sender.equals(currentUser);

        VBox voiceBubble = new VBox(8);
        voiceBubble.getStyleClass().add(isSent ? "voice-bubble-sent" : "voice-bubble-received");

        HBox waveformRow = new HBox(8);
        waveformRow.setAlignment(Pos.CENTER_LEFT);

        Label playIcon = new Label("▶️");
        playIcon.getStyleClass().add("voice-play-icon");
        playIcon.setCursor(javafx.scene.Cursor.HAND);
        playIcon.setOnMouseClicked(e -> playVoiceMessage(filePath, playIcon));

        HBox waveform = new HBox(2);
        waveform.setAlignment(Pos.CENTER);
        for (int i = 0; i < 20; i++) {
            javafx.scene.shape.Rectangle bar = new javafx.scene.shape.Rectangle(3, 5 + Math.random() * 15);
            bar.getStyleClass().add("voice-wave-bar");
            bar.setArcWidth(2);
            bar.setArcHeight(2);
            waveform.getChildren().add(bar);
        }

        waveformRow.getChildren().addAll(playIcon, waveform);

        int mins = durationSeconds / 60;
        int secs = durationSeconds % 60;
        Label durationLabel = new Label(String.format("%02d:%02d", mins, secs));
        durationLabel.getStyleClass().add("voice-duration");

        voiceBubble.getChildren().addAll(waveformRow, durationLabel);

        HBox row = new HBox(voiceBubble);
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getStyleClass().add("chat-row");
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    @FXML
    private void onAttachFile() {
        if (peer == null || selectedContact == null || chatDb == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file để gửi");
        File f = chooser.showOpenDialog(null);
        if (f == null) return;

        new Thread(() -> {
            try {
                peer.sendFileByName(selectedContact, f.getAbsolutePath());
                chatDb.insertMessage(new Message(currentUser, selectedContact, f.getName(), true, f.getAbsolutePath()));
                long size = f.length();
                Platform.runLater(() -> onIncomingFile(currentUser, f.getName(), f.getAbsolutePath(), size));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "send-file-thread").start();
    }

    @FXML
    private void onSendImage() {
        if (peer == null || selectedContact == null || chatDb == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh để gửi");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = chooser.showOpenDialog(null);
        if (f == null) return;

        new Thread(() -> {
            try {
                peer.sendFileByName(selectedContact, f.getAbsolutePath());
                chatDb.insertMessage(new Message(currentUser, selectedContact, f.getName(), true, f.getAbsolutePath()));
                long size = f.length();
                Platform.runLater(() -> onIncomingFile(currentUser, f.getName(), f.getAbsolutePath(), size));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "send-image-thread").start();
    }
    private void appendCallRecord(CallRecord cr) {
        String label = cr.isVideo() ? "📹 Cuộc gọi video" : "📞 Cuộc gọi thoại";
        label += cr.isSuccess() ? " thành công" : " bị từ chối";
        label += " (" + cr.getDuration() + " giây)";

        Label bubble = new Label(label);
        bubble.setWrapText(true);
        bubble.getStyleClass().add("bubble-call");

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("chat-row");

        messagesBox.getChildren().add(row);
        scrollToBottom();
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
        scrollToBottom();
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
        if (peer == null || selectedContact == null || chatDb == null) return;

        // Open voice recorder modal
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/voice-recorder.fxml"));
            Parent root = loader.load();

            VoiceRecorderController controller = loader.getController();
            controller.init(currentUser, selectedContact, (filePath, durationSeconds) -> {
                // Callback khi voice message được gửi
                sendVoiceMessage(filePath, durationSeconds);
            });

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Ghi âm tin nhắn thoại");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendVoiceMessage(String filePath, int durationSeconds) {
        if (peer == null || selectedContact == null || chatDb == null) return;

        File voiceFile = new File(filePath);
        if (!voiceFile.exists()) return;

        new Thread(() -> {
            try {
                peer.sendFileByName(selectedContact, filePath);

                // Lưu vào DB với metadata thời gian
                String message = voiceFile.getName();
                chatDb.insertMessage(new Message(currentUser, selectedContact, message, true, filePath));

                long size = voiceFile.length();
                Platform.runLater(() -> onIncomingVoice(currentUser, voiceFile.getName(), filePath, size, durationSeconds));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "send-voice-thread").start();
    }

    public void onIncomingVoice(String sender, String filename, String absolutePath, long size, int durationSeconds) {
        if (selectedContact == null) return;
        if (!sender.equals(selectedContact) && !sender.equals(currentUser)) return;

        boolean isSent = sender.equals(currentUser);

        // Tạo voice bubble container
        VBox voiceBubble = new VBox(8);
        voiceBubble.getStyleClass().add(isSent ? "voice-bubble-sent" : "voice-bubble-received");

        // Icon loa + waveform
        HBox waveformRow = new HBox(8);
        waveformRow.setAlignment(Pos.CENTER_LEFT);

        Label playIcon = new Label("▶️");
        playIcon.getStyleClass().add("voice-play-icon");
        playIcon.setCursor(javafx.scene.Cursor.HAND);
        playIcon.setOnMouseClicked(e -> playVoiceMessage(absolutePath, playIcon));

        // Waveform visual
        HBox waveform = new HBox(2);
        waveform.setAlignment(Pos.CENTER);
        for (int i = 0; i < 20; i++) {
            javafx.scene.shape.Rectangle bar = new javafx.scene.shape.Rectangle(3, 5 + Math.random() * 15);
            bar.getStyleClass().add("voice-wave-bar");
            bar.setArcWidth(2);
            bar.setArcHeight(2);
            waveform.getChildren().add(bar);
        }

        waveformRow.getChildren().addAll(playIcon, waveform);

        // Duration
        int mins = durationSeconds / 60;
        int secs = durationSeconds % 60;
        Label durationLabel = new Label(String.format("%02d:%02d", mins, secs));
        durationLabel.getStyleClass().add("voice-duration");

        voiceBubble.getChildren().addAll(waveformRow, durationLabel);

        // Wrap trong HBox để căn lề
        HBox row = new HBox(voiceBubble);
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getStyleClass().add("chat-row");
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void playVoiceMessage(String filePath, Label playIcon) {
        new Thread(() -> {
            try {
                File audioFile = new File(filePath);
                if (!audioFile.exists()) return;

                Platform.runLater(() -> playIcon.setText("⏸️"));

                javax.sound.sampled.AudioInputStream audioStream =
                        javax.sound.sampled.AudioSystem.getAudioInputStream(audioFile);
                javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
                clip.open(audioStream);

                clip.addLineListener(event -> {
                    if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                        Platform.runLater(() -> playIcon.setText("▶️"));
                        clip.close();
                    }
                });

                clip.start();

            } catch (Exception e) {
                Platform.runLater(() -> playIcon.setText("❌"));
                e.printStackTrace();
            }
        }, "play-voice-thread").start();
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
        System.out.println("[ChatArea] onIncomingFile: sender=" + sender + ", filename=" + filename + ", path=" + absolutePath);
        System.out.println("[ChatArea] currentUser=" + currentUser + ", selectedContact=" + selectedContact);

        // Cho phép hiển thị nếu sender là selectedContact (file nhận) hoặc currentUser (file gửi đi)
        if (selectedContact == null) {
            System.out.println("[ChatArea] selectedContact is null, skipping");
            return;
        }
        if (!sender.equals(selectedContact) && !sender.equals(currentUser)) {
            System.out.println("[ChatArea] sender doesn't match, skipping. sender=" + sender);
            return;
        }

        // Kiểm tra nếu là voice message (file voice_*.wav)
        if (isVoiceFile(filename)) {
            // Ước tính duration từ file size (44100Hz, 16bit, mono = ~88KB/giây)
            int estimatedDuration = (int) Math.max(1, size / 88200);
            onIncomingVoice(sender, filename, absolutePath, size, estimatedDuration);
            return;
        }

        boolean isSent = sender.equals(currentUser);
        boolean isImage = isImageFile(filename);
        System.out.println("[ChatArea] isSent=" + isSent + ", isImage=" + isImage);

        // Tạo file bubble container
        VBox fileBubble = new VBox(6);
        fileBubble.getStyleClass().add(isSent ? "file-bubble-sent" : "file-bubble-received");

        if (isImage && absolutePath != null && new File(absolutePath).exists()) {
            // Hiển thị ảnh preview
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(new File(absolutePath).toURI().toString(), 200, 200, true, true);
                if (img.isError()) {
                    System.out.println("[ChatArea] Image load error, falling back to file info");
                    addFileInfo(fileBubble, filename, size, absolutePath);
                } else {
                    javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(img);
                    imageView.setPreserveRatio(true);
                    imageView.setFitWidth(200);
                    imageView.getStyleClass().add("file-image-preview");
                    imageView.setOnMouseClicked(e -> openFile(absolutePath));
                    imageView.setCursor(javafx.scene.Cursor.HAND);
                    fileBubble.getChildren().add(imageView);
                }
            } catch (Exception ex) {
                System.out.println("[ChatArea] Exception loading image: " + ex.getMessage());
                // Fallback to file icon if image can't be loaded
                addFileInfo(fileBubble, filename, size, absolutePath);
            }
        } else {
            // Hiển thị file với icon
            addFileInfo(fileBubble, filename, size, absolutePath);
        }

        // Wrap trong HBox để căn lề
        HBox row = new HBox(fileBubble);
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.getStyleClass().add("chat-row");
        messagesBox.getChildren().add(row);
        System.out.println("[ChatArea] File bubble added to messagesBox");
        scrollToBottom();
    }

    private void addFileInfo(VBox container, String filename, long size, String absolutePath) {
        // Icon và tên file
        String fileIcon = getFileIcon(filename);
        Label iconLabel = new Label(fileIcon);
        iconLabel.getStyleClass().add("file-icon");

        Label nameLabel = new Label(filename);
        nameLabel.getStyleClass().add("file-name");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(180);

        HBox fileHeader = new HBox(8, iconLabel, nameLabel);
        fileHeader.setAlignment(Pos.CENTER_LEFT);

        // Size file
        Label sizeLabel = new Label(formatFileSize(size));
        sizeLabel.getStyleClass().add("file-size");

        // Nút mở file
        Button openBtn = new Button("Mở 📂");
        openBtn.getStyleClass().add("file-open-btn");
        openBtn.setOnAction(e -> openFile(absolutePath));

        container.getChildren().addAll(fileHeader, sizeLabel, openBtn);
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private boolean isVoiceFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.startsWith("voice_") && lower.endsWith(".wav");
    }

    private String getFileIcon(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "📕";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "📘";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "📗";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "📙";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "🗜️";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")) return "🎵";
        if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")) return "🎬";
        if (lower.endsWith(".txt")) return "📝";
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js")) return "💻";
        return "📄";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void openFile(String absolutePath) {
        try {
            java.awt.Desktop.getDesktop().open(new File(absolutePath));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
            // Bên gọi: vừa hiển thị video local, vừa chuẩn bị nhận video remote
            controller.initVideoCall(peer, selectedContact);
            controller.setOutgoingCall(selectedContact);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Gọi video với " + selectedContact);
            // Video call rộng hơn voice call ~2x
            stage.setScene(new Scene(root, 800, 520));

            // Lưu reference để có thể cập nhật sau
            activeVideoCallStage = stage;
            activeVideoCallController = controller;

            // Đóng window khi user đóng
            stage.setOnCloseRequest(e -> {
                if (activeVideoCallController != null) {
                    activeVideoCallController.stopVideoCapture();
                }
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
                    // Bên nhận: cấu hình đầy đủ video (local + remote)
                    controller.initVideoCall(peer, peerName);
                    controller.showInCall(peerName);

                    Stage stage = new Stage();
                    stage.initModality(Modality.APPLICATION_MODAL);
                    stage.setTitle("Cuộc gọi video với " + peerName);
                    // Video call rộng hơn voice call ~2x
                    stage.setScene(new Scene(root, 800, 520));

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
                    // Bên nhận: khởi tạo voice call với PeerHandle và trạng thái đang trong cuộc gọi
                    controller.init(peer, peerName, true);

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
