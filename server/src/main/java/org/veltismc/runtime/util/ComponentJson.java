package org.veltismc.runtime.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;

public final class ComponentJson {
    private ComponentJson() {
    }

    public static Component parse(MinecraftServer server, String json) {
        try {
            JsonElement tree = JsonParser.parseString(json);
            return ComponentSerialization.CODEC
                    .parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), tree)
                    .getOrThrow(JsonParseException::new);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid component JSON: " + e.getMessage(), e);
        }
    }
}