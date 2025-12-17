package org.example.peer_chat.ui.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

public class InfoPanelController {

    @FXML private Text contactName;
    @FXML private Text contactAvatar;
    @FXML private Text contactStatus;
    @FXML private Text contactMemberCount;

    @FXML private Button closeButton;
    @FXML private Button muteButton;
    @FXML private Button pinButton;
    @FXML private Button addMemberButton;
    @FXML private Button groupSettingsButton;
    @FXML private Button viewMembersButton;
    @FXML private Button viewPostsButton;
    @FXML private Button viewMediaButton;
    @FXML private Button viewFilesButton;
    @FXML private Button viewLinksButton;
    @FXML private Button securitySettingsButton;
    @FXML private Button messageExpirationButton;
    @FXML private Button hideChatButton;
    @FXML private Button reportUserButton;
    @FXML private Button deleteChatHistoryButton;
    @FXML private Button leaveGroupButton;

    @FXML private StackPane infoPanelRoot; // root StackPane in info-panel.fxml

    public void init(String name, String status, String avatar, int memberCount) {
        contactName.setText(name);
        contactStatus.setText(status);
        contactAvatar.setText(avatar);
        contactMemberCount.setText(memberCount + " members");
    }

    @FXML
    private void onClose() {
        if (infoPanelRoot != null) {
            infoPanelRoot.setVisible(false);
            infoPanelRoot.setManaged(false);
        }
    }

    @FXML
    private void onMuteNotifications() {
        // Mute notifications logic
    }

    @FXML
    private void onPinChat() {
        // Pin chat logic
    }

    @FXML
    private void onAddMember() {
        // Add member logic (for groups)
    }

    @FXML
    private void onGroupSettings() {
        // Group settings logic
    }

    @FXML
    private void onViewMembers() {
        // Show group members
    }

    @FXML
    private void onViewPosts() {
        // Show group posts
    }

    @FXML
    private void onViewMedia() {
        // Show media
    }

    @FXML
    private void onViewFiles() {
        // Show files
    }

    @FXML
    private void onViewLinks() {
        // Show links
    }

    @FXML
    private void onSecuritySettings() {
        // Show security settings
    }

    @FXML
    private void onMessageExpiration() {
        // Show message expiration settings
    }

    @FXML
    private void onHideChat() {
        // Hide the chat logic
    }

    @FXML
    private void onReportUser() {
        // Report user logic
    }

    @FXML
    private void onDeleteChatHistory() {
        // Delete chat history logic
    }

    @FXML
    private void onLeaveGroup() {
        // Leave group logic
    }
}
