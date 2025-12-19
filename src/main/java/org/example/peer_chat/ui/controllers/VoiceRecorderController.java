package org.example.peer_chat.ui.controllers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class VoiceRecorderController {

    @FXML private Text recordingStatus;
    @FXML private Text durationText;
    @FXML private Button cancelButton;
    @FXML private Button sendButton;
    @FXML private Circle micCircle;
    @FXML private Circle micHalo;

    // Waveform bars
    @FXML private Rectangle bar1;
    @FXML private Rectangle bar2;
    @FXML private Rectangle bar3;
    @FXML private Rectangle bar4;
    @FXML private Rectangle bar5;
    @FXML private Rectangle bar6;
    @FXML private Rectangle bar7;
    @FXML private Rectangle bar8;
    @FXML private Rectangle bar9;
    @FXML private Rectangle bar10;

    private boolean isRecording = false;
    private int duration = 0;
    private Timeline timer;
    private Timeline waveformAnimation;

    // Audio recording
    private TargetDataLine targetLine;
    private ByteArrayOutputStream audioData;
    private Thread recordingThread;
    private AudioFormat audioFormat;

    // Callback để gửi voice message
    private VoiceMessageCallback callback;
    private String currentUser;
    private String selectedContact;

    public interface VoiceMessageCallback {
        void onVoiceSend(String filePath, int durationSeconds);
    }

    public void init(String currentUser, String selectedContact, VoiceMessageCallback callback) {
        this.currentUser = currentUser;
        this.selectedContact = selectedContact;
        this.callback = callback;
    }

    @FXML
    public void initialize() {
        // Khởi tạo audio format
        audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100,  // sample rate
                16,     // bits per sample
                1,      // channels (mono)
                2,      // frame size
                44100,  // frame rate
                false   // big-endian
        );

        sendButton.setDisable(true);
        recordingStatus.setText("Nhấn micro để ghi âm");

        // Thiết lập click listener cho mic circle
        micCircle.setOnMouseClicked(e -> toggleRecording());
        micHalo.setOnMouseClicked(e -> toggleRecording());
    }

    private void toggleRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                recordingStatus.setText("❌ Microphone không khả dụng");
                return;
            }

            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(audioFormat);
            targetLine.start();

            audioData = new ByteArrayOutputStream();
            isRecording = true;
            duration = 0;

            // Bắt đầu thread ghi âm
            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioData.write(buffer, 0, bytesRead);
                    }
                }
            }, "voice-recording-thread");
            recordingThread.start();

            // Cập nhật UI
            recordingStatus.setText("🎙️ Đang ghi âm...");
            sendButton.setDisable(false);

            // Timer đếm thời gian
            timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                duration++;
                int minutes = duration / 60;
                int seconds = duration % 60;
                durationText.setText(String.format("%02d:%02d", minutes, seconds));
            }));
            timer.setCycleCount(Timeline.INDEFINITE);
            timer.play();

            // Animation waveform
            startWaveformAnimation();

        } catch (LineUnavailableException e) {
            recordingStatus.setText("❌ Không thể truy cập microphone");
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        isRecording = false;

        if (timer != null) {
            timer.stop();
        }
        if (waveformAnimation != null) {
            waveformAnimation.stop();
        }

        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        recordingStatus.setText("✅ Đã ghi xong - Nhấn Gửi");
        resetWaveform();
    }

    private void startWaveformAnimation() {
        Rectangle[] bars = {bar1, bar2, bar3, bar4, bar5, bar6, bar7, bar8, bar9, bar10};
        Random random = new Random();

        waveformAnimation = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            for (Rectangle bar : bars) {
                if (bar != null) {
                    double newHeight = 10 + random.nextDouble() * 30;
                    bar.setHeight(newHeight);
                }
            }

            // Mic pulse animation
            if (micCircle != null) {
                double scale = 1 + random.nextDouble() * 0.15;
                micCircle.setScaleX(scale);
                micCircle.setScaleY(scale);
            }
        }));
        waveformAnimation.setCycleCount(Timeline.INDEFINITE);
        waveformAnimation.play();
    }

    private void resetWaveform() {
        Rectangle[] bars = {bar1, bar2, bar3, bar4, bar5, bar6, bar7, bar8, bar9, bar10};
        double[] defaultHeights = {12, 20, 32, 18, 26, 16, 30, 22, 14, 24};

        for (int i = 0; i < bars.length; i++) {
            if (bars[i] != null) {
                bars[i].setHeight(defaultHeights[i]);
            }
        }

        if (micCircle != null) {
            micCircle.setScaleX(1);
            micCircle.setScaleY(1);
        }
    }

    @FXML
    private void onCancelRecording() {
        isRecording = false;

        if (timer != null) timer.stop();
        if (waveformAnimation != null) waveformAnimation.stop();
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        closeModal();
    }

    @FXML
    private void onSendRecording() {
        if (isRecording) {
            stopRecording();
        }

        if (audioData == null || audioData.size() == 0) {
            recordingStatus.setText("❌ Chưa có bản ghi");
            return;
        }

        // Lưu file WAV
        try {
            String fileName = "voice_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".wav";
            Path voiceDir = Path.of(System.getProperty("user.home"), "peer_chat_voices");
            Files.createDirectories(voiceDir);

            File outputFile = voiceDir.resolve(fileName).toFile();

            byte[] audioBytes = audioData.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream audioInputStream = new AudioInputStream(bais, audioFormat, audioBytes.length / audioFormat.getFrameSize());

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
            audioInputStream.close();

            System.out.println("[VoiceRecorder] Saved voice message: " + outputFile.getAbsolutePath());

            // Gọi callback để gửi file
            if (callback != null) {
                Platform.runLater(() -> callback.onVoiceSend(outputFile.getAbsolutePath(), duration));
            }

            closeModal();

        } catch (IOException e) {
            recordingStatus.setText("❌ Lỗi lưu file");
            e.printStackTrace();
        }
    }

    @FXML
    private void onClose() {
        onCancelRecording();
    }

    private void closeModal() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}
