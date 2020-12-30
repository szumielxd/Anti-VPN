package me.egg82.antivpn.commands.internal;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChainFactory;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import me.egg82.antivpn.api.VPNAPIProvider;
import me.egg82.antivpn.api.model.ip.AlgorithmMethod;
import me.egg82.antivpn.api.model.ip.IPManager;
import me.egg82.antivpn.api.model.player.PlayerManager;
import me.egg82.antivpn.lang.Message;
import me.egg82.antivpn.utils.ValidationUtil;

public class CheckCommand extends AbstractCommand {
    private final String type;

    public CheckCommand(CommandIssuer issuer, TaskChainFactory taskFactory, String type) {
        super(issuer, taskFactory);
        this.type = type;
    }

    public void run() {
        issuer.sendInfo(Message.CHECK__BEGIN, "{type}", type);

        if (ValidationUtil.isValidIp(type)) {
            checkIp(type);
        } else {
            checkPlayer(type);
        }
    }

    private void checkIp(String ip) {
        IPManager ipManager = VPNAPIProvider.getInstance().getIpManager();

        taskFactory.<Void>newChain()
                .<Boolean>asyncCallback((v, r) -> {
                    if (ipManager.getCurrentAlgorithmMethod() == AlgorithmMethod.CONSESNSUS) {
                        try {
                            r.accept(ipManager.consensus(ip, true)
                                    .exceptionally(this::handleException)
                                    .join() >= ipManager.getMinConsensusValue());
                            return;
                        } catch (CompletionException ignored) { }
                    } else {
                        try {
                            r.accept(ipManager.cascade(ip, true)
                                    .exceptionally(this::handleException)
                                    .join());
                            return;
                        } catch (CompletionException ignored) { }
                    }

                    r.accept(null);
                })
                .abortIfNull(this.handleAbort)
                .syncLast(v -> issuer.sendInfo(Boolean.TRUE.equals(v) ? Message.CHECK__VPN_DETECTED : Message.CHECK__NO_VPN_DETECTED))
                .execute();
    }

    private void checkPlayer(String playerName) {
        PlayerManager playerManager = VPNAPIProvider.getInstance().getPlayerManager();

        taskFactory.<Void>newChain()
                .<UUID>asyncCallback((v, r) -> r.accept(fetchUuid(playerName)))
                .abortIfNull(this.handleAbort)
                .<Boolean>asyncCallback((v, r) -> {
                    try {
                        r.accept(playerManager.checkMcLeaks(v, true)
                                .exceptionally(this::handleException)
                                .join());
                        return;
                    } catch (CompletionException ignored) { }

                    r.accept(null);
                })
                .abortIfNull(this.handleAbort)
                .syncLast(v -> issuer.sendInfo(Boolean.TRUE.equals(v) ? Message.CHECK__MCLEAKS_DETECTED : Message.CHECK__NO_MCLEAKS_DETECTED))
                .execute();
    }
}
