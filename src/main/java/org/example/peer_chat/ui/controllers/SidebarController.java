package org.example.peer_chat.ui.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class SidebarController {

    @FXML private Label userAvatar;
    @FXML private Label userNameLabel;
    @FXML private Circle statusIndicator;
    @FXML private TextField searchField;
    @FXML private ListView<String> contactsListView;
    @FXML private Button logoutButton;
    @FXML private Button addFriendButton;
    @FXML private Button createGroupButton;
    @FXML private Button editProfileButton;
    @FXML private javafx.scene.layout.HBox addFriendForm;
    @FXML private TextField friendIdField;

    private String currentUser;
    private Runnable onLogout;
    private Consumer<String> onContactSelected;

    public void init(String currentUser, Runnable onLogout, List<String> contacts) {
        this.currentUser = currentUser;
        this.onLogout = onLogout;

        userNameLabel.setText(currentUser);
        statusIndicator.setFill(Color.GREEN); // Assuming "Online" status for now
        userAvatar.setText("🐱"); // Avatar could be dynamic

        contactsListView.getItems().setAll(contacts);
    }

    public void updateContacts(List<String> contacts) {
        if (contacts == null) return;
        contactsListView.getItems().setAll(contacts);
    }

    public void setOnContactSelected(Consumer<String> onContactSelected) {
        this.onContactSelected = onContactSelected;
    }

    @FXML
    private void onLogout() {
        if (onLogout != null) onLogout.run();
    }

    @FXML
    private void onSearchContacts() {
        // Logic for searching contacts
    }

    @FXML
    private void onAddFriend() {
        boolean show = !addFriendForm.isVisible();
        addFriendForm.setVisible(show);
        addFriendForm.setManaged(show);
    }

    @FXML
    private void onConfirmAddFriend() {
        String id = friendIdField.getText() == null ? "" : friendIdField.getText().trim();
        if (id.isEmpty()) {
            Alert alert = new Alert(AlertType.WARNING, "Nhập ID bạn bè trước nhé 🐱");
            alert.showAndWait();
            return;
        }

        // TODO: nối với logic thêm bạn thật
        Alert ok = new Alert(AlertType.INFORMATION, "Đã gửi lời mời kết bạn tới: " + id);
        ok.showAndWait();

        friendIdField.clear();
        addFriendForm.setVisible(false);
        addFriendForm.setManaged(false);
    }

    @FXML
    private void onCreateGroup() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Create Group");
        alert.setHeaderText(null);
        alert.setContentText("Chức năng tạo nhóm sẽ được nối với core sau.");
        alert.showAndWait();
    }

    @FXML
    private void onEditProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/profile-edit-modal.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Chỉnh sửa thông tin cá nhân");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // danh sách contact sẽ được truyền từ MainController qua init(),
    // không tự thêm mock data ở đây để phản ánh đúng peer online.
    public void initialize() {
        // no-op
    }

    @FXML
    private void onSelectContact() {
        String selectedContact = contactsListView.getSelectionModel().getSelectedItem();
        if (selectedContact != null && onContactSelected != null) {
            onContactSelected.accept(selectedContact);
        }
    }
}
