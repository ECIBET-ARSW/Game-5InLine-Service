package com.ecibet.game5inline.service;

import com.ecibet.game5inline.messaging.GameEventPublisher;
import com.ecibet.game5inline.model.GameConfig;
import com.ecibet.game5inline.model.Lobby;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameEngineManager {

    private final GameEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Map<String, GameEngine> activeEngines = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public void startEngine(Lobby lobby, GameConfig config) {
        if (activeEngines.containsKey(lobby.getId())) {
            log.warn("Engine already running for lobby {}", lobby.getCode());
            return;
        }

        GameEngine engine = new GameEngine(lobby, config, eventPublisher, applicationEventPublisher);
        activeEngines.put(lobby.getId(), engine);

        executorService.submit(engine);

        log.info("Game engine started for lobby {}", lobby.getCode());
    }

    public void stopEngine(String lobbyId) {
        GameEngine engine = activeEngines.remove(lobbyId);
        if (engine != null) {
            engine.stop();
            log.info("Game engine stopped for lobby {}", lobbyId);
        }
    }

    public GameEngine getEngine(String lobbyId) {
        return activeEngines.get(lobbyId);
    }

    public boolean isRunning(String lobbyId) {
        return activeEngines.containsKey(lobbyId);
    }
}