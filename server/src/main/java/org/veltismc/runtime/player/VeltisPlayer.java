package org.veltismc.runtime.player;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;

import org.veltismc.api.entity.Player;
import org.veltismc.runtime.util.ComponentJson;

import io.netty.channel.Channel;

public final class VeltisPlayer implements Player {
    private final MinecraftServer server;
    private final NameAndId nameAndId;

    public VeltisPlayer(ServerPlayer handle) {
        this.server = handle.level().getServer();
        this.nameAndId = handle.nameAndId();
    }

    /**
     * Converts {@link ServerPlayer} to {@link VeltisPlayer}
     * 
     * @return {@link VeltisPlayer} or null, if {@link ServerPlayer} is null
     */
    public static VeltisPlayer toVeltisPlayer(ServerPlayer serverPlayer) {
        if (serverPlayer == null)
            return null;
        return new VeltisPlayer(serverPlayer);
    }

    /**
     * @return The ServerPlayer of the player. Null if the player is offline
     */
    private ServerPlayer handle() {
        return server.getPlayerList().getPlayer(nameAndId.id());
    }

    @Override
    public String getUsername() {
        return nameAndId.name();
    }

    @Override
    public UUID getUUID() {
        return nameAndId.id();
    }

    @Override
    public boolean isOnline() {
        return handle() != null;
    }

    @Override
    public void sendMessage(String message) {
        if (message == null)
            return;
        var p = handle();
        if (p != null)
            p.sendSystemMessage(Component.literal(message));
    }

    public void sendMessage(Component component) {
        if (component == null)
            return;
        var p = handle();
        if (p != null)
            p.sendSystemMessage(component);
    }

    @Override
    public void sendMessageJson(String json) {
        if (json == null)
            return;
        Component component = ComponentJson.parse(server, json);
        if (component == null)
            return;

        var p = handle();
        if (p != null)
            p.sendSystemMessage(component);
    }

    @Override
    public void kick() {
        var p = handle();
        if (p == null)
            return; // already offline

        try {
            Field connectionField = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            Connection mcConnection = (Connection) connectionField.get(p.connection);

            Field channelField = Connection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            Channel nettyChannel = (Channel) channelField.get(mcConnection);

            nettyChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
            kick("");
        }
    }

    @Override
    public void kick(String reason) {
        if (reason == null)
            return;
        var p = handle();
        if (p != null)
            p.connection.disconnect(Component.literal(reason));
    }

    public void kick(Component component) {
        if (component == null)
            return;
        var p = handle();
        if (p != null)
            p.connection.disconnect(component);
    }

    @Override
    public int getOperatorLevel() {
        ServerOpListEntry entry = server.getPlayerList().getOps().get(nameAndId);
        return entry == null ? 0 : entry.permissions().level().id();
    }

    @Override
    public void setOperatorLevel(int level) {
        if (level < 0 || level > 4)
            throw new IllegalArgumentException("Op level must be between 0 and 4, got: " + level);

        PlayerList list = server.getPlayerList();

        if (nameAndId == null)
            return;

        if (level == 0) {
            list.deop(nameAndId);
            return;
        }

        list.op(nameAndId,
                Optional.<LevelBasedPermissionSet>of(
                        LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level))),
                Optional.empty());
    }
}