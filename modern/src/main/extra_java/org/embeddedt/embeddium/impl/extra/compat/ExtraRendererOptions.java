package org.embeddedt.embeddium.impl.extra.compat;

import com.google.gson.*;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.client.config.ConfigFileIO;

import java.nio.file.*;

/** Host prerequisites added independently of Extra's unchanged serialized configuration. */
public final class ExtraRendererOptions {
    private static Path path() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR
                .get()
                .resolve("nocturna-renderer.json");
    }

    private static Integer cloudRange;

    public static int cloudRange() {
        if (cloudRange == null) {
            cloudRange = 128;
            Path path = path();
            if (Files.exists(path))
                try (var reader = Files.newBufferedReader(path)) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("cloud_range"))
                        cloudRange = Math.max(2, Math.min(128, json.get("cloud_range").getAsInt()));
                } catch (Exception failure) {
                    SodiumExtraClientMod.logger()
                            .warn("Could not load native renderer options", failure);
                }
        }
        return cloudRange;
    }

    public static void setCloudRange(int value) {
        cloudRange = Math.max(2, Math.min(128, value));
    }

    public static void save() {
        try {
            var json = new JsonObject();
            json.addProperty("cloud_range", cloudRange());
            ConfigFileIO.writeStringAtomically(
                    path(), new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (Exception failure) {
            SodiumExtraClientMod.logger().warn("Could not save native renderer options", failure);
        }
    }
}
