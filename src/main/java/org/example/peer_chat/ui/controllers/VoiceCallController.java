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

import javax.sound.sampled.LineUnavailableException;
import java.net.SocketException;

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
        activeInstance = this;
        System.out.println("[VoiceCallController] activeInstance registered");
        // Set up UI
        updateCallUI(false);
        callTypeLabel.setText("Cuộc gọi thoại ");

        // Set button actions
        // endCallButton.setOnAction(e -> endCall());
        muteButton.setOnAction(e -> toggleMute());
        acceptCallButton.setOnAction(e -> {
            try {
                onAccept();
            } catch (SocketException ex) {
                throw new RuntimeException(ex);
            } catch (LineUnavailableException ex) {
                throw new RuntimeException(ex);
            }
        });
        rejectCallButton.setOnAction(e -> rejectIncomingCall());

        // Đảm bảo endCallButton luôn visible và enabled khi cần
        endCallButton.setVisible(true);
        endCallButton.setDisable(false); // Disable ban đầu, sẽ enable khi inCall

        //
        endCallButton.setOnMouseClicked(e -> System.out.println("END BUTTON MOUSE CLICK"));
        rejectCallButton.setOnMouseClicked(e -> {
            System.out.println("🔥 REJECT BUTTON RAW MOUSE CLICK");
        });

    }

    @FXML
    private void onAccept() throws SocketException, LineUnavailableException {
        System.out.println("[VoiceCall] Accept pressed for " + remoteName);

        if (peerHandle == null) {
            System.err.println("[BUG] peerHandle null on accept");
            return;
        }

        peerHandle.acceptCall(remoteName, remoteIp, remoteVoicePort);
        transitionToInCall();
    }

    public void onShown() {
        activeInstance = this;
    }

    public void init(PeerHandle peerHandle, String remoteName, boolean inCall) {
        this.peerHandle = peerHandle;
        this.remoteName = remoteName;

        if (inCall) {
            startInCallUI();
        }
    }

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

    public void showInCall(String peerName) {
        this.remoteName = peerName;
        startInCallUI();
    }

    public void transitionToInCall() {
        startInCallUI();
    }

    @FXML
    private void rejectIncomingCall() {
        System.out.println("Đã từ chối cuộc gọi");
        showStatusMessage("Đã từ chối cuộc gọi");

        if (peerHandle != null && remoteName != null) {
            peerHandle.rejectCall(remoteName);
        }

        closeWindow();
    }

    @FXML
    public void endCall() throws SocketException, LineUnavailableException {
        if (peerHandle == null) {
            System.out.println("[UI] endCall() called, peerHandle=" + peerHandle);
            // Không có PeerHandle để gửi CALL_END, chỉ cần đóng window local
            closeWindow();
            return;
        }
        peerHandle.stopVoiceCall();
    }

    @FXML
    private void toggleMute() {
        isMuted = !isMuted;
        muteButton.setText(isMuted ? "🔇" : "🔈");
        // Also toggle active class if needed for other styling, but text change is
        // primary for uniformity
        muteButton.getStyleClass().removeAll("active");
        if (isMuted) {
            muteButton.getStyleClass().add("active");
        }
    }

    public long getDurationSeconds() {
        return durationInSeconds;
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

    private void startInCallUI() {
        isInCall = true;
        updateCallUI(true);
        contactName.setText(remoteName);
        startCallTimer();
    }

    private void updateCallUI(boolean inCall) {
        isInCall = inCall;

        endCallButton.setVisible(inCall);
        endCallButton.setDisable(!inCall);

        muteButton.setVisible(inCall);
        callDuration.setVisible(inCall);

        // Khi đã vào call → ẩn accept/reject
        acceptCallButton.setVisible(false);
        rejectCallButton.setVisible(false);
    }

    private void showIncomingCallUI() {
        // Incoming = chưa inCall
        isInCall = false;

        acceptCallButton.setVisible(true);
        rejectCallButton.setVisible(true);

        acceptCallButton.setDisable(false);
        rejectCallButton.setDisable(false);

        endCallButton.setVisible(false);
        muteButton.setVisible(false);
        callDuration.setVisible(false);

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
        System.out.println("[DEBUG] closeActiveOnRemoteEnded called, activeInstance=" + activeInstance);

        System.out.println("Active instance: " + activeInstance);
        if (activeInstance != null) {
            activeInstance.closeWindow();
        } else {
            System.out.println("No active instance to close");
        }

    }
}
