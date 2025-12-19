package org.example.peer_chat.ui.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.peer_chat.CallRecord;
import org.example.peer_chat.ChatDb;
import org.example.peer_chat.MessageListener;
import org.example.peer_chat.PeerHandle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.peer_chat.SoundManager;

public class MainController implements MessageListener {

    @FXML
    private Label currentUserLabel;
    @FXML
    private Label selectedContactLabel;
    @FXML
    private StackPane videoCallModal;
    @FXML
    private SidebarController sidebarRootController;
    @FXML
    private ChatAreaController chatAreaRootController;

    private PeerHandle peer;
    private ChatDb chatDb;
    private String currentUser;
    private final Map<String, Long> callStartTimes = new HashMap<>();
    private final Map<String, Boolean> callIsVideo = new HashMap<>();
    private Stage callStage; // dùng chung để hiển thị popup gọi thoại/video
    private Stage receiveCallStage; // Lưu reference đến ReceiveCall window để đóng khi accept
    private PeerHandle peerHandle;
    private Runnable onLogout;

    // Map groupName -> groupId (tạm thời, giả định tên nhóm là duy nhất trên 1 máy)
    private final Map<String, String> groupNameToId = new HashMap<>();

    public void init(PeerHandle peer, String currentUser, ChatDb chatDb) {
        this.peer = peer;
        this.currentUser = currentUser;
        this.chatDb = chatDb;

        if (currentUserLabel != null) {
            currentUserLabel.setText(currentUser);
        }

        // init sidebar with contacts from discovery
        if (sidebarRootController != null && peer != null) {
            // Đúng: truyền chatDb để SidebarController tự load all users
            sidebarRootController.init(currentUser, null, chatDb);
            // Truyền PeerHandle để SidebarController dùng cho Create Group
            sidebarRootController.setPeer(peer);
            sidebarRootController.setOnContactSelected(this::onContactSelected);
            sidebarRootController.setOnGroupSelected(this::onGroupSelected);
            sidebarRootController.setOnLogout(() -> {
                // Xóa user khỏi peer list
                if (peer != null) {
                    peer.broadcastOffline();
                    peer.removePeer(currentUser);
                }

                // Chuyển màu offline
                sidebarRootController.updateUserStatus(currentUser, false);

                // Gọi callback từ AppTestUI để quay về login
                if (onLogout != null) {
                    onLogout.run();
                }
            });
            startSidebarAutoRefresh();
        }

        // register listener to receive events from core and push to UI controllers
        if (peer != null) {
            peer.setListener(this);
        }

        // Sau khi sidebar được init, load các group đã tham gia từ DB để hiển thị ngay
        // khi online
        if (chatDb != null && sidebarRootController != null) {
            List<org.example.peer_chat.GroupInfo> groups = chatDb.loadGroupsForUser(currentUser);
            groupNameToId.clear();
            for (org.example.peer_chat.GroupInfo g : groups) {
                groupNameToId.put(g.getName(), g.getId());
            }
            sidebarRootController.setGroups(new ArrayList<>(groupNameToId.keySet()));
        }
    }

    public void setOnLogoutCallback(Runnable callback) {
        this.onLogout = callback;
    }

    private void onLogoutRequested() {
        if (onLogout != null) {
            onLogout.run();
        }
    }

    private void onContactSelected(String contactName) {
        if (selectedContactLabel != null) {
            selectedContactLabel.setText(contactName);
        }
        if (chatAreaRootController != null && peer != null && chatDb != null) {
            chatAreaRootController.init(peer, currentUser, contactName, chatDb);
        }
    }

    /**
     * Được gọi khi người dùng chọn một group ở sidebar.
     */
    private void onGroupSelected(String groupName) {
        String groupId = groupNameToId.get(groupName);
        if (groupId == null) {
            System.out.println("[MainController] Unknown group selected: " + groupName);
            return;
        }

        if (chatAreaRootController != null && peer != null && chatDb != null) {
            chatAreaRootController.initGroup(peer, currentUser, groupId, groupName, chatDb);
        }
    }

