package org.example.peer_chat;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

public class AppTestUIIT extends ApplicationTest {

    private VBox root;

    @Override
    public void start(Stage stage) {
        root = new VBox(10);

        Label label = new Label("Hello, JavaFX!");
        Button button = new Button("Click me");

        button.setOnAction(e -> label.setText("Button clicked"));

        root.getChildren().addAll(label, button);

        Scene scene = new Scene(root, 300, 200);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void testLabelTextInitially() {
        verifyThat(".label", hasText("Hello, JavaFX!"));
    }

    @Test
    void testButtonClickChangesLabelText() {
        Button button = lookup(".button").queryButton();
        interact(button::fire);
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat(".label", hasText("Button clicked"));
    }

    @Test
    void testButtonVisibility() {
        verifyThat(".button", isVisible());
    }
}
