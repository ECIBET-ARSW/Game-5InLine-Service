package com.ecibet.game5inline.service;

import com.ecibet.game5inline.messaging.GameEventPublisher;
import com.ecibet.game5inline.model.*;
import com.ecibet.game5inline.model.enums.AnimationType;
import com.ecibet.game5inline.model.enums.EffectType;
import com.ecibet.game5inline.model.enums.GroundType;
import com.ecibet.game5inline.model.enums.LobbyStatus;
import com.ecibet.game5inline.model.enums.ObstacleType;
import com.ecibet.game5inline.websocket.GameEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class GameEngine implements Runnable {

    private final Lobby lobby;
    private final GameConfig config;
    private final Map<String, Queue<PlayerInput>> inputQueues;
    private final List<Obstacle> obstacles;
    private final List<Effect> effects;
    private final GameEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Set<String> readyClients;
    private final Map<String, Boolean> playerRunning;
    private final Map<String, Long> lastCollisionTime;
    private boolean running;
    private long lastTickTime;
    private long gameStartTime;
    private Double worldOffset;
    private Double currentSpeed;
    private long lastSpeedIncrease;
    private GroundType currentGroundType;
    private boolean allClientsReady;
    private long countdownStartTime;
    private boolean gameStarted;
    private long countdownTickLast;
    private Random random;

    public GameEngine(Lobby lobby, GameConfig config, GameEventPublisher eventPublisher, ApplicationEventPublisher applicationEventPublisher) {
        this.lobby = lobby;
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.inputQueues = new HashMap<>();
        this.obstacles = new ArrayList<>();
        this.effects = new ArrayList<>();
        this.readyClients = ConcurrentHashMap.newKeySet();
        this.playerRunning = new HashMap<>();
        this.lastCollisionTime = new HashMap<>();
        this.running = true;
        this.worldOffset = 0.0;
        this.currentSpeed = 0.0;
        this.currentGroundType = GroundType.NORMAL;
        this.allClientsReady = false;
        this.gameStarted = false;
        this.countdownTickLast = 0;
        this.random = new Random();

        double startX = 400.0;
        double startY = 448.0;

        for (Player player : lobby.getPlayers().values()) {
            inputQueues.put(player.getUserId(), new ConcurrentLinkedQueue<>());
            player.setX(startX);
            player.setY(startY);
            player.setIsAlive(true);
            player.setLives(config.getInitialLives());
            player.setDistance(0.0);
            player.setAnimation(AnimationType.IDLE.name());
            player.setFrameIndex(0);
            player.setIsOnGround(true);
            playerRunning.put(player.getUserId(), false);
            lastCollisionTime.put(player.getUserId(), 0L);
            startX += 50;
        }
    }

    public void addInput(String userId, String action, Long timestamp) {
        if (lobby.getStatus() != LobbyStatus.IN_PROGRESS) {
            return;
        }
        Queue<PlayerInput> queue = inputQueues.get(userId);
        if (queue != null) {
            queue.offer(new PlayerInput(action, timestamp));
            log.info("Input added - User: {}, Action: {}, Queue size: {}", userId, action, queue.size());
        } else {
            log.warn("No input queue found for user: {}", userId);
        }
    }

    public void addReadyClient(String userId) {
        readyClients.add(userId);
        log.info("Cliente {} listo para lobby {} ({}/{})", userId, lobby.getCode(), readyClients.size(), lobby.getPlayers().size());
    }

    public void stop() {
        this.running = false;
    }

    private void broadcastEvent(String eventType, Map<String, Object> data) {
        applicationEventPublisher.publishEvent(new GameEvent(this, eventType, lobby.getCode(), data));
        log.debug("Evento publicado: {} para lobby {}", eventType, lobby.getCode());
    }

    @Override
    public void run() {
        lobby.setStatus(LobbyStatus.IN_PROGRESS);
        gameStartTime = System.currentTimeMillis();
        lastTickTime = gameStartTime;
        lastSpeedIncrease = gameStartTime;

        log.info("Game engine started for lobby {} - waiting max 2 seconds for clients", lobby.getCode());

        while (running && lobby.getStatus() == LobbyStatus.IN_PROGRESS) {
            long now = System.currentTimeMillis();

            if (!allClientsReady) {
                long elapsedSinceStart = now - gameStartTime;
                if (elapsedSinceStart > 2000) {
                    allClientsReady = true;
                    countdownStartTime = now;
                    countdownTickLast = 0;
                    log.info("Starting countdown for lobby {} (ready: {}/{})", lobby.getCode(), readyClients.size(), lobby.getPlayers().size());

                    Map<String, Object> countdownMsg = new HashMap<>();
                    countdownMsg.put("count", 3);
                    broadcastEvent("COUNTDOWN_START", countdownMsg);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            long elapsedSinceReady = now - countdownStartTime;

            if (!gameStarted && elapsedSinceReady < 4000) {
                int currentCount = 3 - (int)(elapsedSinceReady / 1000);
                if (currentCount > 0 && currentCount != countdownTickLast) {
                    countdownTickLast = currentCount;
                    log.info("Countdown tick: {}", currentCount);
                    Map<String, Object> tickMsg = new HashMap<>();
                    tickMsg.put("count", currentCount);
                    broadcastEvent("COUNTDOWN_TICK", tickMsg);
                }

                if (elapsedSinceReady >= 3500 && !gameStarted) {
                    gameStarted = true;
                    currentSpeed = config.getWorldSpeed();
                    log.info("Game started for lobby {} - initial scroll speed: {}", lobby.getCode(), currentSpeed);
                    broadcastEvent("COUNTDOWN_GO", null);
                }

                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            if (!gameStarted) {
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            long deltaTime = now - lastTickTime;

            if (deltaTime >= 33) {
                tick(now);
                lastTickTime = now;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        finishGame();
    }

    private void tick(long now) {
        if (!gameStarted || lobby.getStatus() != LobbyStatus.IN_PROGRESS) {
            return;
        }

        processInputs();
        updatePhysics();
        updateAnimations(now);
        updateWorldScroll();
        checkCollisions();
        generateObstacles();
        updateEffects(now);
        increaseSpeedIfNeeded(now);
        broadcastGameState();
        checkGameOver();
    }

    private void processInputs() {
        if (lobby.getStatus() != LobbyStatus.IN_PROGRESS) {
            return;
        }

        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            Queue<PlayerInput> queue = inputQueues.get(player.getUserId());
            if (queue == null) continue;

            List<PlayerInput> inputs = new ArrayList<>();
            PlayerInput input;
            while ((input = queue.poll()) != null) {
                inputs.add(input);
            }

            for (PlayerInput pi : inputs) {
                log.info("Processing input - User: {}, Action: {}", player.getUsername(), pi.action);

                if ("jump".equals(pi.action) && Boolean.TRUE.equals(player.getIsOnGround())) {
                    player.setIsOnGround(false);
                    player.setLastJumpTime(System.currentTimeMillis());
                    player.setY(player.getY() - 28.0);
                    log.info("JUMP - User: {}, New Y: {}", player.getUsername(), player.getY());
                    Effect jumpEffect = new Effect(EffectType.JUMP, player.getX(), player.getY() + 20);
                    effects.add(jumpEffect);
                }

                if ("run".equals(pi.action)) {
                    playerRunning.put(player.getUserId(), true);
                    log.info("RUN START - User: {}", player.getUsername());
                }

                if ("stop".equals(pi.action)) {
                    playerRunning.put(player.getUserId(), false);
                    log.info("RUN STOP - User: {}", player.getUsername());
                }

                if ("slide".equals(pi.action) && Boolean.TRUE.equals(player.getIsOnGround())) {
                    player.setIsSliding(true);
                    player.setSlideEndTime(System.currentTimeMillis() + (long) config.getSlideDurationMs());
                    log.info("SLIDE - User: {}", player.getUsername());
                }
            }
        }
    }

    private void updatePhysics() {
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            boolean isRunning = playerRunning.getOrDefault(player.getUserId(), false);

            if (isRunning && Boolean.TRUE.equals(player.getIsOnGround())) {
                player.setX(player.getX() + currentSpeed * 1.2);
                player.setDistance(player.getDistance() + (currentSpeed / 10.0) * 1.2);
                log.debug("MOVING - User: {}, X: {}, Distance: {}", player.getUsername(), player.getX(), player.getDistance());
            }

            if (Boolean.FALSE.equals(player.getIsOnGround())) {
                player.setY(player.getY() + 0.9);

                if (player.getY() >= 448.0) {
                    player.setY(448.0);
                    player.setIsOnGround(true);
                    player.setIsSliding(false);

                    Effect dustEffect = new Effect(EffectType.DUST, player.getX(), player.getY() + 20);
                    effects.add(dustEffect);
                }
            }

            if (Boolean.TRUE.equals(player.getIsSliding())) {
                if (System.currentTimeMillis() > player.getSlideEndTime()) {
                    player.setIsSliding(false);
                }
            }
        }
    }

    private void updateAnimations(long now) {
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) {
                player.setAnimation(AnimationType.DYING.name());
                continue;
            }

            boolean isRunning = playerRunning.getOrDefault(player.getUserId(), false);

            if (Boolean.TRUE.equals(player.getIsSliding())) {
                player.setAnimation(AnimationType.SLIDING.name());
            } else if (Boolean.FALSE.equals(player.getIsOnGround())) {
                player.setAnimation(AnimationType.JUMPING.name());
            } else if (isRunning && Boolean.TRUE.equals(player.getIsOnGround())) {
                player.setAnimation(AnimationType.RUNNING.name());
            } else {
                player.setAnimation(AnimationType.IDLE.name());
            }

            int frameIndex = (int) ((now / 100) % getFrameCountForAnimation(player.getAnimation()));
            player.setFrameIndex(frameIndex);
        }
    }

    private int getFrameCountForAnimation(String animation) {
        switch (animation) {
            case "RUNNING": return 3;
            case "JUMPING": return 3;
            case "SLIDING": return 3;
            case "DYING": return 2;
            case "IDLE": return 4;
            default: return 3;
        }
    }

    private void updateWorldScroll() {
        worldOffset += currentSpeed;
    }

    private void checkCollisions() {
        long currentTime = System.currentTimeMillis();

        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            boolean collided = false;

            for (Obstacle obstacle : obstacles) {
                if (isCollision(player, obstacle)) {
                    Long lastCollision = lastCollisionTime.get(player.getUserId());
                    if (lastCollision == null || (currentTime - lastCollision) > 500) {
                        handleCollision(player, obstacle);
                        lastCollisionTime.put(player.getUserId(), currentTime);
                        collided = true;
                        break;
                    }
                }
            }

            if (player.getY() > 580) {
                if ((currentTime - lastCollisionTime.getOrDefault(player.getUserId(), 0L)) > 500) {
                    player.setLives(player.getLives() - 1);
                    lastCollisionTime.put(player.getUserId(), currentTime);

                    if (player.getLives() <= 0) {
                        player.setIsAlive(false);
                        player.setAnimation(AnimationType.DYING.name());
                        Effect explosion = new Effect(EffectType.EXPLOSION, player.getX(), player.getY());
                        effects.add(explosion);
                        log.info("Player {} fell to death", player.getUsername());
                    } else {
                        player.setY(448.0);
                        player.setIsOnGround(true);
                        Effect hurtEffect = new Effect(EffectType.EXPLOSION, player.getX(), player.getY());
                        effects.add(hurtEffect);
                        log.info("Player {} lost a life, remaining: {}", player.getUsername(), player.getLives());
                    }
                }
            }
        }

        obstacles.removeIf(o -> o.getX() + o.getWidth() < worldOffset - 500);
    }

    private boolean isCollision(Player player, Obstacle obstacle) {
        double playerLeft = player.getX();
        double playerRight = player.getX() + 32;
        double playerTop = player.getY();
        double playerBottom = player.getY() + 32;

        if (Boolean.TRUE.equals(player.getIsSliding())) {
            playerTop = player.getY() + 16;
        }

        double obstacleLeft = obstacle.getX() - worldOffset;
        double obstacleRight = obstacle.getX() - worldOffset + obstacle.getWidth();
        double obstacleTop = obstacle.getY();
        double obstacleBottom = obstacle.getY() + obstacle.getHeight();

        return playerRight > obstacleLeft && playerLeft < obstacleRight &&
                playerBottom > obstacleTop && playerTop < obstacleBottom;
    }

    private void handleCollision(Player player, Obstacle obstacle) {
        log.info("COLLISION - Player: {}, Obstacle: {}, Lives before: {}",
                player.getUsername(), obstacle.getType(), player.getLives());

        switch (obstacle.getType()) {
            case TRAMPOLINE:
                player.setIsOnGround(false);
                player.setY(player.getY() - 30);
                Effect jumpEffect = new Effect(EffectType.JUMP, player.getX(), player.getY());
                effects.add(jumpEffect);
                log.info("TRAMPOLINE - Player {} bounced", player.getUsername());
                break;
            case POWERUP_SPEED:
                currentSpeed = Math.min(currentSpeed + 1.5, config.getMaxSpeed());
                obstacles.remove(obstacle);
                Effect starEffect = new Effect(EffectType.STAR, obstacle.getX(), obstacle.getY());
                effects.add(starEffect);
                log.info("POWERUP - Player {} got speed boost", player.getUsername());
                break;
            case PLATFORM:
                if (player.getY() + 32 > obstacle.getY() && player.getY() < obstacle.getY() + obstacle.getHeight()) {
                    player.setY(obstacle.getY() - 32);
                    player.setIsOnGround(true);
                    log.info("PLATFORM - Player {} landed on platform", player.getUsername());
                }
                break;
            case ROCK:
            case SPIKE:
            case BLADE:
                player.setLives(player.getLives() - 1);
                Effect explosionHit = new Effect(EffectType.EXPLOSION, obstacle.getX(), obstacle.getY());
                effects.add(explosionHit);
                log.info("DAMAGE - Player {} hit {}, lives remaining: {}",
                        player.getUsername(), obstacle.getType(), player.getLives());
                if (player.getLives() <= 0) {
                    player.setIsAlive(false);
                    Effect deathExplosion = new Effect(EffectType.EXPLOSION, player.getX(), player.getY());
                    effects.add(deathExplosion);
                    log.info("DEATH - Player {} died", player.getUsername());
                }
                break;
            case LAVA:
            case PIT:
                player.setLives(0);
                player.setIsAlive(false);
                Effect explosionDeath = new Effect(EffectType.EXPLOSION, player.getX(), player.getY());
                effects.add(explosionDeath);
                log.info("DEATH - Player {} fell into {}", player.getUsername(), obstacle.getType());
                break;
            default:
                break;
        }

        if (obstacle.getType() != ObstacleType.PLATFORM && obstacle.getType() != ObstacleType.TRAMPOLINE) {
            obstacles.remove(obstacle);
        }
    }

    private void generateObstacles() {
        if (obstacles.size() < 6 && random.nextDouble() < 0.015) {
            ObstacleType[] types = {
                    ObstacleType.PLATFORM,
                    ObstacleType.TRAMPOLINE,
                    ObstacleType.POWERUP_SPEED,
                    ObstacleType.ROCK,
                    ObstacleType.BLADE,
                    ObstacleType.SPIKE,
                    ObstacleType.LAVA,
                    ObstacleType.PIT
            };

            double randomValue = random.nextDouble();
            ObstacleType type;

            if (randomValue < 0.25) {
                type = ObstacleType.PLATFORM;
            } else if (randomValue < 0.4) {
                type = ObstacleType.TRAMPOLINE;
            } else if (randomValue < 0.5) {
                type = ObstacleType.POWERUP_SPEED;
            } else if (randomValue < 0.6) {
                type = ObstacleType.ROCK;
            } else if (randomValue < 0.7) {
                type = ObstacleType.BLADE;
            } else if (randomValue < 0.8) {
                type = ObstacleType.SPIKE;
            } else if (randomValue < 0.9) {
                type = ObstacleType.LAVA;
            } else {
                type = ObstacleType.PIT;
            }

            double x = worldOffset + 900 + random.nextDouble() * 500;
            double y = 448;

            switch (type) {
                case PLATFORM:
                    y = 398;
                    break;
                case TRAMPOLINE:
                    y = 432;
                    break;
                case ROCK:
                    y = 416;
                    break;
                case BLADE:
                    y = 388;
                    break;
                case SPIKE:
                    y = 448;
                    break;
                case LAVA:
                    y = 448;
                    break;
                case PIT:
                    y = 448;
                    break;
                default:
                    y = 448;
                    break;
            }

            Obstacle obstacle = new Obstacle(type, x, y);
            obstacles.add(obstacle);
            log.debug("Obstacle generated: {} at position {}", type, x);
        }
    }

    private void updateEffects(long now) {
        effects.removeIf(Effect::isExpired);

        for (Effect effect : effects) {
            long elapsed = now - effect.getCreatedAt();
            int frameDuration = 60;
            int frameIndex = (int) (elapsed / frameDuration) % effect.getType().getFrameCount();
            effect.setFrameIndex(frameIndex);
        }
    }

    private void increaseSpeedIfNeeded(long now) {
        long elapsed = now - lastSpeedIncrease;
        if (elapsed >= config.getSpeedIncreaseInterval()) {
            double newSpeed = currentSpeed * config.getSpeedIncreaseFactor();
            currentSpeed = Math.min(newSpeed, config.getMaxSpeed());
            lastSpeedIncrease = now;
            log.debug("Scroll speed increased to {}", currentSpeed);
        }
    }

    private void broadcastGameState() {
        if (gameStarted && lobby.getStatus() == LobbyStatus.IN_PROGRESS) {
            List<Map<String, Object>> playersList = new ArrayList<>();
            for (Player player : lobby.getPlayers().values()) {
                Map<String, Object> playerMap = new HashMap<>();
                playerMap.put("id", player.getUserId());
                playerMap.put("name", player.getUsername());
                playerMap.put("color", player.getColor().name().toLowerCase());
                playerMap.put("animation", player.getAnimation().toLowerCase());
                playerMap.put("frameIndex", player.getFrameIndex());
                playerMap.put("x", player.getX());
                playerMap.put("y", player.getY());
                playerMap.put("isAlive", player.getIsAlive());
                playerMap.put("lives", player.getLives());
                playerMap.put("distance", player.getDistance());
                playerMap.put("isRunning", playerRunning.getOrDefault(player.getUserId(), false));
                playersList.add(playerMap);
            }

            List<Map<String, Object>> obstaclesList = new ArrayList<>();
            for (Obstacle obstacle : obstacles) {
                Map<String, Object> obstacleMap = new HashMap<>();
                obstacleMap.put("type", obstacle.getType().name().toLowerCase());
                obstacleMap.put("frameIndex", obstacle.getFrameIndex());
                obstacleMap.put("x", obstacle.getX());
                obstacleMap.put("y", obstacle.getY());
                obstaclesList.add(obstacleMap);
            }

            List<Map<String, Object>> effectsList = new ArrayList<>();
            for (Effect effect : effects) {
                Map<String, Object> effectMap = new HashMap<>();
                effectMap.put("type", effect.getType().name().toLowerCase());
                effectMap.put("frameIndex", effect.getFrameIndex());
                effectMap.put("x", effect.getX());
                effectMap.put("y", effect.getY());
                effectsList.add(effectMap);
            }

            Map<String, Object> gameStateMsg = new HashMap<>();
            gameStateMsg.put("type", "GAME_STATE");
            gameStateMsg.put("players", playersList);
            gameStateMsg.put("obstacles", obstaclesList);
            gameStateMsg.put("effects", effectsList);
            gameStateMsg.put("groundType", currentGroundType.name().toLowerCase());
            gameStateMsg.put("worldOffset", worldOffset);
            gameStateMsg.put("gameTime", (System.currentTimeMillis() - gameStartTime) / 1000);

            broadcastEvent("GAME_STATE", gameStateMsg);
        }
    }

    private void checkGameOver() {
        long aliveCount = lobby.getPlayers().values().stream()
                .filter(Player::getIsAlive)
                .count();

        double maxDistance = lobby.getPlayers().values().stream()
                .mapToDouble(Player::getDistance)
                .max()
                .orElse(0);

        if (aliveCount <= 1 || maxDistance >= config.getTrackLength()) {
            lobby.setStatus(LobbyStatus.FINISHED);
            running = false;
            log.info("Game over detected - stopping engine for lobby {}", lobby.getCode());
        }
    }

    private void finishGame() {
        List<Player> alivePlayers = lobby.getPlayers().values().stream()
                .filter(Player::getIsAlive)
                .toList();

        List<Player> sortedByDistance = lobby.getPlayers().values().stream()
                .sorted((a, b) -> Double.compare(b.getDistance(), a.getDistance()))
                .toList();

        Player winner = alivePlayers.isEmpty() ? sortedByDistance.get(0) : alivePlayers.get(0);
        String winnerId = winner != null ? winner.getUserId() : null;

        Integer totalPool = lobby.getPlayers().values().stream()
                .mapToInt(Player::getBetAmount)
                .sum();

        List<PlayerResult> results = new ArrayList<>();
        int position = 1;

        for (Player player : sortedByDistance) {
            Integer coinsEarned = player.getUserId().equals(winnerId) ? totalPool : 0;

            PlayerResult result = PlayerResult.builder()
                    .playerId(player.getUserId())
                    .playerName(player.getUsername())
                    .position(position++)
                    .coinsEarned(coinsEarned)
                    .color(player.getColor())
                    .distance(player.getDistance())
                    .timeAlive(System.currentTimeMillis() - gameStartTime)
                    .build();
            results.add(result);
        }

        log.info("Game finished in lobby {}. Winner: {}, Pool: {}", lobby.getCode(), winnerId, totalPool);

        if (eventPublisher != null) {
            eventPublisher.publishSettlement(lobby, results, winnerId, totalPool);
        }

        Map<String, Object> endMsg = new HashMap<>();
        endMsg.put("type", "GAME_END");
        endMsg.put("results", results);
        broadcastEvent("GAME_END", endMsg);
    }

    private static class PlayerInput {
        final String action;
        final Long timestamp;

        PlayerInput(String action, Long timestamp) {
            this.action = action;
            this.timestamp = timestamp;
        }
    }
}