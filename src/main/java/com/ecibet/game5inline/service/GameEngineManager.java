package com.ecibet.game5inline.service;

import com.ecibet.game5inline.model.GameConfig;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.messaging.GameEventPublisher;
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

    private final Map<String, GameEngine> engines = new ConcurrentHashMap<>();
    private final Map<String, Thread> engineThreads = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final GameEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void startEngine(Lobby lobby, GameConfig config) {
        String lobbyId = lobby.getId();

        if (engines.containsKey(lobbyId)) {
            log.warn("Engine already running for lobby {}", lobby.getCode());
            return;
        }

        GameEngine engine = new GameEngine(lobby, config, eventPublisher, applicationEventPublisher);
        engines.put(lobbyId, engine);

        Thread thread = new Thread(engine, "game-engine-" + lobby.getCode());
        thread.start();
        engineThreads.put(lobbyId, thread);

        log.info("Game engine started for lobby {}", lobby.getCode());
    }

    public GameEngine getEngine(String lobbyId) {
        return engines.get(lobbyId);
    }

    public void stopEngine(String lobbyId) {
        GameEngine engine = engines.remove(lobbyId);
        if (engine != null) {
            engine.stop();
            Thread thread = engineThreads.remove(lobbyId);
            if (thread != null) {
                thread.interrupt();
            }
            log.info("Game engine stopped for lobby {}", lobbyId);
        }
    }
}