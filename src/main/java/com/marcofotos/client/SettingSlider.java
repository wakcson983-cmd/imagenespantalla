package com.marcofotos.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class SettingSlider extends AbstractSliderButton {
    private final DoubleFunction<String> fmt;
    private final double min, max, step;
    private final DoubleConsumer onChange;
    private final Runnable onRelease;

    public SettingSlider(int x, int y, int w, int h, DoubleFunction<String> fmt, double min, double max, double step,
                         double cur, DoubleConsumer onChange, Runnable onRelease) {
        super(x, y, w, h, Component.empty(), (Mth.clamp(cur, min, max) - min) / (max - min));
        this.fmt = fmt;
        this.min = min;
        this.max = max;
        this.step = step;
        this.onChange = onChange;
        this.onRelease = onRelease;
        updateMessage();
    }

    private double real() {
        double v = min + this.value * (max - min);
        v = Math.round((v - min) / step) * step + min;
        return Mth.clamp(v, min, max);
    }

    @Override
    protected void updateMessage() {
        if (fmt == null) return;
        setMessage(Component.literal(fmt.apply(real())));
    }

    @Override
    protected void applyValue() {
        if (onChange != null) onChange.accept(real());
    }

    @Override
    public void onRelease(double mx, double my) {
        super.onRelease(mx, my);
        if (onRelease != null) onRelease.run();
    }
}
