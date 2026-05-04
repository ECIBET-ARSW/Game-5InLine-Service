package com.ecibet.game5inline.scheduler;

import com.ecibet.game5inline.cache.ActiveLobbiesCache;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.enums.LobbyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LobbyCleanupScheduler {

    private final ActiveLobbiesCache lobbyCache;

    @Value("${game.lobby.max-waiting-seconds:3600}")
    private int maxWaitingSeconds;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanupExpiredLobbies() {
        List<Lobby> lobbies = lobbyCache.getAll().stream().toList();
        LocalDateTime now = LocalDateTime.now();

        for (Lobby lobby : lobbies) {
            if (lobby.getStatus() == LobbyStatus.WAITING) {
                long waitingSeconds = ChronoUnit.SECONDS.between(lobby.getLastActivityAt(), now);

                if (waitingSeconds >= maxWaitingSeconds) {
                    log.info("Cancelling expired lobby: {} (waiting: {} seconds)", lobby.getCode(), waitingSeconds);
                    lobby.setStatus(LobbyStatus.CANCELLED);
                    lobbyCache.remove(lobby.getId());
                }
            }

            if (lobby.getStatus() == LobbyStatus.FINISHED || lobby.getStatus() == LobbyStatus.CANCELLED) {
                if (ChronoUnit.MINUTES.between(lobby.getLastActivityAt(), now) >= 5) {
                    log.info("Removing finished lobby: {}", lobby.getCode());
                    lobbyCache.remove(lobby.getId());
                }
            }
        }
    }
}