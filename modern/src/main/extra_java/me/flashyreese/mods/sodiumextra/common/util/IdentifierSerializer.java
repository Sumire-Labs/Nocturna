// Sodium Extra 0.9.6-beta.4, LGPL-3.0, upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.common.util;

import com.google.gson.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.lang.reflect.Type;

public class IdentifierSerializer
        implements JsonDeserializer<ResourceLocation>, JsonSerializer<ResourceLocation> {
    public IdentifierSerializer() {}

    public ResourceLocation deserialize(
            JsonElement jsonElement,
            Type type,
            JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        return new ResourceLocation(GsonHelper.convertToString(jsonElement, "location"));
    }

    public JsonElement serialize(
            ResourceLocation resourceLocation,
            Type type,
            JsonSerializationContext jsonSerializationContext) {
        return new JsonPrimitive(resourceLocation.toString());
    }
}
