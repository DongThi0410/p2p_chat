package org.example.peer_chat.ui.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.peer_chat.ChatDb;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class SidebarController {

    @FXML
    private Label userAvatar;
    @FXML
    private Label userNameLabel;
    @FXML
    private Circle statusIndicator;
    @FXML
    private ListView<String> contactsListView;
    @FXML
    private TextField searchField;

    private final ObservableList<String> allUsers = FXCollections.observableArrayList();

    @FXML
    private Button logoutButton;
    @FXML
    private Button addFriendButton;
    @FXML
    private Button createGroupButton;
    @FXML
    private Button editProfileButton;
    @FXML
    private javafx.scene.layout.HBox addFriendForm;
    @FXML
    private TextField friendIdField;

    private String currentUser;
    private Runnable onLogout;
    private Consumer<String> onContactSelected;
    private final Map<String, HBox> userRows = new HashMap<>();
    private final Map<String, Boolean> userOnlineStatus = new HashMap<>();

    public void init(String currentUser, Runnable onLogout, ChatDb db) {
        this.currentUser = currentUser;
        this.onLogout = onLogout;
        userNameLabel.setText(currentUser);
        statusIndicator.setFill(Color.GREEN);
        // Lấy tất cả user từ DB
        List<String> users = db.getAllUsers();
        // Xóa tên currentUser ra nếu muốn
        users.remove(currentUser);

        allUsers.setAll(users);
        allUsers.remove(currentUser);
        for (String u : allUsers) {
            userOnlineStatus.put(u, false); // offline mặc định
        }
        contactsListView.setItems(allUsers);
        contactsListView.setCellFactory(lv -> new ListCell<String>() {
            private final HBox row = new HBox(5);
            private final Circle circle = new Circle(5);
            private final Label nameLabel = new Label();

            {
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(circle, nameLabel);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item);
                    boolean online = userOnlineStatus.getOrDefault(item, false);
                    circle.setFill(online ? Color.GREEN : Color.GRAY);
                    setGraphic(row);
                }
            }
        });
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String kw = newVal == null ? "" : newVal.toLowerCase();
            contactsListView.setItems(allUsers.filtered(u -> u.toLowerCase().contains(kw)));
        });
    }

    private void filterContacts(String keyword) {
        if (keyword == null) keyword = "";
        String lower = keyword.toLowerCase();
        contactsListView.setItems(allUsers.filtered(u -> u.toLowerCase().contains(lower)));
    }

    public void updateContacts(List<String> users) {
        Platform.runLater(() -> {
            allUsers.setAll(users);
            filterContacts(searchField.getText());
        });
    }

    public void updateOnlinePeers(List<String> onlinePeers) {
        Set<String> onlineSet = new HashSet<>(onlinePeers);
        for (String u : allUsers) {
            updateUserStatus(u, onlineSet.contains(u));
        }
    }

    public void updateUserStatus(String username, boolean online) {
        Platform.runLater(() -> {
            userOnlineStatus.put(username, online);
            contactsListView.refresh();
        });
    }

    @FXML
    private void onLogout() {
        if (onLogout != null) onLogout.run();
    }


    public List<String> getAllUsers() {
        return new ArrayList<>(allUsers);
    }


    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }



    public void setOnContactSelected(Consumer<String> onContactSelected) {
        this.onContactSelected = onContactSelected;
        contactsListView.setOnMouseClicked(e -> {
            String selected = contactsListView.getSelectionModel().getSelectedItem();
            if (selected != null && onContactSelected != null) {
                onContactSelected.accept(selected);
            }
        });
    }

    @FXML
    private void onSearchChanged() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        if (contactsListView.getItems() == null) return;

        contactsListView.setItems(contactsListView.getItems().filtered(item -> item.toLowerCase().contains(keyword)));
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
