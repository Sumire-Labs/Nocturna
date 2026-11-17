package org.embeddedt.embeddium.impl.extra.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Searchable full catalog, exclusive visibility controls, and the two upstream profiles. */
public final class ExtraDebugOptionsScreen extends Screen {
    private final Screen parent;
    private EditBox search;
    private final List<Button> rows = new ArrayList<>();
    private List<DebugOverlayManager.Entry> filtered = List.of();
    private String query = "";
    private int scroll;
    private double scrollRemainder;
    private long revision = -1;
    private Button defaultProfile, performanceProfile;

    public ExtraDebugOptionsScreen() {
        super(Component.translatable("sodium-extra.option.more_hud_overlays"));
        parent = Minecraft.getInstance().screen;
    }

    @Override
    protected void init() {
        defaultProfile =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable("nocturna.debug.profile.default"),
                                        b -> profile(DebugOverlayState.Profile.DEFAULT))
                                .bounds(width / 2 - 155, 32, 150, 20)
                                .build());
        performanceProfile =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable(
                                                "nocturna.debug.profile.performance"),
                                        b -> profile(DebugOverlayState.Profile.PERFORMANCE))
                                .bounds(width / 2 + 5, 32, 150, 20)
                                .build());
        search =
                new EditBox(
                        font, width / 2 - 155, 58, 310, 20, Component.translatable("gui.search"));
        search.setHint(Component.translatable("gui.search"));
        search.setValue(query);
        search.setResponder(
                value -> {
                    query = value;
                    scroll = 0;
                    scrollRemainder = 0;
                    rebuild();
                });
        addRenderableWidget(search);
        addRenderableWidget(
                Button.builder(Component.translatable("gui.done"), b -> onClose())
                        .bounds(width / 2 - 100, height - 28, 200, 20)
                        .build());
        rebuild();
        setInitialFocus(search);
    }

    private void profile(DebugOverlayState.Profile profile) {
        DebugOverlayManager.state().loadProfile(profile);
        DebugOverlayManager.invalidate();
        refreshSelections();
    }

    private void rebuild() {
        for (var button : rows) removeWidget(button);
        rows.clear();
        String text = query.toLowerCase(Locale.ROOT);
        filtered =
                DebugOverlayManager.entries().stream()
                        .filter(
                                e ->
                                        (e.name().getString()
                                                        + " "
                                                        + e.id()
                                                        + " "
                                                        + e.category()
                                                        + " "
                                                        + e.tooltip().getString())
                                                .toLowerCase(Locale.ROOT)
                                                .contains(text))
                        .sorted(
                                Comparator.comparing(DebugOverlayManager.Entry::category)
                                        .thenComparing(
                                                e -> e.name().getString(),
                                                String.CASE_INSENSITIVE_ORDER))
                        .toList();
        int visible = Math.max(1, (height - 120) / 26);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - visible)));
        int right = width - 18, buttonWidth = Math.min(82, Math.max(46, (width - 250) / 3));
        for (int row = 0; row < visible && row + scroll < filtered.size(); row++) {
            var entry = filtered.get(row + scroll);
            int y = 88 + row * 26;
            for (var status : DebugOverlayState.Visibility.values()) {
                var button =
                        Button.builder(
                                        Component.translatable(
                                                "sodium-extra.debug.visibility."
                                                        + status.name().toLowerCase(Locale.ROOT)),
                                        b -> {
                                            DebugOverlayManager.state()
                                                    .set(entry.id().toString(), status);
                                            DebugOverlayManager.invalidate();
                                            refreshSelections();
                                        })
                                .bounds(
                                        right - (3 - status.ordinal()) * buttonWidth,
                                        y,
                                        buttonWidth,
                                        20)
                                .build();
                boolean allowed = entry.reducedAllowed() || !minecraft.showOnlyReducedInfo();
                button.active = allowed;
                if (DebugOverlayManager.state().get(entry.id().toString()) == status)
                    button.setMessage(
                            Component.literal("[ ").append(button.getMessage()).append(" ]"));
                button.setTooltip(
                        Tooltip.create(
                                allowed
                                        ? entry.tooltip()
                                        : Component.translatable("nocturna.debug.not_allowed")));
                rows.add(addRenderableWidget(button));
            }
        }
        refreshSelections();
    }

    private void refreshSelections() {
        for (int i = 0; i < rows.size(); i++) {
            var entry = filtered.get(scroll + i / 3);
            var status = DebugOverlayState.Visibility.values()[i % 3];
            var button = rows.get(i);
            boolean allowed = entry.reducedAllowed() || !minecraft.showOnlyReducedInfo();
            if (button.active != allowed)
                button.setTooltip(
                        Tooltip.create(
                                allowed
                                        ? entry.tooltip()
                                        : Component.translatable("nocturna.debug.not_allowed")));
            button.active = allowed;
            Component message =
                    Component.translatable(
                            "sodium-extra.debug.visibility."
                                    + status.name().toLowerCase(Locale.ROOT));
            button.setMessage(
                    DebugOverlayManager.state().get(entry.id().toString()) == status
                            ? Component.literal("[ ").append(message).append(" ]")
                            : message);
        }
        revision = DebugOverlayManager.cacheRevision();
        defaultProfile.active =
                !DebugOverlayManager.state()
                        .profile()
                        .filter(p -> p == DebugOverlayState.Profile.DEFAULT)
                        .isPresent();
        performanceProfile.active =
                !DebugOverlayManager.state()
                        .profile()
                        .filter(p -> p == DebugOverlayState.Profile.PERFORMANCE)
                        .isPresent();
    }

    @Override
    public void tick() {
        super.tick();
        if (revision != DebugOverlayManager.cacheRevision()) refreshSelections();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (y >= 84 && y < height - 32) {
            scrollRemainder -= amount;
            int steps = (int) scrollRemainder;
            scrollRemainder -= steps;
            if (steps != 0) {
                scroll += steps;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override
    public void render(GuiGraphics gui, int x, int y, float delta) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 12, 0xffffffff);
        super.render(gui, x, y, delta);
        int visible = Math.max(1, (height - 120) / 26);
        int labelWidth = Math.max(60, width - 290);
        for (int row = 0; row < visible && row + scroll < filtered.size(); row++) {
            var entry = filtered.get(row + scroll);
            int top = 88 + row * 26;
            boolean allowed = entry.reducedAllowed() || !minecraft.showOnlyReducedInfo();
            gui.drawString(
                    font,
                    font.plainSubstrByWidth(entry.name().getString(), labelWidth),
                    12,
                    top + 3,
                    allowed ? 0xffffffff : 0xff808080,
                    false);
            gui.drawString(font, entry.category(), 12, top + 13, 0xff909090, false);
            if (x >= 10 && x < 12 + labelWidth && y >= top && y < top + 23)
                gui.renderTooltip(
                        font,
                        font.split(
                                allowed
                                        ? entry.tooltip()
                                        : Component.translatable("nocturna.debug.not_allowed"),
                                Math.min(300, width - 30)),
                        x,
                        y);
        }
        gui.drawString(
                font,
                (scroll + 1) + " / " + filtered.size(),
                width - 70,
                height - 20,
                0xffaaaaaa,
                false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    public static void renderOverlays(GuiGraphics graphics) {
        DebugOverlayManager.renderAlways(graphics);
    }

    public static List<String> appendDebugLines(List<String> ignored) {
        return DebugOverlayManager.left();
    }
}
