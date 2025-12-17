package org.example.peer_chat.ui.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.stage.Stage;
import javafx.application.Platform;
import org.example.peer_chat.PeerHandle;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;
import java.awt.image.BufferedImage;

public class VideoCallModalController {

    @FXML
    private Text callDuration;
    @FXML
    private Label contactName;
    @FXML
    private Text callTypeLabel;
    @FXML
    private Button muteButton;
    @FXML
    private Button videoButton;
    @FXML
    private Button shareScreenButton;
    @FXML
    private Button endCallButton;
    @FXML
    private Button acceptButton;
    @FXML
    private Button rejectButton;
    @FXML
    private ImageView localVideoFeed;

    private boolean isMuted = false;
    private boolean isVideoOff = false;
    private boolean isScreenSharing = false;
    private int durationInSeconds = 0;
    private String callType = "voice"; // voice, video, screen

    // reference to core call handler
    private PeerHandle peerHandle;
    private String remoteName;
    private String remoteIp;
    private int remoteVoicePort;

    // Timer for call duration
    private Timeline callDurationTimer;

    // state flags
    private boolean isIncoming = false;
    private boolean isInCall = false;

    // keep track of last opened modal to allow remote side to close it on CALL_END
    private static VideoCallModalController activeInstance;

    // Webcam for video capture
    private Webcam webcam;
    private Timeline videoUpdateTimer;

    @FXML
    public void initialize() {
        contactName.setText("Contact Name");

        // register this instance as active
        activeInstance = this;

        // default: hide incoming buttons until needed
        if (acceptButton != null)
            acceptButton.setVisible(false);
        if (rejectButton != null)
            rejectButton.setVisible(false);
    }

    /**
     * Configure UI according to call type: "voice", "video" or "screen".
     */
    public void init(String type) {
        this.callType = type == null ? "voice" : type;

        switch (this.callType) {
            case "video":
                callTypeLabel.setText("Cuộc gọi video 📹");
                localVideoFeed.setVisible(true);
                localVideoFeed.setManaged(true);
                startVideoCapture();
                break;
            case "screen":
                callTypeLabel.setText("Chia sẻ màn hình 🖥");
                localVideoFeed.setVisible(true);
                localVideoFeed.setManaged(true);
                startVideoCapture();
                break;
            case "voice":
            default:
                callTypeLabel.setText("Cuộc gọi thoại 🎤");
                localVideoFeed.setVisible(false);
                localVideoFeed.setManaged(false);
                stopVideoCapture();
                break;
        }
    }

