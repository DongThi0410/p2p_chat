package org.example.peer_chat.ui.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.peer_chat.PeerHandle;

public class VoiceCallController {
    @FXML
    private Text callDuration;
    @FXML
    private Label contactName;
    @FXML
    private Text callTypeLabel;
    @FXML
    private Button muteButton;
    @FXML
    private Button endCallButton;
    @FXML
    private Button acceptCallButton;
    @FXML
    private Button rejectCallButton;
    @FXML
    private VBox callContainer;

    // reference to core call handler (PeerHandle)
    private PeerHandle peerHandle;
    private String remoteName;
    private String remoteIp;
    private int remoteVoicePort;
    private boolean isInCall = false;
    private boolean isMuted = false;
    private int durationInSeconds = 0;
    private Timeline callDurationTimer;

    // keep track of active instance to allow remote side to close it on CALL_END
    private static VoiceCallController activeInstance;

    public void initialize() {
        // Set up UI
        updateCallUI(false);
        callTypeLabel.setText("Cuộc gọi thoại ");

        // Set button actions
        endCallButton.setOnAction(e -> endCall());
        muteButton.setOnAction(e -> toggleMute());
        acceptCallButton.setOnAction(e -> acceptIncomingCall());
        rejectCallButton.setOnAction(e -> rejectIncomingCall());
        
        // Đảm bảo endCallButton luôn visible và enabled khi cần
        endCallButton.setVisible(true);
        endCallButton.setDisable(true); // Disable ban đầu, sẽ enable khi inCall

        // Register this instance as active
        activeInstance = this;
    }

    // ===== Public init methods to link with calling / receive call UI =====

    /**
     * Bên A (người gọi): cấu hình UI "Đang gọi ..." tới remoteName.
     * KHÔNG start timer, chỉ hiển thị "Đang gọi..." cho đến khi nhận CALL_ACCEPT.
     */
    public void initOutgoing(PeerHandle peerHandle, String remoteName) {
        this.peerHandle = peerHandle;
        this.remoteName = remoteName;

        // Không start timer, chỉ hiển thị "Đang gọi..."
        updateCallUI(false);
        callDuration.setVisible(false);
        contactName.setText("Đang gọi " + remoteName + "...");
        // Đảm bảo timer không chạy
        stopCallTimer();
    }

    /**
     * Bên B (người nhận): cấu hình UI "A đang gọi..." với nút Chấp nhận / Hủy.
     */
    public void initIncoming(PeerHandle peerHandle, String callerName, String callerIp, int callerVoicePort) {
        this.peerHandle = peerHandle;
        this.remoteName = callerName;
        this.remoteIp = callerIp;
        this.remoteVoicePort = callerVoicePort;

        contactName.setText(callerName + " đang gọi...");
        showIncomingCallUI();
    }

    /**
     * Bên B sau khi đã nhận cuộc gọi từ ReceiveCall.fxml: cấu hình UI
     * voice-call-view
     * để hiển thị trong trình gọi (timer chạy, không hiển thị nút Chấp nhận/Hủy).
     */
    public void initIncomingAccepted(PeerHandle peerHandle, String callerName) {
        this.peerHandle = peerHandle;
        this.remoteName = callerName;

        startInCallUI();
    }

    /**
     * Bên A (hoặc B) sau khi nhận được tín hiệu CALL_ACCEPT từ peer: chuyển sang
     * UI đang trong cuộc gọi với timer chạy.
     */
    public void showInCall(String peerName) {
        this.remoteName = peerName;
        startInCallUI();
    }

    /**
     * Cập nhật UI từ trạng thái "Đang gọi..." sang "Đang trong cuộc gọi" với timer.
     * Dùng cho bên A khi nhận CALL_ACCEPT.
     */
    public void transitionToInCall() {
        startInCallUI();
    }

