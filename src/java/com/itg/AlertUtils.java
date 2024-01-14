package com.itg;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class AlertUtils {

    public static void displayError(Stage stage, String title, String message) {
        if (stage == null){
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
