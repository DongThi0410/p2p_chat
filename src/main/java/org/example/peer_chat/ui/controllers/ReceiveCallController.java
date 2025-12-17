package org.example.peer_chat.ui.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.example.peer_chat.PeerHandle;

public class ReceiveCallController {

    @FXML
    private Button acceptButton;
    @FXML
    private Button rejectButton;

    private PeerHandle peerHandle;
    private String callerName;
    private String callerIp;
    private int callerVoicePort;
    private boolean isVideoCall = false;

    public void init(PeerHandle peerHandle, String callerName, String callerIp, int callerVoicePort) {
        this.peerHandle = peerHandle;
        this.callerName = callerName;
        this.callerIp = callerIp;
        this.callerVoicePort = callerVoicePort;
        this.isVideoCall = false;
    }

    public void init(PeerHandle peerHandle, String callerName, String callerIp, int callerVoicePort, boolean isVideoCall) {
        this.peerHandle = peerHandle;
        this.callerName = callerName;
        this.callerIp = callerIp;
        this.callerVoicePort = callerVoicePort;
        this.isVideoCall = isVideoCall;
    }

    @FXML
    public void onAcceptCall(ActionEvent event) {
        // Chấp nhận cuộc gọi: báo core layer và mở UI voice-call-view hoặc video-call-modal
        if (peerHandle != null && callerName != null) {
            System.out.println("[ReceiveCallController] Accept pressed for caller=" + callerName + " (video=" + isVideoCall + ")");
            if (isVideoCall) {
                peerHandle.acceptVideoCall(callerName, callerIp, callerVoicePort);
            } else {
                peerHandle.acceptCall(callerName, callerIp, callerVoicePort);
            }
        }

        // UI gọi sẽ được mở khi onCallStarted trigger ở MainController; đóng popup nhận
        // cuộc gọi.
        closeWindow();
    }

    @FXML
    public void onRejectCall(ActionEvent event) {
        // Từ chối cuộc gọi: chỉ cần đóng UI (chưa gửi CALL_REJECT)
        closeWindow();
    }

    // Đóng hoàn toàn cửa sổ ReceiveCall hiện tại (dùng cho nút Hủy).
    private void closeWindow() {
        if (acceptButton != null && acceptButton.getScene() != null) {
            Stage stage = (Stage) acceptButton.getScene().getWindow();
            if (stage != null) {
                stage.close();
            }
        }
    }
}
