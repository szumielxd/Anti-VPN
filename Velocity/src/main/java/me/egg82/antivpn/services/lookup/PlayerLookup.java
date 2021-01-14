package me.egg82.antivpn.services.lookup;

import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.NonNull;

public class PlayerLookup {
    private PlayerLookup() { }

    public static @NonNull PlayerInfo get(@NonNull UUID uuid, @NonNull ProxyServer proxy) throws IOException { return new VelocityPlayerInfo(uuid, proxy); }

    public static @NonNull PlayerInfo get(@NonNull String name, @NonNull ProxyServer proxy) throws IOException { return new VelocityPlayerInfo(name, proxy); }
}
