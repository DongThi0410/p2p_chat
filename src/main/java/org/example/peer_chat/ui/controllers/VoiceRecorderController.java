package org.example.peer_chat.ui.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.text.Text;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class VoiceRecorderController {

    @FXML private Text recordingStatus;
    @FXML private Text durationText;
    @FXML private Button cancelButton;
    @FXML private Button sendButton;
    @FXML private Circle micCircle;

    private boolean isRecording = false;
    private int duration = 0;  // Duration in seconds
    private Timeline timer;

    @FXML
    public void initialize() {
        // Initialize UI components and set up animation
        micCircle.setFill(Color.TRANSPARENT);
        micCircle.setStroke(Color.WHITE);

        // Start recording if required
        startRecording();
    }

    private void startRecording() {
        isRecording = true;
        recordingStatus.setText("Recording...");

        // Start a timer to simulate recording duration
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateRecordingDuration()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void updateRecordingDuration() {
        duration++;
        int minutes = duration / 60;
        int seconds = duration % 60;
        durationText.setText(String.format("%02d:%02d", minutes, seconds));

        // Simulate mic animation
        double scale = Math.random() * 1.2 + 1;
        micCircle.setScaleX(scale);
        micCircle.setScaleY(scale);
    }

    @FXML
    private void onCancelRecording() {
        // Stop the recording and close the modal
        isRecording = false;
        timer.stop();
        // Close the modal or reset states as needed
    }

    @FXML
    private void onSendRecording() {
        // Send the recording and close the modal
        if (isRecording) {
            // Send the recorded data (duration or file)
            System.out.println("Sending recording of duration: " + duration + " seconds.");
            isRecording = false;
            timer.stop();
            // Close modal or transition to another screen
        }
    }

    @FXML
    private void onClose() {
        // Close the modal without saving
        isRecording = false;
        timer.stop();
        // Close the modal or reset states as needed
    }
}
