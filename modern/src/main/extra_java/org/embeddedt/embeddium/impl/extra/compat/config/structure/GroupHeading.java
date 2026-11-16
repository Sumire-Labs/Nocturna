package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.control.*;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.util.Dim2i;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;

import java.util.Collection;
import java.util.List;

/** A noninteractive group title in the legacy layout; it has no binding or persisted setting. */
final class GroupHeading implements Option<Boolean> {
    private final Component name;
    private final OptionIdentifier<Boolean> id;
    private final OptionStorage<?> storage;

    GroupHeading(ResourceLocation id, Component name, OptionStorage<?> storage) {
        this.name = name;
        this.id = OptionIdentifier.create(id, Boolean.class);
        this.storage = storage;
    }

    public OptionIdentifier<Boolean> getId() {
        return id;
    }

    public Component getName() {
        return name;
    }

    public Component getTooltip() {
        return Component.empty();
    }

    public OptionImpact getImpact() {
        return null;
    }

    public Boolean getValue() {
        return false;
    }

    public void setValue(Boolean value) {}

    public void reset() {}

    public OptionStorage<?> getStorage() {
        return storage;
    }

    public boolean isAvailable() {
        return false;
    }

    public boolean hasChanged() {
        return false;
    }

    public void applyChanges() {}

    public Collection<OptionFlag> getFlags() {
        return List.of();
    }

    public Control<Boolean> getControl() {
        return new Control<>() {
            public Option<Boolean> getOption() {
                return GroupHeading.this;
            }

            public int getMaxWidth() {
                return 0;
            }

            public ControlElement<Boolean> createElement(Dim2i dim) {
                return new ControlElement<>(GroupHeading.this, dim) {
                    public void render(GuiGraphics gui, int x, int y, float delta) {
                        gui.drawString(
                                net.minecraft.client.Minecraft.getInstance().font,
                                name,
                                dim.x() + 6,
                                dim.getCenterY() - 4,
                                0xffaaaaaa,
                                false);
                    }
                };
            }
        };
    }
}
