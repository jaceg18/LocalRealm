package com.jaceg18.localrealm.core.ui;


import javafx.application.Platform;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.util.converter.IntegerStringConverter;

public final class SpinnerSetup {
    private SpinnerSetup() {}

    public static void applyMinMaxMemory(Spinner<Integer> minSpinner, Spinner<Integer> maxSpinner) {
        var minFactory = new IntegerSpinnerValueFactory(1, 128, 2, 1);
        var maxFactory = new IntegerSpinnerValueFactory(1, 128, 4, 1);

        java.util.function.UnaryOperator<TextFormatter.Change> digitsOnly = c ->
                c.getControlNewText().matches("\\d*") ? c : null;

        var minFormat = new TextFormatter<>(new IntegerStringConverter(), minFactory.getValue(), digitsOnly);
        var maxFormat = new TextFormatter<>(new IntegerStringConverter(), maxFactory.getValue(), digitsOnly);

        minFactory.valueProperty().bindBidirectional(minFormat.valueProperty());
        maxFactory.valueProperty().bindBidirectional(maxFormat.valueProperty());

        minSpinner.setValueFactory(minFactory);
        maxSpinner.setValueFactory(maxFactory);

        minSpinner.setEditable(true);
        maxSpinner.setEditable(true);

        minSpinner.getEditor().setTextFormatter(minFormat);
        maxSpinner.getEditor().setTextFormatter(maxFormat);

        minSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null && maxSpinner.getValue() != null && nv > maxSpinner.getValue()) {
                Platform.runLater(() -> maxSpinner.getValueFactory().setValue(nv));
            }
        });

        maxSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null && minSpinner.getValue() != null && nv < minSpinner.getValue()) {
                Platform.runLater(() -> minSpinner.getValueFactory().setValue(nv));
            }
        });
    }
}