    /**
     * Bắt đầu capture video từ webcam và hiển thị trong localVideoFeed.
     */
    private void startVideoCapture() {
        if (webcam != null && webcam.isOpen()) {
            return; // Đã đang capture
        }

        try {
            // Tìm và mở webcam đầu tiên
            webcam = Webcam.getDefault();
            if (webcam == null) {
                System.out.println("[VideoCall] No webcam found");
                return;
            }

            // Set resolution (có thể điều chỉnh)
            webcam.setViewSize(new java.awt.Dimension(640, 480));
            webcam.open();

            // Tạo timer để update video feed mỗi 33ms (~30 FPS)
            videoUpdateTimer = new Timeline(new KeyFrame(Duration.millis(33), e -> updateVideoFrame()));
            videoUpdateTimer.setCycleCount(Timeline.INDEFINITE);
            videoUpdateTimer.play();

            System.out.println("[VideoCall] Started video capture");
        } catch (WebcamException e) {
            System.err.println("[VideoCall] Failed to start webcam: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[VideoCall] Error initializing webcam: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Dừng capture video từ webcam.
     */
    private void stopVideoCapture() {
        if (videoUpdateTimer != null) {
            videoUpdateTimer.stop();
            videoUpdateTimer = null;
        }

        if (webcam != null) {
            try {
                if (webcam.isOpen()) {
                    webcam.close();
                }
            } catch (Exception e) {
                System.err.println("[VideoCall] Error closing webcam: " + e.getMessage());
            }
            webcam = null;
        }

        // Clear video feed
        if (localVideoFeed != null) {
            Platform.runLater(() -> localVideoFeed.setImage(null));
        }
    }

    /**
     * Cập nhật frame video từ webcam vào ImageView.
     */
    private void updateVideoFrame() {
        if (webcam == null || !webcam.isOpen() || localVideoFeed == null) {
            return;
        }

        try {
            BufferedImage bufferedImage = webcam.getImage();
            if (bufferedImage != null) {
                // Convert BufferedImage to JavaFX Image
                Image fxImage = convertToFxImage(bufferedImage);
                Platform.runLater(() -> {
                    if (localVideoFeed != null) {
                        localVideoFeed.setImage(fxImage);
                    }
                });
            }
        } catch (Exception e) {
            // Ignore errors during capture
        }
    }

    /**
     * Convert BufferedImage (AWT) to JavaFX Image.
     */
    private Image convertToFxImage(BufferedImage image) {
        WritableImage wr = null;
        if (image != null) {
            wr = new WritableImage(image.getWidth(), image.getHeight());
            PixelWriter pw = wr.getPixelWriter();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    pw.setArgb(x, y, image.getRGB(x, y));
                }
            }
        }
        return wr;
    }

    public void setPeerHandle(PeerHandle peerHandle) {
        this.peerHandle = peerHandle;
    }

    public void setIncomingCall(String callerName, String callerIp, int callerVoicePort) {
        this.remoteName = callerName;
        this.remoteIp = callerIp;
        this.remoteVoicePort = callerVoicePort;
        this.isIncoming = true;

        contactName.setText(callerName + " đang gọi...");

        // show accept / reject, hide in-call controls until user accepts
        if (acceptButton != null)
            acceptButton.setVisible(true);
        if (rejectButton != null)
            rejectButton.setVisible(true);

        if (muteButton != null)
            muteButton.setVisible(false);
        if (videoButton != null)
            videoButton.setVisible(false);
        if (shareScreenButton != null)
            shareScreenButton.setVisible(false);
        if (endCallButton != null)
            endCallButton.setVisible(false);
    }

    /**
     * Configure UI for outgoing call (caller side): show "Đang gọi..." and in-call
     * controls.
     * KHÔNG start timer, chỉ hiển thị "Đang gọi..." cho đến khi nhận CALL_ACCEPT.
     */
    public void setOutgoingCall(String calleeName) {
        this.remoteName = calleeName;
        this.isIncoming = false;
        this.isInCall = false;

        if (contactName != null) {
            contactName.setText("Đang gọi " + calleeName + "...");
        }

        // Ẩn timer và các controls trong cuộc gọi
        if (callDuration != null)
            callDuration.setVisible(false);
        showInCallControls(false);

        // Đảm bảo timer không chạy
        if (callDurationTimer != null) {
            callDurationTimer.stop();
        }
    }

    /**
     * Chuyển từ trạng thái "Đang gọi..." sang "Đang trong cuộc gọi" với timer.
     * Dùng cho bên A khi nhận CALL_ACCEPT hoặc bên B sau khi accept.
     */
    public void showInCall(String peerName) {
        this.remoteName = peerName;
        this.isInCall = true;

        if (contactName != null) {
            contactName.setText(peerName);
        }

        // Hiển thị timer và controls
        if (callDuration != null)
            callDuration.setVisible(true);
        showInCallControls(true);
        startCallDurationTimer();

        // Bắt đầu capture video nếu là video call
        if ("video".equals(callType)) {
            startVideoCapture();
        }
    }

    /**
     * Cập nhật UI từ trạng thái "Đang gọi..." sang "Đang trong cuộc gọi" với timer.
     * Dùng cho bên A khi nhận CALL_ACCEPT.
     */
    public void transitionToInCall() {
        showInCall(remoteName);
    }

    private void startCallDurationTimer() {
        if (callDurationTimer != null) {
            callDurationTimer.stop();
        }
        durationInSeconds = 0;
        updateCallDuration();
        callDurationTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateCallDuration()));
        callDurationTimer.setCycleCount(Timeline.INDEFINITE);
        callDurationTimer.play();
    }

    private void updateCallDuration() {
        durationInSeconds++;
        int minutes = durationInSeconds / 60;
        int seconds = durationInSeconds % 60;
        callDuration.setText(String.format("%02d:%02d", minutes, seconds));
    }

    @FXML
    private void onToggleMute() {
        isMuted = !isMuted;
        muteButton.setText(isMuted ? "Unmute" : "Mute");
        // Toggle mute audio in peer handle
    }

    @FXML
    private void onToggleVideo() {
        isVideoOff = !isVideoOff;
        videoButton.setText(isVideoOff ? "Start Video" : "Stop Video");

        // Toggle video feed on/off
        if (isVideoOff) {
            stopVideoCapture();
            if (localVideoFeed != null) {
                localVideoFeed.setVisible(false);
            }
        } else {
            if (localVideoFeed != null) {
                localVideoFeed.setVisible(true);
            }
            startVideoCapture();
        }
    }

    @FXML
    private void onToggleScreenSharing() {
        isScreenSharing = !isScreenSharing;
        shareScreenButton.setText(isScreenSharing ? "Stop Screen Sharing" : "Start Screen Sharing");
        // Handle screen sharing functionality
    }

    @FXML
    private void onEndCall() {
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
    private void onClose() {
        closeWindow();
    }

    @FXML
    private void onAcceptCall() {
        if (peerHandle != null && remoteName != null) {
            peerHandle.acceptCall(remoteName, remoteIp, remoteVoicePort);
        }

        isIncoming = false;
        isInCall = true;

        if (contactName != null && remoteName != null) {
            contactName.setText(remoteName);
        }

        showInCallControls(true);
        startCallDurationTimer();
    }

    @FXML
    private void onRejectCall() {
        // Hiện tại chưa có signaling CALL_REJECT, chỉ cần đóng cửa sổ.
        if (callDurationTimer != null) {
            callDurationTimer.stop();
        }
        closeWindow();
    }

    private void showInCallControls(boolean fromIncoming) {
        // hide incoming buttons
        if (acceptButton != null)
            acceptButton.setVisible(false);
        if (rejectButton != null)
            rejectButton.setVisible(false);

        // show basic in-call controls
        if (muteButton != null)
            muteButton.setVisible(true);
        if (endCallButton != null)
            endCallButton.setVisible(true);

        // video/screen buttons only visible for non-voice types
        boolean isVideoLike = "video".equals(callType) || "screen".equals(callType);
        if (videoButton != null)
            videoButton.setVisible(isVideoLike);
        if (shareScreenButton != null)
            shareScreenButton.setVisible("screen".equals(callType));
    }

    private void closeWindow() {
        // Dừng video capture trước khi đóng
        stopVideoCapture();

        if (contactName != null && contactName.getScene() != null) {
            Stage stage = (Stage) contactName.getScene().getWindow();
            if (stage != null) {
                stage.close();
            }
        }
        // clear active instance when window is closed
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    /**
     * Called from outside (MainController) when remote side sends CALL_END.
     * Ensures the active modal is closed on the callee/caller side as well.
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
