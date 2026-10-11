package org.veltismc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.veltismc.api.Server;
import org.veltismc.api.entity.Player;
import org.veltismc.runtime.VeltisBootstrap;
import org.veltismc.runtime.player.VeltisPlayer;
import org.veltismc.runtime.util.ComponentJson;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class VeltisServer implements Server {
    private final Map<UUID, VeltisPlayer> PLAYERS = new ConcurrentHashMap<>();
    private final MinecraftServer server =
            VeltisBootstrap.MINECRAFT_SERVER instanceof MinecraftServer s ? s : null;

    /**
     * @return The server version. Null if invalid, its unlikely to happen
     */
    @Override
    public @Nullable String getVersion() {
        return server.getServerModName();
    }

    /**
     * Converts {@link ServerPlayer} to {@link VeltisPlayer}
     * 
     * @return {@link VeltisPlayer} or null, if {@link ServerPlayer} is null
     */
    public VeltisPlayer toVeltisPlayer(ServerPlayer serverPlayer) {
        return PLAYERS.computeIfAbsent(serverPlayer.getUUID(), id -> new VeltisPlayer(serverPlayer));
    }

    /**
     * Broadcasts a message in the whole server
     * @param message The message
     */
    @Override
    public void broadcast(String message) {
        if (message == null)
            return;
        broadcast(Component.literal(message));
    }

    /**
     * Broadcasts a custom minecraft JSON message in the whole server
     * @param json The JSON message
     */
    @Override
    public void broadcastJson(String json) {
        if (server == null)
            return;

        broadcast(ComponentJson.parse(server, json));
    }

    // internal
    private void broadcast(Component component) {
        if (server == null)
            return;
        if (component == null)
            return;

        server.getPlayerList().broadcastSystemMessage(component, false); // false = chat, true = action bar
    }

    /**
     * @return Returns null if the player cannot be found
     */
    @Override
    public @Nullable Player getPlayer(String username) {
        if (server == null)
            return null;
        if (username == null)
            return null;

        ServerPlayer p = server.getPlayerList().getPlayerByName(username);
        return p == null ? null : toVeltisPlayer(p);
    }

    /**
     * @return Returns null if the player cannot be found
     */
    @Override
    public @Nullable Player getPlayer(UUID uuid) {
        if (server == null)
            return null;
        if (uuid == null)
            return null;

        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        return p == null ? null : toVeltisPlayer(p);
    }

    @Override
    public int getTotalPlayers() {
        return server == null ? 0 : server.getPlayerList().getPlayerCount();
    }
}
