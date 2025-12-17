package org.example.peer_chat.ui.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.peer_chat.ChatDb;
import org.example.peer_chat.MessageListener;
import org.example.peer_chat.PeerHandle;

import java.io.IOException;

public class MainController {

    @FXML
    private Label currentUserLabel;
    @FXML
    private Label selectedContactLabel;
    @FXML
    private StackPane videoCallModal;
    // Controllers from fx:include: fx:id + "Controller"
    @FXML
    private SidebarController sidebarRootController;
    @FXML
    private ChatAreaController chatAreaRootController;

    private PeerHandle peer;
    private ChatDb chatDb;
    private String currentUser;

    private boolean showVideoCall = false;
    private String callType = "video";
    private Stage callStage; // dùng chung để hiển thị popup gọi thoại/video
    private Stage receiveCallStage; // Lưu reference đến ReceiveCall window để đóng khi accept

    public void init(PeerHandle peer, String currentUser, ChatDb chatDb) {
        this.peer = peer;
        this.currentUser = currentUser;
        this.chatDb = chatDb;

        if (currentUserLabel != null) {
            currentUserLabel.setText(currentUser);
        }

        // init sidebar with contacts from discovery
        if (sidebarRootController != null && peer != null) {
            sidebarRootController.init(currentUser, this::onLogoutRequested, peer.getPeerList());
            sidebarRootController.setOnContactSelected(this::onContactSelected);
            startSidebarAutoRefresh();
        }

        // register listener to receive events from core and push to UI controllers
        if (peer != null) {
            peer.setListener(new MessageListener() {
                @Override
                public void onMessage(String sender, String msg) {
                    Platform.runLater(() -> {
                        if (chatAreaRootController != null) {
                            chatAreaRootController.onIncomingMessage(sender, msg);
                        }
                    });
                }

                @Override
                public void onFileReceived(String sender, String filename, String absolutePath, long size) {
                    Platform.runLater(() -> {
                        if (chatAreaRootController != null) {
                            chatAreaRootController.onIncomingFile(sender, filename, absolutePath, size);
                        }
                    });
                }

                @Override
                public void onIncomingCall(String callerName, String callerIp, int callerVoicePort) {
                    Platform.runLater(() -> {
                        // Đánh dấu là voice call trong ChatAreaController
                        if (chatAreaRootController != null) {
                            chatAreaRootController.setCurrentCallVideo(false);
                        }
                        showReceiveCallWindow(callerName, callerIp, callerVoicePort, false);
                    });
                }

                @Override
                public void onIncomingVideoCall(String callerName, String callerIp, int callerVoicePort) {
                    Platform.runLater(() -> {
                        // Đánh dấu là video call trong ChatAreaController
                        if (chatAreaRootController != null) {
                            chatAreaRootController.setCurrentCallVideo(true);
                        }
                        showReceiveCallWindow(callerName, callerIp, callerVoicePort, true);
                    });
                }

                @Override
                public void onCallStarted(String peerName) {
                    Platform.runLater(() -> {
                        // Đóng ReceiveCall window nếu đang mở (bên B sau khi accept)
                        closeReceiveCallWindow();
                        
                        // Cập nhật UI hiện tại nếu đang có window "Đang gọi..." mở (bên A)
                        // Hoặc mở window mới nếu chưa có (bên B sau khi accept)
                        if (chatAreaRootController != null) {
                            chatAreaRootController.onCallAccepted(peerName);
                        } else {
                            // Fallback: mở window mới nếu không có ChatAreaController
                            showVoiceCallWindow(peerName);
                        }
                    });
                }

                @Override
                public void onCallEnded(String peerName) {
                    Platform.runLater(() -> {
                        // Đóng tất cả các UI call đang mở
                        showVideoCall = false;
                        if (videoCallModal != null) {
                            videoCallModal.setVisible(false);
                        }
                        
                        // Đóng VideoCallModal nếu đang mở
                        VideoCallModalController.closeActiveOnRemoteEnded();
                        
                        // Đóng VoiceCallController nếu đang mở
                        VoiceCallController.closeActiveOnRemoteEnded();
                        
                        // Đóng các call windows được track trong ChatAreaController
                        if (chatAreaRootController != null) {
                            chatAreaRootController.closeCallWindows();
                        }
                        
                        // Đóng callStage nếu đang mở
                        closeCallWindow();
                        
                        // Hiển thị popup "Cuộc gọi đã kết thúc" sau khi đóng tất cả windows
                        showCallEndedPopup(peerName);
                    });
                }
            });
        }
    }

