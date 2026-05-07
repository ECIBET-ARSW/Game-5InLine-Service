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
    private final Map<String, Double> playerVelocityY;
    private final Map<String, Boolean> playerIsJumping;
    private final Map<String, Long> lastActiveTime;
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
    private double lastObstacleX;
    private String gameEndMessage;
    private boolean testingMode = true;

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
        this.playerVelocityY = new HashMap<>();
        this.playerIsJumping = new HashMap<>();
        this.lastActiveTime = new HashMap<>();
        this.running = true;
        this.worldOffset = 0.0;
        this.currentSpeed = 0.0;
        this.currentGroundType = GroundType.NORMAL;
        this.allClientsReady = false;
        this.gameStarted = false;
        this.countdownTickLast = 0;
        this.random = new Random();
        this.lastObstacleX = 0;
        this.gameEndMessage = "";

        double startX = 400.0;
        double startY = config.getGroundY();

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
            playerVelocityY.put(player.getUserId(), 0.0);
            playerIsJumping.put(player.getUserId(), false);
            lastActiveTime.put(player.getUserId(), System.currentTimeMillis());
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
        }
        lastActiveTime.put(userId, System.currentTimeMillis());
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
    }

    private void broadcastGameEndMessage(String message) {
        Map<String, Object> endMsg = new HashMap<>();
        endMsg.put("type", "GAME_END_MESSAGE");
        endMsg.put("message", message);
        broadcastEvent("GAME_END_MESSAGE", endMsg);
        log.info("Game end message: {}", message);
    }

    @Override
    public void run() {
        lobby.setStatus(LobbyStatus.IN_PROGRESS);
        gameStartTime = System.currentTimeMillis();
        lastTickTime = gameStartTime;
        lastSpeedIncrease = gameStartTime;

        log.info("Game engine started for lobby {}", lobby.getCode());

        while (running && lobby.getStatus() == LobbyStatus.IN_PROGRESS) {
            long now = System.currentTimeMillis();

            checkInactivePlayers(now);

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

    private void checkInactivePlayers(long now) {
        List<String> inactivePlayers = new ArrayList<>();
        for (Map.Entry<String, Long> entry : lastActiveTime.entrySet()) {
            if (now - entry.getValue() > 10000) {
                inactivePlayers.add(entry.getKey());
            }
        }

        for (String userId : inactivePlayers) {
            Player player = lobby.getPlayers().get(userId);
            if (player != null && player.getIsAlive()) {
                log.info("Player {} marked as inactive (disconnected)", player.getUsername());
                player.setIsAlive(false);
                lastActiveTime.remove(userId);
            }
        }
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
                if ("jump".equals(pi.action) && player.getIsOnGround()) {
                    playerVelocityY.put(player.getUserId(), config.getJumpPower());
                    player.setIsOnGround(false);
                    playerIsJumping.put(player.getUserId(), true);
                    player.setLastJumpTime(System.currentTimeMillis());
                    log.info("JUMP - User: {}, Velocity: {}", player.getUsername(), config.getJumpPower());
                    Effect jumpEffect = new Effect(EffectType.JUMP, player.getX(), player.getY() + 20);
                    effects.add(jumpEffect);
                }

                if ("run".equals(pi.action)) {
                    playerRunning.put(player.getUserId(), true);
                }

                if ("stop".equals(pi.action)) {
                    playerRunning.put(player.getUserId(), false);
                }

                if ("slide".equals(pi.action) && player.getIsOnGround()) {
                    player.setIsSliding(true);
                    player.setSlideEndTime(System.currentTimeMillis() + (long) config.getSlideDurationMs());
                }
            }
        }
    }

    private void updatePhysics() {
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            boolean isRunning = playerRunning.getOrDefault(player.getUserId(), false);

            if (isRunning) {
                double moveDistance = Math.min(currentSpeed * 1.2, 8.0);
                player.setX(player.getX() + moveDistance);
                player.setDistance(player.getDistance() + (moveDistance / 10.0));
            }

            double velocityY = playerVelocityY.getOrDefault(player.getUserId(), 0.0);

            if (!player.getIsOnGround()) {
                velocityY += config.getGravity();
                playerVelocityY.put(player.getUserId(), velocityY);
                player.setY(player.getY() + velocityY);

                boolean landedOnPlatform = false;
                for (Obstacle obstacle : obstacles) {
                    if (obstacle.getType() == ObstacleType.PLATFORM) {
                        double platformLeft = obstacle.getX() - worldOffset;
                        double platformRight = obstacle.getX() - worldOffset + obstacle.getWidth();
                        double playerBottom = player.getY() + 28;

                        if (player.getX() + 16 > platformLeft && player.getX() + 16 < platformRight &&
                                playerBottom >= obstacle.getY() && playerBottom <= obstacle.getY() + 20 &&
                                velocityY >= 0) {
                            player.setY(obstacle.getY() - 28);
                            player.setIsOnGround(true);
                            player.setIsSliding(false);
                            playerVelocityY.put(player.getUserId(), 0.0);
                            playerIsJumping.put(player.getUserId(), false);
                            landedOnPlatform = true;
                            log.info("Player {} landed on platform at Y={}", player.getUsername(), obstacle.getY());
                            break;
                        }
                    }
                }

                if (!landedOnPlatform && player.getY() >= config.getGroundY()) {
                    player.setY(config.getGroundY());
                    player.setIsOnGround(true);
                    player.setIsSliding(false);
                    playerVelocityY.put(player.getUserId(), 0.0);
                    playerIsJumping.put(player.getUserId(), false);

                    Effect dustEffect = new Effect(EffectType.DUST, player.getX(), player.getY() + 20);
                    effects.add(dustEffect);
                }
            } else {
                playerVelocityY.put(player.getUserId(), 0.0);
                playerIsJumping.put(player.getUserId(), false);
            }

            if (player.getIsSliding()) {
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
            boolean isJumping = playerIsJumping.getOrDefault(player.getUserId(), false);

            if (player.getIsSliding()) {
                player.setAnimation(AnimationType.SLIDING.name());
            } else if (!player.getIsOnGround() && isJumping) {
                player.setAnimation(AnimationType.JUMPING.name());
            } else if (isRunning && player.getIsOnGround()) {
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
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            for (Obstacle obstacle : new ArrayList<>(obstacles)) {
                if (isCollision(player, obstacle)) {
                    handleCollision(player, obstacle);
                }
            }

            if (!testingMode) {
                if (player.getX() < worldOffset - 100) {
                    player.setLives(0);
                    player.setIsAlive(false);
                    log.info("Player {} was left behind and died", player.getUsername());
                }

                if (player.getY() > 600) {
                    player.setLives(0);
                    player.setIsAlive(false);
                    Effect explosion = new Effect(EffectType.EXPLOSION, player.getX(), player.getY());
                    effects.add(explosion);
                    log.info("Player {} fell to death", player.getUsername());
                }
            }
        }
    }

    private boolean isCollision(Player player, Obstacle obstacle) {
        double playerLeft = player.getX() + 10;
        double playerRight = player.getX() + 22;
        double playerTop = player.getY() + 10;
        double playerBottom = player.getY() + 28;

        if (player.getIsSliding()) {
            playerTop = player.getY() + 20;
            playerBottom = player.getY() + 28;
        }

        double obstacleLeft = obstacle.getX() - worldOffset + 4;
        double obstacleRight = obstacle.getX() - worldOffset + obstacle.getWidth() - 4;
        double obstacleTop = obstacle.getY() + 4;
        double obstacleBottom = obstacle.getY() + obstacle.getHeight() - 4;

        boolean collision = playerRight > obstacleLeft && playerLeft < obstacleRight &&
                playerBottom > obstacleTop && playerTop < obstacleBottom;

        if (collision && obstacle.getType() == ObstacleType.PLATFORM) {
            if (playerBottom > obstacleTop + 8) {
                return false;
            }
        }

        if (collision) {
            log.info("COLLISION - Player: {}, Obstacle: {}, Player X: {}, Obstacle X: {}",
                    player.getUsername(), obstacle.getType(), player.getX(), obstacle.getX() - worldOffset);
        }

        return collision;
    }

    private void handleCollision(Player player, Obstacle obstacle) {
        log.info("HANDLING COLLISION - Player: {}, Obstacle: {}", player.getUsername(), obstacle.getType());

        if (testingMode) {
            log.info("TESTING MODE: Collision detected but no damage applied");
            Effect hitEffect = new Effect(EffectType.EXPLOSION, obstacle.getX(), obstacle.getY());
            effects.add(hitEffect);

            if (obstacle.getType() == ObstacleType.TRAMPOLINE) {
                player.setIsOnGround(false);
                playerVelocityY.put(player.getUserId(), -16.0);
                playerIsJumping.put(player.getUserId(), true);
                Effect jumpEffect = new Effect(EffectType.JUMP, player.getX(), player.getY());
                effects.add(jumpEffect);
                log.info("TRAMPOLINE - Player {} bounced (testing mode)", player.getUsername());
            }

            if (obstacle.getType() == ObstacleType.POWERUP_SPEED) {
                currentSpeed = Math.min(currentSpeed + 1.5, config.getMaxSpeed());
                Effect starEffect = new Effect(EffectType.STAR, obstacle.getX(), obstacle.getY());
                effects.add(starEffect);
                log.info("POWERUP - Player {} got speed boost (testing mode)", player.getUsername());
            }

            if (obstacle.getType() == ObstacleType.PLATFORM) {
                if (player.getY() + 28 > obstacle.getY() && player.getY() + 28 < obstacle.getY() + 25) {
                    player.setY(obstacle.getY() - 28);
                    player.setIsOnGround(true);
                    playerVelocityY.put(player.getUserId(), 0.0);
                    playerIsJumping.put(player.getUserId(), false);
                    log.info("PLATFORM - Player {} landed on platform (testing mode)", player.getUsername());
                }
            }

            obstacles.remove(obstacle);
            return;
        }

        switch (obstacle.getType()) {
            case TRAMPOLINE:
                player.setIsOnGround(false);
                playerVelocityY.put(player.getUserId(), -16.0);
                playerIsJumping.put(player.getUserId(), true);
                Effect jumpEffect = new Effect(EffectType.JUMP, player.getX(), player.getY());
                effects.add(jumpEffect);
                log.info("TRAMPOLINE - Player {} bounced", player.getUsername());
                obstacles.remove(obstacle);
                break;
            case POWERUP_SPEED:
                currentSpeed = Math.min(currentSpeed + 1.5, config.getMaxSpeed());
                Effect starEffect = new Effect(EffectType.STAR, obstacle.getX(), obstacle.getY());
                effects.add(starEffect);
                log.info("POWERUP - Player {} got speed boost", player.getUsername());
                obstacles.remove(obstacle);
                break;
            case PLATFORM:
                if (player.getY() + 28 > obstacle.getY() && player.getY() + 28 < obstacle.getY() + 25) {
                    player.setY(obstacle.getY() - 28);
                    player.setIsOnGround(true);
                    playerVelocityY.put(player.getUserId(), 0.0);
                    playerIsJumping.put(player.getUserId(), false);
                    log.info("PLATFORM - Player {} landed on platform at Y={}", player.getUsername(), obstacle.getY());
                }
                break;
            default:
                int damage = 1;
                if (obstacle.getType() == ObstacleType.BLADE) damage = 2;
                if (obstacle.getType() == ObstacleType.PIT) damage = 3;

                player.setLives(player.getLives() - damage);
                Effect explosionHit = new Effect(EffectType.EXPLOSION, obstacle.getX(), obstacle.getY());
                effects.add(explosionHit);
                log.info("DAMAGE - Player {} hit {}, damage: {}, lives remaining: {}",
                        player.getUsername(), obstacle.getType(), damage, player.getLives());

                if (player.getLives() <= 0) {
                    player.setIsAlive(false);
                    Effect deathExplosion = new Effect(EffectType.EXPLOSION, player.getX(), player.getY());
                    effects.add(deathExplosion);
                    log.info("DEATH - Player {} died", player.getUsername());
                }
                obstacles.remove(obstacle);
                break;
        }
    }

    private void generateObstacles() {
        if (obstacles.size() >= 8) return;

        double minDistance = 350;
        double distanceSinceLast = (worldOffset + 500) - lastObstacleX;

        if (distanceSinceLast < minDistance && lastObstacleX > 0) return;

        if (random.nextDouble() < 0.025) {
            double zone = worldOffset;
            double x = worldOffset + 800 + random.nextDouble() * 400;
            double y = config.getGroundY();
            ObstacleType type;

            if (zone < 2000) {
                type = getEasyObstacle();
                y = getYForObstacle(type);
            } else if (zone < 4000) {
                type = getMediumObstacle();
                y = getYForObstacle(type);
            } else if (zone < 6000) {
                type = getHardObstacle();
                y = getYForObstacle(type);
            } else {
                type = getAllObstacle();
                y = getYForObstacle(type);
            }

            if (random.nextDouble() < 0.3 && zone > 2500) {
                y = config.getPlatformY();
                type = ObstacleType.PLATFORM;
            }

            if (random.nextDouble() < 0.2 && zone > 4500) {
                y = config.getPlatformHighY();
                type = ObstacleType.PLATFORM;
            }

            if (random.nextDouble() < 0.15 && zone > 3500) {
                y = config.getHighHeightY();
                type = ObstacleType.BLADE;
            }

            Obstacle obstacle = new Obstacle(type, x, y);
            obstacles.add(obstacle);
            lastObstacleX = x;
            log.info("Obstacle generated: {} at position {} (y={})", type, x, y);
        }
    }

    private double getYForObstacle(ObstacleType type) {
        switch (type) {
            case PLATFORM:
                return config.getPlatformY();
            case TRAMPOLINE:
                return config.getLowHeightY();
            case POWERUP_SPEED:
                return config.getLowHeightY();
            case BLADE:
                return config.getMediumHeightY();
            default:
                return config.getGroundY();
        }
    }

    private ObstacleType getEasyObstacle() {
        ObstacleType[] types = {ObstacleType.SPIKE, ObstacleType.ROCK, ObstacleType.TRAMPOLINE, ObstacleType.POWERUP_SPEED};
        return types[random.nextInt(types.length)];
    }

    private ObstacleType getMediumObstacle() {
        ObstacleType[] types = {ObstacleType.SPIKE, ObstacleType.ROCK, ObstacleType.TRAMPOLINE, ObstacleType.LAVA, ObstacleType.POWERUP_SPEED};
        return types[random.nextInt(types.length)];
    }

    private ObstacleType getHardObstacle() {
        ObstacleType[] types = {ObstacleType.SPIKE, ObstacleType.ROCK, ObstacleType.TRAMPOLINE, ObstacleType.LAVA, ObstacleType.BLADE, ObstacleType.PIT, ObstacleType.POWERUP_SPEED};
        return types[random.nextInt(types.length)];
    }

    private ObstacleType getAllObstacle() {
        ObstacleType[] types = ObstacleType.values();
        return types[random.nextInt(types.length)];
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

        if (aliveCount <= 1) {
            if (aliveCount == 1) {
                Player winner = lobby.getPlayers().values().stream()
                        .filter(Player::getIsAlive)
                        .findFirst()
                        .orElse(null);
                if (winner != null) {
                    gameEndMessage = "🏆 " + winner.getUsername() + " GANA! 🏆";
                    broadcastGameEndMessage(gameEndMessage);
                    log.info("Winner: {}", winner.getUsername());
                }
            } else if (aliveCount == 0) {
                gameEndMessage = "💀 EMPATE! Todos murieron 💀";
                broadcastGameEndMessage(gameEndMessage);
                log.info("All players died - Tie!");
            }

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