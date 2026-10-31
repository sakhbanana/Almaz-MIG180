package com.almaz.mig180.wirespeed;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Таблица режимов с аппарата Алмаз MIG180, проволока 0.8 мм.
    // толщина мм / подача м/мин / напряжение В
    private static final double[] THICKNESS = {0.8, 1.0, 1.2, 1.5, 2.0, 3.0, 4.0, 5.0};

    // Смесь Ar+CO2
    private static final double[] MIX_SPEED = {2.6, 3.6, 3.4, 4.4, 5.6, 7.5, 8.8, 10.1};
    private static final double[] MIX_VOLT  = {13.5, 15.0, 15.5, 16.0, 17.0, 18.5, 20.5, 21.5};

    // Чистый CO2
    private static final double[] CO2_SPEED = {2.0, 2.5, 2.6, 3.4, 4.6, 6.5, 7.7, 8.7};
    private static final double[] CO2_VOLT  = {13.0, 13.5, 16.5, 17.5, 18.5, 20.0, 22.0, 22.5};

    // Сила тока, А. В заводской таблице аппарата её нет — значения ориентировочные,
    // типовые для проволоки 0.8 мм (зависят от толщины, не от газа).
    private static final double[] CURRENT = {40, 45, 55, 65, 90, 120, 140, 160};

    private SeekBar seekThickness;
    private Switch switchGas;
    private TextView textThickness, textVoltage, textSpeed, textDisplay, textCurrent, textGasFlow, textHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        seekThickness = findViewById(R.id.seekThickness);
        switchGas = findViewById(R.id.switchGas);
        textThickness = findViewById(R.id.textThickness);
        textVoltage = findViewById(R.id.textVoltage);
        textSpeed = findViewById(R.id.textSpeed);
        textDisplay = findViewById(R.id.textDisplay);
        textCurrent = findViewById(R.id.textCurrent);
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
        // SeekBar: 0..42 -> 0.8..5.0 мм с шагом 0.1
        double thickness = 0.8 + seekThickness.getProgress() / 10.0;
        boolean co2 = switchGas.isChecked();

        double speed = interpolate(THICKNESS, co2 ? CO2_SPEED : MIX_SPEED, thickness);
        double voltage = interpolate(THICKNESS, co2 ? CO2_VOLT : MIX_VOLT, thickness);
        double gasFlow = 8.0 + Math.max(0, thickness - 1.0);

        textThickness.setText(String.format(Locale.US, "%.1f мм", thickness));
        textSpeed.setText(String.format(Locale.US, "%.1f м/мин", speed));

        // На дисплее аппарата подача показана как четырёхзначное число:
        // 2.6 м/мин -> 0260, 5.1 -> 0510
        long display = Math.round(speed * 10) * 10L;
        textDisplay.setText(String.format(Locale.US, "%04d", display));

        textCurrent.setText(String.format(Locale.US, "%.0f А",
                interpolate(THICKNESS, CURRENT, thickness)));
        textVoltage.setText(String.format(Locale.US, "%.1f В", voltage));
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
