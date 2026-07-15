package com.almaz.mig180.wirespeed;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Опорная таблица режимов для проволоки 0.8 мм (сталь), Алмаз MIG180.
    // толщина мм / ток А / подача м/мин
    private static final double[] THICKNESS = {1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0};
    private static final double[] CURRENT   = {45,  65,  90,  105, 120, 140, 160};
    private static final double[] WIRE_SPEED = {3.0, 4.0, 5.0, 5.8, 6.5, 8.0, 9.5};

    private SeekBar seekThickness;
    private Switch switchGas;
    private TextView textThickness, textCurrent, textVoltage, textSpeed, textGasFlow, textHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        seekThickness = findViewById(R.id.seekThickness);
        switchGas = findViewById(R.id.switchGas);
        textThickness = findViewById(R.id.textThickness);
        textCurrent = findViewById(R.id.textCurrent);
        textVoltage = findViewById(R.id.textVoltage);
        textSpeed = findViewById(R.id.textSpeed);
        textGasFlow = findViewById(R.id.textGasFlow);
        textHint = findViewById(R.id.textHint);

        seekThickness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                recalc();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        switchGas.setOnCheckedChangeListener((buttonView, isChecked) -> recalc());

        recalc();
    }

    private void recalc() {
        // SeekBar: 0..40 -> 1.0..5.0 мм с шагом 0.1
        double thickness = 1.0 + seekThickness.getProgress() / 10.0;
        boolean co2 = switchGas.isChecked();

        double current = interpolate(THICKNESS, CURRENT, thickness);
        double speed = interpolate(THICKNESS, WIRE_SPEED, thickness);
        double voltage = 14.0 + 0.05 * current;

        if (co2) {
            // В чистом CO2 дуга жёстче: напряжение выше, подачу немного увеличиваем
            voltage += 1.5;
            speed *= 1.05;
        }

        double gasFlow = 8.0 + (thickness - 1.0);

        textThickness.setText(String.format(Locale.US, "%.1f мм", thickness));
        textCurrent.setText(String.format(Locale.US, "%.0f А", current));
        textVoltage.setText(String.format(Locale.US, "%.1f В", voltage));
        textSpeed.setText(String.format(Locale.US, "%.1f м/мин", speed));
        textGasFlow.setText(String.format(Locale.US, "%.0f–%.0f л/мин", gasFlow, gasFlow + 2));

        if (thickness >= 4.0) {
            textHint.setText(R.string.hint_thick);
        } else if (thickness <= 1.5) {
            textHint.setText(R.string.hint_thin);
        } else {
            textHint.setText(R.string.hint_default);
        }
    }

    private static double interpolate(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) return ys[0];
        if (x >= xs[xs.length - 1]) return ys[ys.length - 1];
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + t * (ys[i] - ys[i - 1]);
            }
        }
        return ys[ys.length - 1];
    }
}
