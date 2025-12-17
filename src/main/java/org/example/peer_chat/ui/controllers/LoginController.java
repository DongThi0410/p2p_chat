package org.example.peer_chat.ui.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.Random;
import java.util.function.Consumer;

public class LoginController {

    @FXML private Pane floatingLayer;
    @FXML private Pane starLayer;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private javafx.scene.layout.VBox confirmBox;

    @FXML private Button submitBtn;
    @FXML private Button toggleBtn;

    @FXML private Label catEmoji;
    @FXML private Label heartEmoji;
    @FXML private Label rabbitEmoji;

    private final Random rnd = new Random();
    private boolean isRegister = false;

    private Consumer<String> onLogin;

    public void setOnLogin(Consumer<String> onLogin) {
        this.onLogin = onLogin;
    }

    @FXML
    private void initialize() {
        // confirmBox hidden by default
        confirmBox.setManaged(false);
        confirmBox.setVisible(false);

        // cute pulse heart
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1), heartEmoji);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.2);   pulse.setToY(1.2);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();

        // hover scale for cat/rabbit
        addHoverScale(catEmoji);
        addHoverScale(rabbitEmoji);

        // background animations
        spawnFloatingAnimals();
        spawnStars();
    }

    private void addHoverScale(Label lbl) {
        lbl.setOnMouseEntered(e -> { lbl.setScaleX(1.2); lbl.setScaleY(1.2); lbl.setRotate(lbl == catEmoji ? 10 : -10); });
        lbl.setOnMouseExited(e -> { lbl.setScaleX(1.0); lbl.setScaleY(1.0); lbl.setRotate(0); });
    }

    @FXML
    private void onToggleMode() {
        isRegister = !isRegister;

        confirmBox.setManaged(isRegister);
        confirmBox.setVisible(isRegister);

        submitBtn.setText(isRegister ? "🐰 Đăng ký ngay!" : "🐱 Đăng nhập thôi!");
        toggleBtn.setText(isRegister ? "🐱 Đã có tài khoản? Đăng nhập" : "🐰 Chưa có tài khoản? Đăng ký");
    }

    @FXML
    private void onSubmit() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        if (username.isEmpty()) return;

        if (isRegister) {
            // tối giản: chỉ check confirm == password (bạn nối vào core sau)
            String p1 = passwordField.getText() == null ? "" : passwordField.getText();
            String p2 = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();
            if (!p1.equals(p2)) {
                // bạn có thể show alert đẹp hơn sau
                new Alert(Alert.AlertType.WARNING, "Mật khẩu xác nhận không khớp 💔").show();
                return;
            }
        }

        if (onLogin != null) onLogin.accept(username);
    }

    private void spawnFloatingAnimals() {
        // 8 floating emoji like TSX
        for (int i = 0; i < 8; i++) {
            Label l = new Label(i % 2 == 0 ? "🐱" : "🐰");
            l.setStyle("-fx-font-size: 56px; -fx-opacity: 0.20;");
            floatingLayer.getChildren().add(l);

            double x = (i * 0.12) * 800; // sẽ ổn dù resize vì chỉ là effect
            l.setLayoutX(x);
            l.setLayoutY(800); // start bottom

            TranslateTransition moveUp = new TranslateTransition(Duration.seconds(15 + i * 2), l);
            moveUp.setFromY(0);
            moveUp.setToY(-1100);
            moveUp.setInterpolator(Interpolator.LINEAR);
            moveUp.setCycleCount(Animation.INDEFINITE);

            RotateTransition rot = new RotateTransition(Duration.seconds(15 + i * 2), l);
            rot.setFromAngle(0);
            rot.setToAngle(360);
            rot.setInterpolator(Interpolator.LINEAR);
            rot.setCycleCount(Animation.INDEFINITE);

            new ParallelTransition(moveUp, rot).play();
        }
    }

    private void spawnStars() {
        // 15 stars like TSX
        for (int i = 0; i < 15; i++) {
            Label star = new Label("⭐");
            star.setStyle("-fx-font-size: 18px;");
            starLayer.getChildren().add(star);

            star.setLayoutX((i * 0.07 % 1.0) * 900);
            star.setLayoutY((i * 0.13 % 1.0) * 700);

            TranslateTransition bob = new TranslateTransition(Duration.seconds(2 + i * 0.3), star);
            bob.setFromY(0);
            bob.setToY(-18);
            bob.setAutoReverse(true);
            bob.setCycleCount(Animation.INDEFINITE);
            bob.setInterpolator(Interpolator.EASE_BOTH);

            FadeTransition fade = new FadeTransition(Duration.seconds(2 + i * 0.3), star);
            fade.setFromValue(0.3);
            fade.setToValue(1.0);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.setInterpolator(Interpolator.EASE_BOTH);

            new ParallelTransition(bob, fade).play();
        }
    }
}