    @FXML
    private void acceptIncomingCall() {
        startInCallUI();

        if (peerHandle != null && remoteName != null) {
            peerHandle.acceptCall(remoteName, remoteIp, remoteVoicePort);
        }
    }

    @FXML
    private void rejectIncomingCall() {
        showStatusMessage("Đã từ chối cuộc gọi");
        // hiện tại không có tín hiệu CALL_REJECT, chỉ cần đóng UI
        closeWindow();
    }

    @FXML
    public void endCall() {
        // Gửi CALL_END và trigger onCallEnded() callback
        // Không đóng window ở đây, để onCallEnded() callback xử lý
        // để đảm bảo cả hai bên đều đóng window và hiển thị popup
        if (peerHandle != null) {
            peerHandle.stopVoiceCall();
        }
        // stopVoiceCall() sẽ gọi listener.onCallEnded()
        // MainController.onCallEnded() sẽ đóng window và hiển thị popup
    }

    @FXML
    private void toggleMute() {
        isMuted = !isMuted;
        muteButton.getStyleClass().removeAll("active");
        if (isMuted) {
            muteButton.getStyleClass().add("active");
            // TODO: Implement mute functionality
        }
    }

    private void startCallTimer() {
        stopCallTimer();
        durationInSeconds = 0;
        updateCallDuration();
        callDurationTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateCallDuration()));
        callDurationTimer.setCycleCount(Timeline.INDEFINITE);
        callDurationTimer.play();
    }

    private void stopCallTimer() {
        if (callDurationTimer != null) {
            callDurationTimer.stop();
        }
    }

    private void updateCallDuration() {
        durationInSeconds++;
        int minutes = durationInSeconds / 60;
        int seconds = durationInSeconds % 60;
        callDuration.setText(String.format("%02d:%02d", minutes, seconds));
    }

    /**
     * Đưa UI vào trạng thái đang trong cuộc gọi: hiển thị timer, ẩn nút chấp
     * nhận/từ chối.
     */
    private void startInCallUI() {
        isInCall = true;
        updateCallUI(true);
        contactName.setText(remoteName);
        startCallTimer();
    }

    private void updateCallUI(boolean inCall) {
        isInCall = inCall;
        // Enable và hiển thị endCallButton khi đang trong cuộc gọi
        endCallButton.setDisable(!inCall);
        endCallButton.setVisible(inCall);
        muteButton.setVisible(inCall);
        callDuration.setVisible(inCall);

        // Hide accept/reject buttons during call
        if (inCall) {
            acceptCallButton.setVisible(false);
            rejectCallButton.setVisible(false);
        }
    }

    private void showIncomingCallUI() {
        acceptCallButton.setVisible(true);
        rejectCallButton.setVisible(true);
        endCallButton.setVisible(false);
        muteButton.setVisible(false);
    }

    private void resetCallUI() {
        isInCall = false;
        remoteName = null;
        updateCallUI(false);
        contactName.setText("");
        callDuration.setText("00:00");
        acceptCallButton.setVisible(false);
        rejectCallButton.setVisible(false);
    }

    private void showStatusMessage(String message) {
        contactName.setText(message);
        // Auto-clear status message after 3 seconds
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            if (contactName.getText().equals(message)) {
                                contactName.setText("");
                            }
                        });
                    }
                },
                3000);
    }

    public void cleanup() {
        stopCallTimer();
    }

    private void closeWindow() {
        if (contactName != null && contactName.getScene() != null) {
            Stage stage = (Stage) contactName.getScene().getWindow();
            if (stage != null) {
                stage.close();
            }
        }
        // Clear active instance when window is closed
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    /**
     * Called from outside (MainController) when remote side sends CALL_END.
     * Ensures the active voice call window is closed on the callee/caller side as
     * well.
     */
    public static void closeActiveOnRemoteEnded() {
        if (activeInstance != null) {
            if (activeInstance.callDurationTimer != null) {
                activeInstance.callDurationTimer.stop();
            }
            activeInstance.closeWindow();
        }
    }
}
