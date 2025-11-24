package com.gameengine.core;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.input.InputManager;
import com.gameengine.math.CollisionUtils;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.List;
import java.util.function.Consumer;

public class GameLogic {
    private Scene scene;
    private InputManager inputManager;
    private Consumer<GameObject> onPlayerEnemyCollision;
    private GameEngine gameEngine;
    private boolean gameOver = false;

    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
    }

    public void setGameEngine(GameEngine engine) {
        this.gameEngine = engine;
    }
    
    public void setOnPlayerEnemyCollision(Consumer<GameObject> onPlayerEnemyCollision) {
        this.onPlayerEnemyCollision = onPlayerEnemyCollision;
    }

    public boolean isGameOver() {
        return gameOver;
    }
    
    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public GameObject getUserPlayer() {
        for (GameObject obj : scene.getGameObjects()) {
            if ("Player".equals(obj.getName())) {
                return obj;
            }
        }
        return null;
    }

    public void handlePlayerInput(float deltaTime) {
        if (gameOver) return;

        GameObject player = getUserPlayer();
        if (player == null) return;
        
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        
        if (transform == null || physics == null) return;
        
        Vector2 movement = new Vector2();
        
        // W / Up
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(265)) {
            movement.y -= 1;
        }
        // S / Down
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(264)) {
            movement.y += 1;
        }
        // A / Left
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(263)) {
            movement.x -= 1;
        }
        // D / Right
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(262)) {
            movement.x += 1;
        }
        
        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200); // Speed from j03 was ~140-200
            physics.setVelocity(movement);
        } else {
            // Optional: stop if no input (or let friction handle it)
            // physics.setVelocity(new Vector2(0,0)); 
        }
        
        // Manual boundary check/clamping if PhysicsSystem doesn't catch it perfectly or we want hard stops
        Vector2 pos = transform.getPosition();
        int screenW = (gameEngine != null && gameEngine.getRenderer() != null) ? gameEngine.getRenderer().getWidth() : 800;
        int screenH = (gameEngine != null && gameEngine.getRenderer() != null) ? gameEngine.getRenderer().getHeight() : 600;
        
        boolean clamped = false;
        if (pos.x < 0) { pos.x = 0; clamped = true; }
        if (pos.y < 0) { pos.y = 0; clamped = true; }
        if (pos.x > screenW - 20) { pos.x = screenW - 20; clamped = true; }
        if (pos.y > screenH - 20) { pos.y = screenH - 20; clamped = true; }
        
        if (clamped) {
            transform.setPosition(pos);
        }
    }
    
    // j05 PhysicsSystem handles the physics update loop, so we don't need an explicit updatePhysics() here
    // unless we want custom behavior not covered by PhysicsSystem.
    // But we do need to check collisions.

    public void checkCollisions() {
        if (gameOver) return;

        GameObject player = getUserPlayer();
        if (player == null || !player.isActive()) return;

        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null) return;

        CollisionUtils.Rect playerRect = CollisionUtils.playerBounds(playerTransform.getPosition());
        if (playerRect == null) return;

        for (GameObject obj : scene.getGameObjects()) {
            if ("Enemy".equals(obj.getName()) && obj.isActive()) {
                TransformComponent enemyTransform = obj.getComponent(TransformComponent.class);
                CollisionUtils.Rect enemyRect = CollisionUtils.enemyBounds(obj, enemyTransform);
                
                if (enemyRect != null && playerRect.intersects(enemyRect)) {
                    if (onPlayerEnemyCollision != null) {
                        onPlayerEnemyCollision.accept(obj);
                    }
                }
            }
        }
    }

    public void cleanup() {
        // Nothing special to cleanup if we removed the thread pool
    }
}