package org.example.peer_chat.ui.controllers;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.sound.sampled.LineUnavailableException;

import javafx.geometry.Insets;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
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

    @FXML
    private TextField messageField;
    @FXML
    private VBox messagesBox;
    @FXML
    private Label contactName;
    @FXML
    private Label contactStatus;
    @FXML
    private Label contactAvatar;
    @FXML
    private javafx.scene.control.ScrollPane messageScrollPane;
    @FXML
    private Button renameButton;
    @FXML
    private Button manageMembersButton;
    @FXML
    private Button leaveButton;

    // Root layout cho chat và placeholder
    @FXML
    private javafx.scene.layout.BorderPane chatRootPane;
    @FXML
    private VBox placeholderRoot;

    // Root of embedded info panel (fx:include)
    @FXML
    private StackPane infoPanelRoot; // root StackPane from fx:include
    @FXML
    private InfoPanelController infoPanelRootController; // auto-wired: fx:id + "Controller"

    private PeerHandle peer;
    private ChatDb chatDb;
    private String currentUser;
    private String selectedContact; // peer chat
    private String currentGroupId; // group chat (nếu != null thì đang ở chế độ group)
    private String currentGroupName;

    // Lưu reference đến VoiceCall window để cập nhật khi call được accept
    private Stage activeVoiceCallStage;
    private VoiceCallController activeVoiceCallController;

    // Lưu reference đến VideoCall window để cập nhật khi call được accept
    private Stage activeVideoCallStage;
    private VideoCallModalController activeVideoCallController;
    private boolean isCurrentCallVideo = false; // Track loại call hiện tại
    private void updateGroupActionButtons(boolean isGroup, boolean isOwner) {

        // Rename + Manage Members: chỉ owner mới thấy
        if (renameButton != null) {
            renameButton.setVisible(isGroup && isOwner);
            renameButton.setManaged(isGroup && isOwner);
        }

        if (manageMembersButton != null) {
            manageMembersButton.setVisible(isGroup && isOwner);
            manageMembersButton.setManaged(isGroup && isOwner);
        }

        // Leave: mọi member đều thấy khi là group
        if (leaveButton != null) {
            leaveButton.setVisible(isGroup);
            leaveButton.setManaged(isGroup);
        }
    }

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
        this.currentGroupId = null;
        this.currentGroupName = null;
        this.chatDb = chatDb;

        chatRootPane.setVisible(true);
        chatRootPane.setManaged(true);
        placeholderRoot.setVisible(false);
        placeholderRoot.setManaged(false);

        contactName.setText(selectedContact);
        contactStatus.setText("Online");
        contactAvatar.setText("🐱");

        updateGroupActionButtons(false, false);

        loadMessages();
    }

    /**
     * Khởi tạo UI cho một group chat.
     */
    public void initGroup(PeerHandle peer, String currentUser, String groupId, String groupName, ChatDb chatDb) {
        this.peer = peer;
        this.currentUser = currentUser;
        this.chatDb = chatDb;
        this.currentGroupId = groupId;
        this.currentGroupName = groupName;
        this.selectedContact = null; // không ở chế độ peer chat

        if (chatRootPane != null) {
            chatRootPane.setVisible(true);
            chatRootPane.setManaged(true);
        }
        if (placeholderRoot != null) {
            placeholderRoot.setVisible(false);
            placeholderRoot.setManaged(false);
        }

        contactName.setText(groupName);
        contactStatus.setText("Group");
        contactAvatar.setText("👥");

        boolean isOwner = false;
        if (chatDb != null && currentGroupId != null) {
            String owner = chatDb.getGroupOwner(currentGroupId);
            isOwner = currentUser.equals(owner);
        }
        updateGroupActionButtons(true, isOwner);


        messagesBox.getChildren().clear();
        if (chatDb != null) {
            loadGroupHistory(groupId);
        }
    }

    private void loadMessages() {
        messagesBox.getChildren().clear();

        if (chatDb == null || currentUser == null)
            return;
        if (currentGroupId != null) {
            // lịch sử group đã được load trong initGroup()
            return;
        }
        if (selectedContact == null)
            return;

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
                        if (f.exists())
                            size = f.length();
                    }
                    // Sử dụng onIncomingFile để hiển thị đúng dạng (tự động phân biệt
                    // voice/image/file)
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
                javafx.scene.image.Image img = new javafx.scene.image.Image(new File(filePath).toURI().toString(), 200,
                        200, true, true);
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
        if (peer == null || chatDb == null)
            return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file để gửi");
        File f = chooser.showOpenDialog(null);
        if (f == null)
            return;
        if (currentGroupId != null) {
            handleGroupFileSend(f, null);
        } else if (selectedContact != null) {
            new Thread(() -> {
                try {
                    peer.sendFileByName(selectedContact, f.getAbsolutePath());
                    chatDb.insertMessage(
                            new Message(currentUser, selectedContact, f.getName(), true, f.getAbsolutePath()));
                    long size = f.length();
                    Platform.runLater(() -> onIncomingFile(currentUser, f.getName(), f.getAbsolutePath(), size));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "send-file-thread").start();
        }
    }

    @FXML
    private void onSendImage() {
        if (peer == null || chatDb == null)
            return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh để gửi");
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = chooser.showOpenDialog(null);
        if (f == null)
            return;
        if (currentGroupId != null) {
            handleGroupFileSend(f, null);
        } else if (selectedContact != null) {
            new Thread(() -> {
                try {
                    peer.sendFileByName(selectedContact, f.getAbsolutePath());
                    chatDb.insertMessage(
                            new Message(currentUser, selectedContact, f.getName(), true, f.getAbsolutePath()));
                    long size = f.length();
                    Platform.runLater(() -> onIncomingFile(currentUser, f.getName(), f.getAbsolutePath(), size));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "send-image-thread").start();
        }
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
        appendPlainTextBubble(msg.getFromUser(), msg.getContent(), isSent);
    }

    private void appendPlainTextBubble(String sender, String content, boolean isSent) {
        // Trong group: hiển thị tên người gửi trước nội dung nếu không phải mình
        String display = content;
        if (currentGroupId != null && !isSent) {
            display = sender + ": " + content;
        }

        if (!messagesBox.getChildren().isEmpty()) {
            javafx.scene.Node last = messagesBox.getChildren().get(messagesBox.getChildren().size() - 1);
            if (last instanceof HBox h && !h.getChildren().isEmpty() && h.getChildren().get(0) instanceof Label l) {
                String lastText = l.getText();
                if (lastText.equals(display)) {
                    return;
                }
            }
        }

        Label bubble = new Label(display);
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
            messageField.clear();

            if (currentGroupId != null) {
                // Gửi message trong group
                if (peer != null) {
                    peer.sendGroupMessage(currentGroupId, messageText);
                }
                // Hiển thị local luôn
                appendPlainTextBubble(currentUser, messageText, true);
            } else if (selectedContact != null) {
                // peer-to-peer như cũ
                Message msg = new Message(currentUser, selectedContact, messageText, false, null);
                msg.setTimestamp(System.currentTimeMillis()); // Set timestamp

                // Lưu vào database trước khi gửi
                if (chatDb != null) {
                    chatDb.insertMessage(msg);
                }

                appendMessage(msg);
                if (peer != null) {
                    peer.sendToByName(selectedContact, messageText);
                }
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
        if (peer == null || selectedContact == null || chatDb == null)
            return;

        // Open voice recorder modal
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/voice-recorder.fxml"));
            Parent root = loader.load();

            VoiceRecorderController controller = loader.getController();
            controller.init(currentUser, selectedContact, new VoiceRecorderController.VoiceMessageCallback() {
                @Override
                public void onVoiceSend(String filePath, int durationSeconds) {

                    Platform.runLater(() -> appendVoiceBubble(currentUser, filePath, durationSeconds)
                    );
                    sendVoiceMessage(filePath, durationSeconds);

                }

                @Override
                public void onTextSend(String text) {
                    // Lưu text vào DB
                    Message textMsg = new Message(currentUser, selectedContact, text, false, null);
                    textMsg.setTimestamp(System.currentTimeMillis());
                    chatDb.insertMessage(textMsg);

                    // Hiển thị local
                    Platform.runLater(() -> appendMessage(textMsg));

                    // Gửi P2P
                    if (peer != null) {
                        peer.sendToByName(selectedContact, text);
                    }
                }
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
        if (peer == null || chatDb == null)
            return;

        File voiceFile = new File(filePath);
        if (!voiceFile.exists())
            return;

        if (currentGroupId != null) {
            handleGroupFileSend(voiceFile, durationSeconds);
        } else if (selectedContact != null) {
            new Thread(() -> {
                try {
                    peer.sendFileByName(selectedContact, filePath);

                    // Lưu vào DB với metadata thời gian
                    String message = voiceFile.getName();
                    chatDb.insertMessage(new Message(currentUser, selectedContact, message, true, filePath));

                    long size = voiceFile.length();
                    Platform.runLater(
                            () -> onIncomingVoice(currentUser, voiceFile.getName(), filePath, size, durationSeconds));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, "send-voice-thread").start();
        }
    }

    /**
     * Gửi file/ảnh/voice tới tất cả thành viên trong group hiện tại và lưu lịch sử
     * group_files.
     */
    private void handleGroupFileSend(File file, Integer voiceDurationSecondsIfAny) {
        if (peer == null || currentGroupId == null || chatDb == null)
            return;
        if (file == null || !file.exists())
            return;

        new Thread(() -> {
            try {
                String absPath = file.getAbsolutePath();
                String filename = file.getName();

                List<String> members = chatDb.getGroupMembers(currentGroupId);
                for (String member : members) {
                    if (member.equals(currentUser))
                        continue;
                    peer.sendGroupFileByName(member, absPath, currentGroupId);
                }

                // lưu vào bảng group_files
                chatDb.insertGroupFile(currentGroupId, currentUser, filename, absPath);

                long size = file.length();
                Platform.runLater(() -> {
                    if (voiceDurationSecondsIfAny != null) {
                        onIncomingVoice(currentUser, filename, absPath, size, voiceDurationSecondsIfAny);
                    } else {
                        onIncomingFile(currentUser, filename, absPath, size);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "send-group-file-thread").start();
    }

    public void onIncomingGroupFile(String groupId, String sender, String filename, String absolutePath, long size) {
        if (currentGroupId == null || !currentGroupId.equals(groupId))
            return;
        if (isVoiceFile(filename)) {
            int estimatedDuration = (int) Math.max(1, size / 88200);
            onIncomingVoice(sender, filename, absolutePath, size, estimatedDuration);
        } else {
            boolean isSent = sender.equals(currentUser);
            boolean isImage = isImageFile(filename);
            VBox fileBubble = new VBox(6);
            fileBubble.getStyleClass().add(isSent ? "file-bubble-sent" : "file-bubble-received");
            if (isImage && absolutePath != null && new File(absolutePath).exists()) {
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(
                            new File(absolutePath).toURI().toString(),
                            200, 200, true, true);
                    if (img.isError()) {
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
                    addFileInfo(fileBubble, filename, size, absolutePath);
                }
            } else {
                addFileInfo(fileBubble, filename, size, absolutePath);
            }
            HBox row = new HBox(fileBubble);
            row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            row.getStyleClass().add("chat-row");
            messagesBox.getChildren().add(row);
            scrollToBottom();
        }
    }

    public void onIncomingVoice(String sender, String filename, String absolutePath, long size, int durationSeconds) {
        // Peer chat: chỉ hiển thị nếu là cuộc trò chuyện với sender hoặc chính mình
        if (currentGroupId == null) {
            if (selectedContact == null)
                return;
            if (!sender.equals(selectedContact) && !sender.equals(currentUser))
                return;
        }

        appendVoiceBubble(sender, absolutePath, durationSeconds);

    }


    private void appendVoiceBubble(String sender, String filePath, int durationSeconds) {
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

        Platform.runLater(() -> {
            messagesBox.getChildren().add(row);
            scrollToBottom();
        });
    }


    private void playVoiceMessage(String filePath, Label playIcon) {
        new Thread(() -> {
            try {
                File audioFile = new File(filePath);
                if (!audioFile.exists())
                    return;

                Platform.runLater(() -> playIcon.setText("⏸️"));

                javax.sound.sampled.AudioInputStream audioStream = javax.sound.sampled.AudioSystem
                        .getAudioInputStream(audioFile);
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
     * Chỉ dùng cho gọi video: mở video-call-modal.fxml với
     * VideoCallModalController.
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
        // Chỉ handle cho peer chat
        if (currentGroupId != null)
            return;
        if (selectedContact == null || !sender.equals(selectedContact))
            return;
        Message m = new Message(sender, currentUser, msg, false, null);
        appendMessage(m);
    }

    /**
     * Được MainController gọi khi nhận GROUP_MSG.
     */
    public void onIncomingGroupMessage(String groupId, String from, String content) {
        if (currentGroupId == null || !currentGroupId.equals(groupId))
            return;
        if (from.equals(currentUser))
            return;
        appendPlainTextBubble(from, content, false);
    }

    public void onIncomingFile(String sender, String filename, String absolutePath, long size) {
        System.out.println(
                "[ChatArea] onIncomingFile: sender=" + sender + ", filename=" + filename + ", path=" + absolutePath);
        System.out.println("[ChatArea] currentUser=" + currentUser + ", selectedContact=" + selectedContact
                + ", currentGroupId=" + currentGroupId);

        // Peer chat: chỉ hiển thị nếu sender là selectedContact hoặc currentUser
        if (currentGroupId == null) {
            if (selectedContact == null) {
                System.out.println("[ChatArea] selectedContact is null, skipping");
                return;
            }
            if (!sender.equals(selectedContact) && !sender.equals(currentUser)) {
                System.out.println("[ChatArea] sender doesn't match, skipping. sender=" + sender);
                return;
            }
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
                javafx.scene.image.Image img = new javafx.scene.image.Image(new File(absolutePath).toURI().toString(),
                        200, 200, true, true);
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
        if (lower.endsWith(".pdf"))
            return "📕";
        if (lower.endsWith(".doc") || lower.endsWith(".docx"))
            return "📘";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx"))
            return "📗";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx"))
            return "📙";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z"))
            return "🗜️";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac"))
            return "🎵";
        if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv"))
            return "🎬";
        if (lower.endsWith(".txt"))
            return "📝";
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js"))
            return "💻";
        return "📄";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
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
        // Bên B (người nhận): mở ReceiveCall.fxml để hiển thị "A đang gọi..." với nút
        // Chấp nhận / Hủy
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
        // Bên A (người gọi): mở trực tiếp giao diện VoiceCall với trạng thái "Đang
        // gọi..."
        openVoiceCallForCaller();
    }

    @FXML
    private void onCallVideo() throws SocketException, LineUnavailableException {
        if (peer != null && selectedContact != null) {
            peer.startVideoCall(selectedContact);
            isCurrentCallVideo = true; // Đánh dấu là video call
        }
        // Bên A (người gọi): mở trực tiếp giao diện VideoCall với trạng thái "Đang
        // gọi..."
        openVideoCallForCaller();
    }

    @FXML
    private void onOpenInfoPanel() {
        // Nếu đang ở group, hiển thị danh sách thành viên nhóm
        if (currentGroupId != null && chatDb != null) {
            List<String> members = chatDb.getGroupMembers(currentGroupId);
            String text = String.join(", ", members);
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Thành viên nhóm");
            alert.setHeaderText("Nhóm: " + currentGroupName);
            alert.setContentText(text.isEmpty() ? "(Không có thành viên)" : text);
            alert.showAndWait();
            return;
        }

        // Khởi tạo dữ liệu cho panel mỗi lần mở (đối với peer chat)
        if (infoPanelRootController != null) {
            infoPanelRootController.init(contactName.getText(), contactStatus.getText(), contactAvatar.getText(), 1);
        }

        infoPanelRoot.setVisible(true);
        infoPanelRoot.setManaged(true);
    }

    private void openVoiceCallForCaller() {
        if (peer == null || selectedContact == null)
            return;
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
        if (peer == null || selectedContact == null)
            return;
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

    public void updateGroupHeader(String newName) {
        if (currentGroupId != null) {
            currentGroupName = newName;
            contactName.setText(newName);
        }
    }

    @FXML
    private void onLeaveGroup() {
        if (peer == null || currentGroupId == null || chatDb == null)
            return;
        String leavingGroupId = currentGroupId;
        List<String> members = chatDb.getGroupMembers(currentGroupId);
        for (String m : members) {
            if (!m.equals(currentUser)) {
                peer.sendToByName(m, "GROUP_LEAVE|" + currentGroupId + "|" + currentUser);
            }
        }
        chatDb.removeGroupMember(currentGroupId, currentUser);
        chatDb.deleteGroupIfEmpty(currentGroupId);
        if (peer != null) {
            peer.notifyLocalGroupLeft(leavingGroupId, currentUser);
        }
        currentGroupId = null;
        currentGroupName = null;
        selectedContact = null;
        if (chatRootPane != null) {
            chatRootPane.setVisible(false);
            chatRootPane.setManaged(false);
        }
        if (placeholderRoot != null) {
            placeholderRoot.setVisible(true);
            placeholderRoot.setManaged(true);
        }
    }

    @FXML
    private void onRenameGroup() {
        if (peer == null || currentGroupId == null || chatDb == null)
            return;
        String owner = chatDb.getGroupOwner(currentGroupId);
        if (!currentUser.equals(owner))
            return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(currentGroupName);
        dialog.setTitle("Đổi tên nhóm");
        dialog.setHeaderText("Nhập tên nhóm mới");
        dialog.setContentText("Tên nhóm:");
        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isEmpty())
            return;
        String newName = result.get().trim();
        if (newName.isEmpty())
            return;
        chatDb.renameGroup(currentGroupId, newName);
        List<String> members = chatDb.getGroupMembers(currentGroupId);
        for (String m : members) {
            if (!m.equals(currentUser)) {
                peer.sendToByName(m, "GROUP_RENAME|" + currentGroupId + "|" + newName);
            }
        }
        currentGroupName = newName;
        contactName.setText(newName);
        if (chatDb != null) {
            chatDb.insertGroupMessage(currentGroupId, "SYSTEM", "Tên nhóm đã đổi thành \"" + newName + "\"");
        }
        onGroupSystemMessage(currentGroupId, "Tên nhóm đã đổi thành \"" + newName + "\"");
        if (peer != null) {
            peer.notifyLocalGroupRenamed(currentGroupId, newName);
        }
    }

    @FXML
    private void onManageMembers() {
        if (peer == null || chatDb == null || currentGroupId == null)
            return;
        String owner = chatDb.getGroupOwner(currentGroupId);
        if (!currentUser.equals(owner))
            return;
        java.util.List<String> allUsers = chatDb.getAllUsers();
        java.util.List<String> currentMembers = chatDb.getGroupMembers(currentGroupId);
        allUsers.remove(currentUser);
        javafx.scene.control.Dialog<ManageResult> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Quản lý thành viên nhóm");
        dialog.setHeaderText("Chọn thành viên để thêm hoặc xóa");
        javafx.scene.control.ButtonType saveBtn = new javafx.scene.control.ButtonType("Lưu",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, javafx.scene.control.ButtonType.CANCEL);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8);
        box.setPadding(new javafx.geometry.Insets(10));
        java.util.List<javafx.scene.control.CheckBox> addChecks = new java.util.ArrayList<>();
        java.util.List<javafx.scene.control.CheckBox> removeChecks = new java.util.ArrayList<>();
        box.getChildren().add(new javafx.scene.control.Label("Thêm thành viên:"));
        for (String u : allUsers) {
            if (!currentMembers.contains(u)) {
                javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(u);
                addChecks.add(cb);
                box.getChildren().add(cb);
            }
        }
        box.getChildren().add(new javafx.scene.control.Label("Xóa thành viên:"));
        for (String u : currentMembers) {
            if (!u.equals(currentUser)) {
                javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(u);
                removeChecks.add(cb);
                box.getChildren().add(cb);
            }
        }
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                java.util.List<String> toAdd = new java.util.ArrayList<>();
                for (javafx.scene.control.CheckBox cb : addChecks)
                    if (cb.isSelected())
                        toAdd.add(cb.getText());
                java.util.List<String> toRemove = new java.util.ArrayList<>();
                for (javafx.scene.control.CheckBox cb : removeChecks)
                    if (cb.isSelected())
                        toRemove.add(cb.getText());
                return new ManageResult(toAdd, toRemove);
            }
            return null;
        });
        java.util.Optional<ManageResult> res = dialog.showAndWait();
        res.ifPresent(r -> {
            if (r.toAdd != null && !r.toAdd.isEmpty())
                handleAddMembers(r.toAdd);
            if (r.toRemove != null && !r.toRemove.isEmpty())
                handleRemoveMembers(r.toRemove);
            if ((r.toAdd != null && !r.toAdd.isEmpty()) || (r.toRemove != null && !r.toRemove.isEmpty())) {
                if (chatDb != null) {
                    chatDb.insertGroupMessage(currentGroupId, "SYSTEM", "Danh sách thành viên nhóm đã được cập nhật");
                }
                onGroupSystemMessage(currentGroupId, "Danh sách thành viên nhóm đã được cập nhật");
            }
        });
    }

    public void onGroupSystemMessage(String groupId, String content) {
        if (currentGroupId == null || !currentGroupId.equals(groupId)) {
            return;
        }
        if (!messagesBox.getChildren().isEmpty()) {
            javafx.scene.Node last = messagesBox.getChildren().get(messagesBox.getChildren().size() - 1);
            if (last instanceof HBox h && !h.getChildren().isEmpty() && h.getChildren().get(0) instanceof Label l) {
                String lastText = l.getText();
                if (lastText.equals(content)) {
                    return;
                }
            }
        }
        Label bubble = new Label(content);
        bubble.setWrapText(true);
        bubble.getStyleClass().add("bubble-system");
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("chat-row");
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void loadGroupHistory(String groupId) {
        List<Message> timeline = new ArrayList<>();
        List<Message> msgs = chatDb.loadGroupMessagesAsc(groupId, 1000);
        if (msgs != null)
            timeline.addAll(msgs);
        List<Message> files = chatDb.loadGroupFilesAsc(groupId, 1000);
        if (files != null)
            timeline.addAll(files);
        timeline.sort(Comparator.comparingLong(Message::getTimestamp));

        for (Message item : timeline) {
            if (item.isFile()) {
                String filename = item.getContent();
                String path = item.getFilePath();
                long size = 0;
                if (path != null) {
                    File f = new File(path);
                    if (f.exists())
                        size = f.length();
                }
                if (isVoiceFile(filename)) {
                    int estimatedDuration = (int) Math.max(1, size / 88200);
                    onIncomingVoice(item.getFromUser(), filename, path, size, estimatedDuration);
                } else {
                    onIncomingFile(item.getFromUser(), filename, path, size);
                }
            } else {
                if ("SYSTEM".equals(item.getFromUser())) {
                    onGroupSystemMessage(groupId, item.getContent());
                } else {
                    boolean isSent = item.getFromUser().equals(currentUser);
                    appendPlainTextBubble(item.getFromUser(), item.getContent(), isSent);
                }
            }
        }
    }

    private void handleAddMembers(List<String> members) {
        if (peer == null || chatDb == null || currentGroupId == null)
            return;
        java.util.List<String> normalized = new java.util.ArrayList<>();
        for (String m : members) {
            String mm = m.trim();
            if (!mm.isEmpty())
                normalized.add(mm);
        }
        if (normalized.isEmpty())
            return;
        chatDb.insertGroupMembers(currentGroupId, normalized);
        String csv = String.join(",", normalized);
        List<String> existing = chatDb.getGroupMembers(currentGroupId);
        for (String m : existing) {
            if (m.equals(currentUser))
                continue;
            peer.sendToByName(m, "GROUP_ADD_MEMBER|" + currentGroupId + "|" + csv);
        }
    }

    private void handleRemoveMembers(List<String> members) {
        if (peer == null || chatDb == null || currentGroupId == null)
            return;
        java.util.List<String> normalized = new java.util.ArrayList<>();
        for (String m : members) {
            String mm = m.trim();
            if (!mm.isEmpty())
                normalized.add(mm);
        }
        if (normalized.isEmpty())
            return;
        for (String m : normalized) {
            chatDb.removeGroupMember(currentGroupId, m);
        }
        chatDb.deleteGroupIfEmpty(currentGroupId);
        String csv = String.join(",", normalized);
        List<String> existing = chatDb.getGroupMembers(currentGroupId);
        for (String m : existing) {
            if (m.equals(currentUser))
                continue;
            peer.sendToByName(m, "GROUP_REMOVE_MEMBER|" + currentGroupId + "|" + csv);
        }
    }

    private static class ManageResult {
        final java.util.List<String> toAdd;
        final java.util.List<String> toRemove;

        ManageResult(java.util.List<String> toAdd, java.util.List<String> toRemove) {
            this.toAdd = toAdd;
            this.toRemove = toRemove;
        }
    }


    public void onCallAccepted(String peerName) {
        // Kiểm tra nếu đang có video call window mở
        if (activeVideoCallController != null && activeVideoCallStage != null && activeVideoCallStage.isShowing()) {
            // Cập nhật UI video call hiện tại
            activeVideoCallController.transitionToInCall();
        }
        // Kiểm tra nếu đang có voice call window mở
        else if (activeVoiceCallController != null && activeVoiceCallStage != null
                && activeVoiceCallStage.isShowing()) {
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
                    // Bên nhận: khởi tạo voice call với PeerHandle và trạng thái đang trong cuộc
                    // gọi
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
