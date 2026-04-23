package com.ecibet.game5inline.service;

import com.ecibet.game5inline.messaging.GameEventPublisher;
import com.ecibet.game5inline.model.*;
import com.ecibet.game5inline.model.enums.AnimationType;
import com.ecibet.game5inline.model.enums.GroundType;
import com.ecibet.game5inline.model.enums.LobbyStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class GameEngine implements Runnable {

    private final Lobby lobby;
    private final GameConfig config;
    private final Map<String, Queue<PlayerInput>> inputQueues;
    private final List<Obstacle> obstacles;
    private final List<Effect> effects;
    private final GameEventPublisher eventPublisher;
    private boolean running;
    private long lastTickTime;
    private long gameStartTime;
    private Double worldOffset;
    private Double currentSpeed;
    private long lastSpeedIncrease;
    private GroundType currentGroundType;

    public GameEngine(Lobby lobby, GameConfig config, GameEventPublisher eventPublisher) {
        this.lobby = lobby;
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.inputQueues = new HashMap<>();
        this.obstacles = new ArrayList<>();
        this.effects = new ArrayList<>();
        this.running = true;
        this.worldOffset = 0.0;
        this.currentSpeed = config.getWorldSpeed();
        this.currentGroundType = GroundType.NORMAL;

        for (Player player : lobby.getPlayers().values()) {
            inputQueues.put(player.getUserId(), new ConcurrentLinkedQueue<>());
            player.setX(100.0);
            player.setY(400.0);
            player.setIsAlive(true);
            player.setLives(config.getInitialLives());
            player.setDistance(0.0);
            player.setAnimation(AnimationType.RUNNING.name());
            player.setFrameIndex(0);
            player.setIsOnGround(true);
        }
    }

    public void addInput(String userId, String action, Long timestamp) {
        Queue<PlayerInput> queue = inputQueues.get(userId);
        if (queue != null) {
            queue.offer(new PlayerInput(action, timestamp));
        }
    }

    public void stop() {
        this.running = false;
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
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            Queue<PlayerInput> queue = inputQueues.get(player.getUserId());
            if (queue == null) continue;

            PlayerInput input = queue.poll();
            if (input == null) continue;

            if ("jump".equals(input.action) && Boolean.TRUE.equals(player.getIsOnGround())) {
                player.setIsOnGround(false);
                player.setLastJumpTime(System.currentTimeMillis());
                player.setY(player.getY() + config.getJumpPower());

                Effect jumpEffect = new Effect(com.ecibet.game5inline.model.enums.EffectType.JUMP, player.getX(), player.getY() + 20);
                effects.add(jumpEffect);
            }

            if ("slide".equals(input.action) && Boolean.TRUE.equals(player.getIsOnGround())) {
                player.setIsSliding(true);
                player.setSlideEndTime(System.currentTimeMillis() + config.getSlideDurationMs());
            }
        }
    }

    private void updatePhysics() {
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            if (Boolean.FALSE.equals(player.getIsOnGround())) {
                double newY = player.getY() + config.getGravity();
                player.setY(newY);

                if (player.getY() >= 400) {
                    player.setY(400.0);
                    player.setIsOnGround(true);
                    player.setIsSliding(false);
                }
            }

            if (Boolean.TRUE.equals(player.getIsSliding())) {
                if (System.currentTimeMillis() > player.getSlideEndTime()) {
                    player.setIsSliding(false);
                }
            }

            player.setDistance(player.getDistance() + currentSpeed / 10.0);
        }
    }

    private void updateAnimations(long now) {
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) {
                player.setAnimation(AnimationType.DYING.name());
                continue;
            }

            if (Boolean.TRUE.equals(player.getIsSliding())) {
                player.setAnimation(AnimationType.SLIDING.name());
            } else if (Boolean.FALSE.equals(player.getIsOnGround())) {
                player.setAnimation(AnimationType.JUMPING.name());
            } else {
                player.setAnimation(AnimationType.RUNNING.name());
            }

            int frameIndex = (int) ((now / 50) % getFrameCountForAnimation(player.getAnimation()));
            player.setFrameIndex(frameIndex);
        }
    }

    private int getFrameCountForAnimation(String animation) {
        return switch (animation) {
            case "RUNNING" -> 6;
            case "JUMPING" -> 4;
            case "SLIDING" -> 2;
            case "DYING" -> 5;
            case "CELEBRATING" -> 4;
            default -> 3;
        };
    }

    private void updateWorldScroll() {
        worldOffset += currentSpeed;
    }

    private void checkCollisions() {
        for (Player player : lobby.getPlayers().values()) {
            if (!player.getIsAlive()) continue;

            for (Obstacle obstacle : obstacles) {
                if (isCollision(player, obstacle)) {
                    handleCollision(player, obstacle);
                    break;
                }
            }

            if (player.getY() > 500) {
                player.setLives(player.getLives() - 1);
                if (player.getLives() <= 0) {
                    player.setIsAlive(false);
                    player.setAnimation(AnimationType.DYING.name());

                    Effect explosion = new Effect(com.ecibet.game5inline.model.enums.EffectType.EXPLOSION, player.getX(), player.getY());
                    effects.add(explosion);
                } else {
                    player.setY(400.0);
                    player.setIsOnGround(true);
                    player.setX(100.0);
                }
            }
        }

        obstacles.removeIf(o -> o.getX() + o.getWidth() < worldOffset - 500);
    }

    private boolean isCollision(Player player, Obstacle obstacle) {
        double playerLeft = player.getX();
        double playerRight = player.getX() + 30;
        double playerTop = player.getY();
        double playerBottom = player.getY() + 30;

        if (Boolean.TRUE.equals(player.getIsSliding())) {
            playerTop = player.getY() + 15;
        }

        double obstacleLeft = obstacle.getX() - worldOffset;
        double obstacleRight = obstacle.getX() - worldOffset + obstacle.getWidth();
        double obstacleTop = obstacle.getY();
        double obstacleBottom = obstacle.getY() + obstacle.getHeight();

        return playerRight > obstacleLeft && playerLeft < obstacleRight &&
                playerBottom > obstacleTop && playerTop < obstacleBottom;
    }

    private void handleCollision(Player player, Obstacle obstacle) {
        switch (obstacle.getType()) {
            case SPIKE, LAVA, BLADE -> {
                player.setLives(0);
                player.setIsAlive(false);
                player.setAnimation(AnimationType.DYING.name());
                Effect explosion = new Effect(com.ecibet.game5inline.model.enums.EffectType.EXPLOSION, player.getX(), player.getY());
                effects.add(explosion);
            }
            case WALL -> {
                player.setX(player.getX() - 20);
            }
            case TRAMPOLINE -> {
                player.setIsOnGround(false);
                player.setY(player.getY() - 15);
            }
            case POWERUP_SPEED -> {
                currentSpeed += 2.0;
                obstacles.remove(obstacle);
            }
            default -> {
                player.setLives(player.getLives() - 1);
                if (player.getLives() <= 0) {
                    player.setIsAlive(false);
                    player.setAnimation(AnimationType.DYING.name());
                }
            }
        }
    }

    private void generateObstacles() {
        if (obstacles.size() < 5 && Math.random() < 0.02) {
            com.ecibet.game5inline.model.enums.ObstacleType[] types = com.ecibet.game5inline.model.enums.ObstacleType.values();
            com.ecibet.game5inline.model.enums.ObstacleType type = types[(int) (Math.random() * types.length)];
            double x = worldOffset + 800;
            double y = 400 - (type == com.ecibet.game5inline.model.enums.ObstacleType.PLATFORM ? 50 : 0);

            Obstacle obstacle = new Obstacle(type, x, y);
            obstacles.add(obstacle);
        }
    }

    private void updateEffects(long now) {
        effects.removeIf(Effect::isExpired);

        for (Effect effect : effects) {
            long elapsed = now - effect.getCreatedAt();
            int frameDuration = 50;
            int frameIndex = (int) (elapsed / frameDuration) % effect.getType().getFrameCount();
            effect.setFrameIndex(frameIndex);
        }
    }

    private void increaseSpeedIfNeeded(long now) {
        long elapsed = now - lastSpeedIncrease;
        if (elapsed >= 10000) {
            currentSpeed = currentSpeed * 1.05;
            lastSpeedIncrease = now;
            log.debug("Speed increased to {}", currentSpeed);
        }
    }

    private void broadcastGameState() {
        // Implementation would send GameStateMessage to all sessions
    }

    private void checkGameOver() {
        long aliveCount = lobby.getPlayers().values().stream()
                .filter(Player::getIsAlive)
                .count();

        long finishedCount = lobby.getPlayers().values().stream()
                .filter(p -> !p.getIsAlive() || AnimationType.DYING.name().equals(p.getAnimation()))
                .count();

        if (aliveCount <= 0) {
            lobby.setStatus(LobbyStatus.FINISHED);
            running = false;
        }

        if (aliveCount == 1 && finishedCount >= lobby.getPlayers().size() - 1) {
            lobby.setStatus(LobbyStatus.FINISHED);
            running = false;
        }
    }

    private void finishGame() {
        List<Player> sortedByDistance = lobby.getPlayers().values().stream()
                .sorted((a, b) -> Double.compare(b.getDistance(), a.getDistance()))
                .toList();

        Player winner = sortedByDistance.isEmpty() ? null : sortedByDistance.get(0);
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