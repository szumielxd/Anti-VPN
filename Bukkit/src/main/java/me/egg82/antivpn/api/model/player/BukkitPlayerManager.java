package me.egg82.antivpn.api.model.player;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import me.egg82.antivpn.api.APIException;
import me.egg82.antivpn.config.CachedConfig;
import me.egg82.antivpn.config.ConfigUtil;
import me.egg82.antivpn.messaging.packets.PlayerPacket;
import me.egg82.antivpn.services.lookup.PlayerLookup;
import me.egg82.antivpn.storage.StorageService;
import me.egg82.antivpn.storage.models.PlayerModel;
import me.egg82.antivpn.utils.PacketUtil;
import me.gong.mcleaks.MCLeaksAPI;
import org.checkerframework.checker.nullness.qual.NonNull;

public class BukkitPlayerManager extends AbstractPlayerManager {
    private final MCLeaksAPI api;

    public BukkitPlayerManager(int webThreads, String mcleaksKey, long cacheTime, TimeUnit cacheTimeUnit) {
        super(cacheTime, cacheTimeUnit);

        api = MCLeaksAPI.builder()
                .nocache()
                .threadCount(webThreads)
                .userAgent("egg82/AntiVPN")
                .apiKey(mcleaksKey)
                .build();
    }

    public void cancel() {
        api.shutdown();
    }

    public @NonNull CompletableFuture<Player> getPlayer(@NonNull UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            CachedConfig cachedConfig = ConfigUtil.getCachedConfig();
            if (cachedConfig == null) {
                throw new APIException(false, "Cached config could not be fetched.");
            }

            for (StorageService service : cachedConfig.getStorage()) {
                PlayerModel model = service.getPlayerModel(uniqueId, cachedConfig.getSourceCacheTime());
                if (model != null) {
                    return new BukkitPlayer(uniqueId, model.isMcleaks());
                }
            }
            return null;
        });
    }

    public @NonNull CompletableFuture<Player> getPlayer(@NonNull String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return PlayerLookup.get(username).getUUID();
            } catch (IOException ex) {
                throw new APIException(false, "Could not fetch player UUID. (rate-limited?)", ex);
            }
        }).thenApply(uniqueId -> {
            CachedConfig cachedConfig = ConfigUtil.getCachedConfig();
            if (cachedConfig == null) {
                throw new APIException(false, "Cached config could not be fetched.");
            }

            for (StorageService service : cachedConfig.getStorage()) {
                PlayerModel model = service.getPlayerModel(uniqueId, cachedConfig.getSourceCacheTime());
                if (model != null) {
                    return new BukkitPlayer(uniqueId, model.isMcleaks());
                }
            }
            return null;
        });
    }

    protected @NonNull PlayerModel calculatePlayerResult(@NonNull UUID uuid, boolean useCache) throws APIException {
        CachedConfig cachedConfig = ConfigUtil.getCachedConfig();
        if (cachedConfig == null) {
            throw new APIException(false, "Cached config could not be fetched.");
        }

        if (cachedConfig.getDebug()) {
            logger.info("Getting web result for player " + uuid + ".");
        }

        if (useCache) {
            for (StorageService service : cachedConfig.getStorage()) {
                PlayerModel model = service.getPlayerModel(uuid, cachedConfig.getSourceCacheTime());
                if (model != null) {
                    return model;
                }
            }
        }

        PlayerModel retVal = new PlayerModel();
        retVal.setUuid(uuid);

        MCLeaksAPI.Result result = api.checkAccount(uuid);
        if (result.hasError()) {
            throw new APIException(result.getError().getMessage().contains("key"), result.getError());
        }

        retVal.setMcleaks(result.isMCLeaks());

        if (useCache) {
            playerCache.put(uuid, retVal);
            storeResult(retVal, cachedConfig);
            sendResult(retVal, cachedConfig);
        }
        return retVal;
    }

    private void storeResult(@NonNull PlayerModel model, @NonNull CachedConfig cachedConfig) {
        for (StorageService service : cachedConfig.getStorage()) {
            PlayerModel m = new PlayerModel();
            m.setUuid(model.getUuid());
            m.setMcleaks(model.isMcleaks());
            service.storeModel(m);
        }

        if (cachedConfig.getDebug()) {
            logger.info("Stored data for " + model.getUuid() + " in storage.");
        }
    }

    private void sendResult(@NonNull PlayerModel model, @NonNull CachedConfig cachedConfig) {
        PlayerPacket packet = new PlayerPacket();
        packet.setUuid(model.getUuid());
        packet.setValue(model.isMcleaks());
        PacketUtil.queuePacket(packet);

        if (cachedConfig.getDebug()) {
            logger.info("Queued packet for " + model.getUuid() + " in messaging.");
        }
    }
}
