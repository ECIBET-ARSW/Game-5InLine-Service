package com.ecibet.game5inline.service;

import com.ecibet.game5inline.model.Obstacle;
import com.ecibet.game5inline.model.enums.ObstacleType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class ObstacleGenerator {

    private final Random random = new Random();
    private double difficultyMultiplier = 1.0;

    public List<Obstacle> generateNext(double currentWorldOffset, int currentObstacleCount) {
        List<Obstacle> newObstacles = new ArrayList<>();

        int maxObstacles = 5;
        double spawnProbability = 0.02 * difficultyMultiplier;

        if (currentObstacleCount < maxObstacles && random.nextDouble() < spawnProbability) {
            ObstacleType[] types = ObstacleType.values();
            ObstacleType type = types[random.nextInt(types.length)];

            double x = currentWorldOffset + 800;
            double y = calculateYPosition(type);

            Obstacle obstacle = new Obstacle(type, x, y);
            newObstacles.add(obstacle);
        }

        return newObstacles;
    }

    private double calculateYPosition(ObstacleType type) {
        return switch (type) {
            case PLATFORM -> 350.0;
            case TRAMPOLINE -> 384.0;
            default -> 400.0 - type.getObstacleHeight();
        };
    }

    public void increaseDifficulty() {
        difficultyMultiplier = Math.min(difficultyMultiplier * 1.05, 2.0);
    }

    public void resetDifficulty() {
        difficultyMultiplier = 1.0;
    }
}