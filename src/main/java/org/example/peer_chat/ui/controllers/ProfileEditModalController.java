package org.example.peer_chat.ui.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

public class ProfileEditModalController {

    @FXML private TextField nameField;
    @FXML private TextField statusField;
    @FXML private TextArea bioField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private HBox avatarButtons;

    private String selectedAvatar = "🐱"; // Default avatar

    private Runnable onSaveCallback;
    private Runnable onCloseCallback;

    public void init(Runnable onSaveCallback, Runnable onCloseCallback) {
        this.onSaveCallback = onSaveCallback;
        this.onCloseCallback = onCloseCallback;

        // Set default values
        nameField.setText("John Doe");
        statusField.setText("Đang vui vẻ ✨");
        bioField.setText("Yêu mèo và thỏ 💕");

        // Setup avatar buttons, you can add more logic to handle selection
    }

    @FXML
    private void onSelectAvatar() {
        Button selectedButton = (Button) avatarButtons.getScene().getFocusOwner();
        selectedAvatar = selectedButton.getText(); // Set selected avatar
    }

    @FXML
    private void onNameChanged() {
        // Handle name change
    }

    @FXML
    private void onStatusChanged() {
        // Handle status change
    }

    @FXML
    private void onBioChanged() {
        // Handle bio change
    }

    @FXML
    private void onSave() {
        // Get updated data
        String name = nameField.getText();
        String status = statusField.getText();
        String bio = bioField.getText();

        // Call save callback with the new data
        if (onSaveCallback != null) onSaveCallback.run();

        // Close the modal
        if (onCloseCallback != null) onCloseCallback.run();
    }

    @FXML
    private void onClose() {
        // Close the modal without saving
        if (onCloseCallback != null) onCloseCallback.run();
    }
}
