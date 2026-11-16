package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.control.*;
import me.jellysquid.mods.sodium.client.util.Dim2i;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public final class ExternalButtonOptionBuilder
        extends StatefulOptionBuilder<Boolean, ExternalButtonOptionBuilder> {
    private Consumer<Screen> screenConsumer;

    ExternalButtonOptionBuilder(ConfigBuilder b, ResourceLocation id) {
        super(b, id);
        getter = () -> false;
        setter = v -> {};
        defaultValue = false;
    }

    public ExternalButtonOptionBuilder setScreenConsumer(Consumer<Screen> v) {
        screenConsumer = v;
        return this;
    }

    public void openScreen(Screen previous) {
        screenConsumer.accept(previous);
    }

    public Class<Boolean> type() {
        return Boolean.class;
    }

    public Control<Boolean> control(ExtraOption<Boolean> option) {
        return new Control<>() {
            public ExtraOption<Boolean> getOption() {
                return option;
            }

            public int getMaxWidth() {
                return 20;
            }

            public ControlElement<Boolean> createElement(Dim2i dim) {
                return new ControlElement<>(option, dim) {
                    public void render(GuiGraphics gui, int x, int y, float delta) {
                        super.render(gui, x, y, delta);
                        drawString(
                                gui,
                                net.minecraft.network.chat.Component.literal(">"),
                                dim.getLimitX() - 12,
                                dim.getCenterY() - 4,
                                0xffffffff);
                    }

                    public boolean mouseClicked(double x, double y, int button) {
                        if (button == 0 && dim.containsCursor(x, y) && option.isAvailable()) {
                            open();
                            return true;
                        }
                        return false;
                    }

                    public boolean keyPressed(int key, int scan, int modifiers) {
                        if (isFocused()
                                && net.minecraft.client.gui.navigation.CommonInputs.selected(key)
                                && option.isAvailable()) {
                            open();
                            return true;
                        }
                        return false;
                    }

                    private void open() {
                        playClickSound();
                        screenConsumer.accept(Minecraft.getInstance().screen);
                    }
                };
            }
        };
    }
}