    @FXML
    private void onStartCall() {
        // optional handler if bound from FXML buttons
        if (chatAreaRootController != null) {
            chatAreaRootController.onCallVoice();
        }
    }

    @FXML
    private void onCloseCall() {
        showVideoCall = false;
        if (videoCallModal != null) {
            videoCallModal.setVisible(false);
        }
        closeCallWindow();
    }

    private void onContactSelected(String contactName) {
        if (selectedContactLabel != null) {
            selectedContactLabel.setText(contactName);
        }
        if (chatAreaRootController != null && peer != null && chatDb != null) {
            chatAreaRootController.init(peer, currentUser, contactName, chatDb);
        }
    }

    private void onLogoutRequested() {
        // TODO: implement navigation back to login if needed
    }

    private void startSidebarAutoRefresh() {
        Thread t = new Thread(() -> {
            while (peer != null) {
                try {
                    Thread.sleep(1500);
                    if (sidebarRootController != null) {
                        var peers = peer.getPeerList();
                        Platform.runLater(() -> sidebarRootController.updateContacts(peers));
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }, "ui-sidebar-refresh");
        t.setDaemon(true);
        t.start();
    }

    // ===== Call UI helpers =====
    private void showReceiveCallWindow(String callerName, String callerIp, int callerVoicePort, boolean isVideoCall) {
        try {
            // Đóng window cũ nếu đang mở
            closeReceiveCallWindow();
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/ReceiveCall.fxml"));
            Parent root = loader.load();

            ReceiveCallController controller = loader.getController();
            controller.init(peer, callerName, callerIp, callerVoicePort, isVideoCall);

            receiveCallStage = new Stage();
            receiveCallStage.initModality(Modality.APPLICATION_MODAL);
            String title = isVideoCall ? "Cuộc gọi video đến từ " + callerName : "Cuộc gọi đến từ " + callerName;
            receiveCallStage.setTitle(title);
            receiveCallStage.setScene(new Scene(root));
            
            // Đóng khi user đóng window
            receiveCallStage.setOnCloseRequest(e -> {
                receiveCallStage = null;
            });
            
            receiveCallStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void closeReceiveCallWindow() {
        if (receiveCallStage != null && receiveCallStage.isShowing()) {
            receiveCallStage.close();
            receiveCallStage = null;
        }
    }

    private void showVoiceCallWindow(String peerName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/voice-call-view.fxml"));
            Parent root = loader.load();
            VoiceCallController controller = loader.getController();
            controller.initIncomingAccepted(peer, peerName);

            callStage = new Stage();
            callStage.initModality(Modality.APPLICATION_MODAL);
            callStage.setTitle("Cuộc gọi với " + peerName);
            callStage.setScene(new Scene(root));
            callStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeCallWindow() {
        if (callStage != null) {
            callStage.close();
            callStage = null;
        }
    }
    
    private void showCallEndedPopup(String peerName) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Cuộc gọi đã kết thúc");

        Label lb = new Label("📵 Cuộc gọi với " + peerName + " đã kết thúc");
        lb.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Button ok = new Button("OK");
        ok.setOnAction(e -> popup.close());

        VBox root = new VBox(15, lb, ok);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));

        popup.setScene(new Scene(root, 300, 150));
        popup.show();
    }
}
