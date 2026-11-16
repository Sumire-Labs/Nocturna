// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package org.embeddedt.embeddium.api.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public interface WeatherColumnProvider {

    // False suppresses vanilla rain and snow in this dimension.
    boolean rendersVanillaWeather();

    // Called each instanced weather frame before columnRevision and collectColumns; returns the tick counter that drives scroll and drift.
    int beginFrame(ClientLevel world, Entity view, float partialTicks);

    // Must change whenever any collected column changes; columns are re-collected only then or when the dimension or the player's block X/Z changes.
    int columnRevision();

    // Finite positive block distance d0 in alpha = ((1 - (d / d0)^2) * fadeScale + 0.5) * a.
    float fadeRadius();

    void collectColumns(WeatherColumnSink sink);
}