    @Override
    public void onMessage(String sender, String msg) {
        // Play beep
        SoundManager.getInstance().playMessageSound();
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
    public void onGroupFileReceived(String groupId, String from, String filename, String path, long size) {
        SoundManager.getInstance().playMessageSound();
        Platform.runLater(() -> {
            if (chatAreaRootController != null) {
                chatAreaRootController.onIncomingGroupFile(groupId, from, filename, path, size);
            }
        });
    }

    @Override
    public void onIncomingCall(String callerName, String callerIp, int callerVoicePort) {
        SoundManager.getInstance().playIncomingCallSound();
        Platform.runLater(() -> {
            if (chatAreaRootController != null) {
                chatAreaRootController.setCurrentCallVideo(false);
            }
            showReceiveCallWindow(callerName, callerIp, callerVoicePort, false);
        });
    }

    @Override
    public void onIncomingVideoCall(String callerName, String callerIp, int callerVoicePort) {
        SoundManager.getInstance().playIncomingCallSound();
        Platform.runLater(() -> {
            if (chatAreaRootController != null) {
                chatAreaRootController.setCurrentCallVideo(true);
            }
            showReceiveCallWindow(callerName, callerIp, callerVoicePort, true);
        });
    }

    @Override
    public void onVoiceCallStarted(String peerName) {
        SoundManager.getInstance().stopIncomingCallSound();
        Platform.runLater(() -> {
            // Đóng popup nhận cuộc gọi (nếu có)
            closeReceiveCallWindow();

            // Ưu tiên để ChatAreaController cập nhật window "Đang gọi..." thành "Đang trong
            // cuộc gọi"
            // để KHÔNG mở thêm 1 cửa sổ voice call mới cho bên gọi.
            if (chatAreaRootController != null) {
                chatAreaRootController.onCallAccepted(peerName);
            } else {
                // Fallback an toàn nếu vì lý do nào đó ChatAreaController chưa sẵn sàng
                showVoiceCallWindow(peerName);
            }

            onCallStarted(peerName, false);
        });
    }

    @Override
    public void onVideoCallStarted(String peerName) {
        SoundManager.getInstance().stopIncomingCallSound();
        Platform.runLater(() -> {
            // Đóng popup nhận cuộc gọi (nếu có)
            closeReceiveCallWindow();

            // Ưu tiên cho ChatAreaController cập nhật window "Đang gọi..." thành "Đang
            // trong cuộc gọi"
            // để KHÔNG mở thêm 1 cửa sổ video call mới cho bên gọi.
            if (chatAreaRootController != null) {
                chatAreaRootController.onCallAccepted(peerName);
            } else {
                // Fallback an toàn nếu vì lý do nào đó ChatAreaController chưa sẵn sàng
                showVideoCallWindow(peerName);
            }

            onCallStarted(peerName, true);
        });
    }

    public void onCallStarted(String peerName, boolean isVideo) {
        callStartTimes.put(peerName, System.currentTimeMillis());
        callIsVideo.put(peerName, isVideo);
    }

    @Override
    public void onCallEnded(String peerName) {
        Platform.runLater(() -> {
            long startTs = callStartTimes.getOrDefault(peerName, System.currentTimeMillis());
            long duration = (System.currentTimeMillis() - startTs) / 1000; // giây
            if (chatDb != null) {
                // success = true vì đây là cuộc gọi kết thúc bình thường
                CallRecord record = new CallRecord(
                        currentUser,
                        peerName,
                        startTs, // start timestamp
                        duration, // duration seconds
                        true, // success
                        callIsVideo.getOrDefault(peerName, false) // voice/video
                );
                chatDb.insertCallRecord(record);
            }

            callStartTimes.remove(peerName); // cleanup
            callIsVideo.remove(peerName);
            VideoCallModalController.closeActiveOnRemoteEnded();
            VoiceCallController.closeActiveOnRemoteEnded();

            if (chatAreaRootController != null) {
                chatAreaRootController.closeCallWindows();
            }

            closeCallWindow();
            showCallEndedPopup(peerName);
        });
    }

    public void onCallRejected(String peerName) {
        Platform.runLater(() -> {
            long startTs = callStartTimes.getOrDefault(peerName, System.currentTimeMillis());

            if (chatDb != null) {
                CallRecord record = new CallRecord(
                        currentUser,
                        peerName,
                        startTs,
                        0, // duration 0 giây vì bị từ chối
                        false, // success = false
                        callIsVideo.getOrDefault(peerName, false));
                chatDb.insertCallRecord(record);
            }

            callStartTimes.remove(peerName);
            callIsVideo.remove(peerName);

            VoiceCallController.closeActiveOnRemoteEnded();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Cuộc gọi bị từ chối");
            alert.setHeaderText(null);
            alert.setContentText(peerName + " đã từ chối cuộc gọi.");
            alert.showAndWait();
        });
    }

    @Override
    public void onRemoteVideoOn(String peerName) {
        Platform.runLater(() -> {
            VideoCallModalController.setRemoteVideoEnabled(true);
        });
    }

    @Override
    public void onRemoteVideoOff(String peerName) {
        Platform.runLater(() -> {
            VideoCallModalController.setRemoteVideoEnabled(false);
        });
    }

    // ====== GROUP CHAT CALLBACKS (INVITE / CREATE / MSG) ======

    @Override
    public void onGroupInviteReceived(String groupId, String groupName, String owner, List<String> members) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle("Lời mời tham gia nhóm");
            alert.setHeaderText(owner + " mời bạn vào nhóm \"" + groupName + "\"");
            alert.setContentText("Thành viên: " + String.join(", ", members));

            Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
            Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
            ok.setText("Đồng ý");
            cancel.setText("Từ chối");

            alert.showAndWait().ifPresent(result -> {
                if (peer == null)
                    return;
                if (result == ButtonType.OK) {
                    peer.sendToByName(owner, "GROUP_INVITE_ACCEPT|" + groupId + "|" + currentUser);
                } else {
                    peer.sendToByName(owner, "GROUP_INVITE_REJECT|" + groupId + "|" + currentUser);
                }
            });
        });
    }

    @Override
    public void onGroupCreated(String groupId, String groupName, String owner, List<String> members) {
        Platform.runLater(() -> {
            // Lưu lại mapping groupName -> groupId và cập nhật sidebar
            groupNameToId.put(groupName, groupId);
            if (sidebarRootController != null) {
                sidebarRootController.setGroups(new ArrayList<>(groupNameToId.keySet()));
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Nhóm mới");
            alert.setHeaderText("Đã tạo nhóm \"" + groupName + "\"");
            alert.setContentText("Thành viên: " + String.join(", ", members));
            alert.showAndWait();
        });
    }

    @Override
    public void onGroupMessage(String groupId, String from, String content) {
        SoundManager.getInstance().playMessageSound();
        Platform.runLater(() -> {
            if (chatAreaRootController != null) {
                chatAreaRootController.onIncomingGroupMessage(groupId, from, content);
            }
        });
    }

    @Override
    public void onGroupMemberLeft(String groupId, String member) {
        Platform.runLater(() -> {
            if (chatDb != null) {
                chatDb.insertGroupMessage(groupId, "SYSTEM", member + " đã rời nhóm");
            }
            if (member.equals(currentUser)) {
                String oldName = null;
                for (Map.Entry<String, String> e : groupNameToId.entrySet()) {
                    if (e.getValue().equals(groupId)) {
                        oldName = e.getKey();
                        break;
                    }
                }
                if (oldName != null) {
                    groupNameToId.remove(oldName);
                    if (sidebarRootController != null) {
                        sidebarRootController.setGroups(new ArrayList<>(groupNameToId.keySet()));
                    }
                }
            }
            if (chatAreaRootController != null) {
                chatAreaRootController.onGroupSystemMessage(groupId, member + " đã rời nhóm");
            }
        });
    }

    @Override
    public void onGroupRenamed(String groupId, String newName) {
        Platform.runLater(() -> {
            if (chatDb != null) {
                chatDb.insertGroupMessage(groupId, "SYSTEM", "Tên nhóm đã đổi thành \"" + newName + "\"");
            }
            String oldName = null;
            for (Map.Entry<String, String> e : groupNameToId.entrySet()) {
                if (e.getValue().equals(groupId)) {
                    oldName = e.getKey();
                    break;
                }
            }
            if (oldName != null) {
                groupNameToId.remove(oldName);
            }
            groupNameToId.put(newName, groupId);
            if (sidebarRootController != null) {
                sidebarRootController.setGroups(new ArrayList<>(groupNameToId.keySet()));
            }
            if (chatAreaRootController != null) {
                chatAreaRootController.updateGroupHeader(newName);
            }
            if (chatAreaRootController != null) {
                chatAreaRootController.onGroupSystemMessage(groupId, "Tên nhóm đã đổi thành \"" + newName + "\"");
            }
        });
    }

    @Override
    public void onGroupMembersChanged(String groupId) {
        Platform.runLater(() -> {
            if (chatDb == null)
                return;
            List<String> members = chatDb.getGroupMembers(groupId);
            if (!members.contains(currentUser)) {
                String oldName = null;
                for (Map.Entry<String, String> e : groupNameToId.entrySet()) {
                    if (e.getValue().equals(groupId)) {
                        oldName = e.getKey();
                        break;
                    }
                }
                if (oldName != null) {
                    groupNameToId.remove(oldName);
                    if (sidebarRootController != null) {
                        sidebarRootController.setGroups(new ArrayList<>(groupNameToId.keySet()));
                    }
                }
                return;
            }
            chatDb.insertGroupMessage(groupId, "SYSTEM", "Danh sách thành viên nhóm đã được cập nhật");
            if (chatAreaRootController != null) {
                chatAreaRootController.onGroupSystemMessage(groupId, "Danh sách thành viên nhóm đã được cập nhật");
            }
        });
    }

    private void startSidebarAutoRefresh() {
        Thread t = new Thread(() -> {
            while (peer != null) {
                try {
                    Thread.sleep(1500);
                    if (sidebarRootController != null) {
                        List<String> onlinePeers = peer.getPeerList();
                        Platform.runLater(() -> sidebarRootController.updateOnlinePeers(onlinePeers));
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }, "ui-sidebar-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void showReceiveCallWindow(String callerName, String callerIp, int callerVoicePort, boolean isVideoCall) {
        try {
            // Đóng window cũ nếu đang mở
            closeReceiveCallWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/ReceiveCall.fxml"));
            Parent root = loader.load();

            receiveCallStage = new Stage();
            receiveCallStage.initModality(Modality.APPLICATION_MODAL);
            String title = isVideoCall ? "Cuộc gọi video đến từ " + callerName : "Cuộc gọi đến từ " + callerName;
            receiveCallStage.setTitle(title);
            receiveCallStage.setScene(new Scene(root));

            ReceiveCallController controller = loader.getController();
            controller.init(peer, callerName, callerIp, callerVoicePort, isVideoCall);
            controller.setStage(receiveCallStage);

            receiveCallStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeReceiveCallWindow() {
        // Ensure ringing stops if window closed manually (though triggered in onCallEnded too)
        SoundManager.getInstance().stopIncomingCallSound();
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
            controller.init(peer, peerName, true);

            callStage = new Stage();
            // Voice call giữ kích thước mặc định, nhỏ hơn video call
            callStage.setScene(new Scene(root));
            callStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // Show error message to user
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to load voice call interface");
            alert.setContentText("The voice call interface could not be loaded: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void showVideoCallWindow(String peerName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/video-call-modal.fxml"));
            Parent root = loader.load();

            VideoCallModalController controller = loader.getController();
            controller.initVideoCall(peer, peerName);

            callStage = new Stage();
            callStage.setScene(new Scene(root));
            callStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // Show error message to user
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to load video call interface");
            alert.setContentText("The video call interface could not be loaded: " + e.getMessage());
            alert.showAndWait();
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
