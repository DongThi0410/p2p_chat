package org.example.peer_chat.ui.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.example.peer_chat.ChatDb;

import java.util.Random;
import java.util.function.Consumer;

public class LoginController {

    @FXML
    private Pane floatingLayer;
    @FXML
    private Pane starLayer;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button submitBtn;
    @FXML
    private Button toggleBtn;

    @FXML
    private Label catEmoji;
    @FXML
    private Label heartEmoji;
    @FXML
    private Label rabbitEmoji;

    private final Random rnd = new Random();
    private boolean isLogin = false;
    private ChatDb chatDb;

    public void setChatDb(ChatDb chatDb) {
        this.chatDb = chatDb;
    }

    private Consumer<String> onLogin;

    @FXML
    private void onLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Vui lòng nhập đầy đủ thông tin").show();
            return;
        }

        boolean success = chatDb.loginUser(username, password);

        if (success) {
            if (onLogin != null) onLogin.accept(username); // callback sang MainView
        } else {
            new Alert(Alert.AlertType.ERROR, "Tên đăng nhập hoặc mật khẩu không đúng 💔").show();
        }
    }


    @FXML
    private void onRegister() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();


        if (username.isEmpty() || password.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Vui lòng nhập đầy đủ thông tin").show();
            return;
        }

        boolean success = chatDb.registerUser(username, password); // lưu vào DB
        new Alert(Alert.AlertType.WARNING, success ? "Đăng ký thành công!" : "Đăng ký thất bại").show();

    }

    public void setOnLogin(Consumer<String> onLogin) {
        this.onLogin = onLogin;
    }


    @FXML
    private void initialize() {

        // cute pulse heart
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1), heartEmoji);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.2);
        pulse.setToY(1.2);
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
        lbl.setOnMouseEntered(e -> {
            lbl.setScaleX(1.2);
            lbl.setScaleY(1.2);
            lbl.setRotate(lbl == catEmoji ? 10 : -10);
        });
        lbl.setOnMouseExited(e -> {
            lbl.setScaleX(1.0);
            lbl.setScaleY(1.0);
            lbl.setRotate(0);
        });
    }

    @FXML
    private void onToggleMode() {
        isLogin = !isLogin;


        submitBtn.setText(isLogin ? "🐰 Đăng ký ngay!" : "🐱 Đăng nhập thôi!");
        toggleBtn.setText(isLogin ? "🐱 Đã có tài khoản? Đăng nhập" : "🐰 Chưa có tài khoản? Đăng ký");
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
