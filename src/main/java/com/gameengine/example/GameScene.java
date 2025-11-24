package com.gameengine.example;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.CollisionUtils;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameScene extends Scene {

    private GameEngine engine;
    private IRenderer renderer;
    private InputManager inputManager;
    private Random random;

    private float elapsedTime;
    private float spawnTimer;

    private GameObject player;
    private final List<GameObject> fireballs = new ArrayList<>();
    private boolean wasLeftMousePressed;
    private int score;
    private int maxHealth;
    private int playerHealth;
    private boolean playerDead;

    private final float FIREBALL_SPEED = 350f;
    private final float FIREBALL_RADIUS = 6f;
    private final int LEFT_MOUSE_BUTTON = 0; // GLFW mouse button 0 is left
    private final float MIN_SPAWN_INTERVAL = 0.1f;
    private final float BASE_SPAWN_INTERVAL = 0.8f;

    private boolean awaitingRestartConfirmation = false;

    public GameScene(GameEngine engine) {
        super("GameScene");
        this.engine = engine;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.renderer = engine.getRenderer();
        this.inputManager = engine.getInputManager();
        this.random = new Random();
        
        resetGame();
    }

    @Override
    public void update(float deltaTime) {
        if (awaitingRestartConfirmation) {
            // Check for restart confirmation (SPACE or ENTER) or return to menu (ESC)
            if (inputManager.isKeyJustPressed(32) || inputManager.isKeyJustPressed(257)) { // Space or Enter
                awaitingRestartConfirmation = false;
                resetGame();
            } else if (inputManager.isKeyJustPressed(256)) { // ESC
                engine.setScene(new MenuScene(engine, "MainMenu"));
                return;
            }
            return;
        }

        super.update(deltaTime);
        handlePlayerInput(deltaTime);
        handleShooting();
        handleFireballEnemyCollisions();
        checkPlayerEnemyCollisions();
        cleanupInactiveFireballs();

        elapsedTime += deltaTime;
        spawnTimer += deltaTime;

        // Increase difficulty over time by spawning enemies faster
        float spawnInterval = Math.max(MIN_SPAWN_INTERVAL, BASE_SPAWN_INTERVAL - elapsedTime * 0.05f);
        if (spawnTimer > spawnInterval) {
            createEnemy();
            spawnTimer = 0f;
        }
    }

    @Override
    public void render() {
        // Draw background
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.1f, 0.1f, 0.2f, 1.0f);

        // Render all game objects (player, enemies, fireballs, etc.)
        super.render();

        // Draw UI
        renderer.drawText(20, 30, "Score: " + score, 1.0f, 1.0f, 1.0f, 1.0f);
        renderer.drawText(renderer.getWidth() - 200, 30, String.format("Time: %.1f s", elapsedTime), 1.0f, 1.0f, 1.0f, 1.0f);
    
        if (awaitingRestartConfirmation) {
            float cx = renderer.getWidth() / 2.0f;
            float cy = renderer.getHeight() / 2.0f;
            renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.0f, 0.0f, 0.0f, 0.35f);
            renderer.drawRect(cx - 250, cy - 60, 500, 120, 0.1f, 0.1f, 0.1f, 0.8f);
            renderer.drawText(cx - 100, cy - 20, "GAME OVER", 1.0f, 0.2f, 0.2f, 1.0f);
            renderer.drawText(cx - 220, cy + 30, "Press SPACE or ENTER to restart", 0.9f, 0.9f, 0.9f, 1.0f);
        }
    }

    private void handlePlayerInput(float deltaTime) {
        if (player == null || playerDead) return;

        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        if (physics == null) return;

        Vector2 movement = new Vector2();
        // Use GLFW key codes
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(265)) { // W or Up
            movement.y -= 1;
        }
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(264)) { // S or Down
            movement.y += 1;
        }
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(263)) { // A or Left
            movement.x -= 1;
        }
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(262)) { // D or Right
            movement.x += 1;
        }

        if (movement.magnitude() > 0) {
            // Apply force instead of setting velocity directly for better physics interaction
            physics.applyForce(movement.normalize().multiply(55000f * deltaTime));
        }
    }

    private void createPlayer() {
        player = new GameObject("Player") {
            private Vector2 basePosition;

            @Override
            public void render() {
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform == null) return;
                basePosition = transform.getPosition();

                // Body
                renderer.drawRect(basePosition.x - 8, basePosition.y - 10, 16, 20, 1.0f, 0.0f, 0.0f, 1.0f);
                // Head
                renderer.drawRect(basePosition.x - 6, basePosition.y - 22, 12, 12, 1.0f, 0.5f, 0.0f, 1.0f);
                // Left arm
                renderer.drawRect(basePosition.x - 13, basePosition.y - 5, 6, 12, 1.0f, 0.8f, 0.0f, 1.0f);
                // Right arm
                renderer.drawRect(basePosition.x + 7, basePosition.y - 5, 6, 12, 0.0f, 1.0f, 0.0f, 1.0f);

                drawPlayerHealthBar(basePosition);
            }
        };

        player.addComponent(new TransformComponent(new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f)));
        PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.92f); // Slightly lower friction for more slide

        addGameObject(player);
    }

    private void createEnemies(int count) {
        for (int i = 0; i < count; i++) {
            createEnemy();
        }
    }

    private void createEnemy() {
        final float chaseSpeed = 30f + random.nextFloat() * 15f;

        GameObject enemy = new GameObject("Enemy") {
            @Override
            public void update(float deltaTime) {
                if (player == null || !player.isActive()) {
                    return;
                }
                TransformComponent enemyTransform = getComponent(TransformComponent.class);
                TransformComponent playerTransform = player.getComponent(TransformComponent.class);
                PhysicsComponent physics = getComponent(PhysicsComponent.class);

                if (enemyTransform != null && playerTransform != null && physics != null) {
                    Vector2 direction = playerTransform.getPosition().subtract(enemyTransform.getPosition());
                    if (direction.magnitude() > 1f) {
                        Vector2 desiredVelocity = direction.normalize().multiply(chaseSpeed);
                        // Apply force for acceleration
                        physics.applyForce(desiredVelocity.subtract(physics.getVelocity()).multiply(5f));
                    }
                }
            }
        };

        Vector2 position = new Vector2(random.nextFloat() * renderer.getWidth(), random.nextFloat() * renderer.getHeight());
        enemy.addComponent(new TransformComponent(position));
        RenderComponent render = enemy.addComponent(new RenderComponent(RenderComponent.RenderType.RECTANGLE,
            new Vector2(20, 20), new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)));
        render.setRenderer(renderer);
        PhysicsComponent physics = enemy.addComponent(new PhysicsComponent(0.5f));
        physics.setFriction(0.95f);

        addGameObject(enemy);
    }

    private void createDecorations(int count) {
        for (int i = 0; i < count; i++) {
            GameObject decoration = new GameObject("Decoration");
            Vector2 position = new Vector2(random.nextFloat() * renderer.getWidth(), random.nextFloat() * renderer.getHeight());
            decoration.addComponent(new TransformComponent(position));
            RenderComponent render = decoration.addComponent(new RenderComponent(RenderComponent.RenderType.CIRCLE,
                new Vector2(5, 5), new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)));
            render.setRenderer(renderer);
            addGameObject(decoration);
        }
    }

    private void handleShooting() {
        if (player == null || playerDead) return;

        boolean isPressed = inputManager.isMouseButtonPressed(LEFT_MOUSE_BUTTON);
        if (isPressed && !wasLeftMousePressed) {
            TransformComponent transform = player.getComponent(TransformComponent.class);
            if (transform != null) {
                Vector2 startPosition = transform.getPosition();
                Vector2 targetPosition = inputManager.getMousePosition();
                Vector2 direction = targetPosition.subtract(startPosition);
                if (direction.magnitude() > 0.01f) {
                    spawnFireball(startPosition, direction);
                }
            }
        }
        wasLeftMousePressed = isPressed;
    }

    private void spawnFireball(Vector2 startPosition, Vector2 direction) {
        Vector2 normalizedDirection = direction.normalize();

        GameObject fireball = new GameObject("Fireball") {
            @Override
            public void render() {
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform != null) {
                    renderer.drawCircle(transform.getPosition().x, transform.getPosition().y, FIREBALL_RADIUS, 16, 1.0f, 0.4f, 0.0f, 1.0f);
                }
            }
        };

        fireball.addComponent(new TransformComponent(new Vector2(startPosition)));
        PhysicsComponent physics = fireball.addComponent(new PhysicsComponent(0.1f));
        physics.setFriction(1.0f); // No friction
        physics.setVelocity(normalizedDirection.multiply(FIREBALL_SPEED));
        
        addGameObject(fireball);
        fireballs.add(fireball);
    }

    private void handleFireballEnemyCollisions() {
        List<GameObject> enemies = findGameObjectsByName("Enemy");
        if (enemies.isEmpty() || fireballs.isEmpty()) return;

        for (GameObject fireball : new ArrayList<>(fireballs)) {
            if (!fireball.isActive()) continue;
            
            TransformComponent fireballTransform = fireball.getComponent(TransformComponent.class);
            if (fireballTransform == null) continue;
            CollisionUtils.Rect fireballRect = CollisionUtils.circleBounds(fireballTransform.getPosition(), FIREBALL_RADIUS);

            for (GameObject enemy : enemies) {
                if (!enemy.isActive()) continue;

                TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
                CollisionUtils.Rect enemyRect = CollisionUtils.enemyBounds(enemy, enemyTransform);
                if (enemyRect != null && fireballRect.intersects(enemyRect)) {
                    enemy.setActive(false);
                    fireball.setActive(false);
                    score += 10;
                    break; 
                }
            }
        }
    }
    
    private void checkPlayerEnemyCollisions() {
        if (player == null || playerDead) return;

        List<GameObject> enemies = findGameObjectsByName("Enemy");
        if (enemies.isEmpty()) return;

        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        CollisionUtils.Rect playerRect = CollisionUtils.playerBounds(playerTransform.getPosition());

        for (GameObject enemy : enemies) {
            if (!enemy.isActive()) continue;

            TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
            CollisionUtils.Rect enemyRect = CollisionUtils.enemyBounds(enemy, enemyTransform);
            
            if (enemyRect != null && playerRect.intersects(enemyRect)) {
                handlePlayerEnemyCollision(enemy);
                break; 
            }
        }
    }

    private void handlePlayerEnemyCollision(GameObject enemy) {
        if (playerDead) return;

        enemy.setActive(false);
        playerHealth--;
        score++;

        if (playerHealth <= 0) {
            handlePlayerDeath();
        }
    }

    private void handlePlayerDeath() {
        playerDead = true;
        if (player != null) {
            player.setActive(false);
        }
        awaitingRestartConfirmation = true;
    }

    private void cleanupInactiveFireballs() {
        fireballs.removeIf(fb -> !fb.isActive() || isOutOfBounds(fb.getComponent(TransformComponent.class).getPosition()));
    }

    private boolean isOutOfBounds(Vector2 position) {
        return position.x < 0 || position.x > renderer.getWidth() || position.y < 0 || position.y > renderer.getHeight();
    }

    private void resetGame() {
        // Clear existing objects
        for(GameObject obj : new ArrayList<>(getGameObjects())) {
            removeGameObject(obj);
        }
        fireballs.clear();

        // Reset state
        this.score = 0;
        this.elapsedTime = 0f;
        this.spawnTimer = 0f;
        this.wasLeftMousePressed = false;
        this.maxHealth = 5;
        this.playerHealth = maxHealth;
        this.playerDead = false;
        this.awaitingRestartConfirmation = false;
        
        // Create fresh game objects
        createPlayer();
        createEnemies(20);
        createDecorations(5);
    }
    
    private void drawPlayerHealthBar(Vector2 basePosition) {
        if (maxHealth <= 0) return;
        float barWidth = 50f;
        float barHeight = 6f;
        float x = basePosition.x - barWidth / 2f;
        float y = basePosition.y - CollisionUtils.PLAYER_TOP_OFFSET - 8f;

        renderer.drawRect(x, y, barWidth, barHeight, 0.2f, 0.0f, 0.0f, 0.7f);
        if (playerHealth > 0) {
            float ratio = (float) playerHealth / maxHealth;
            renderer.drawRect(x, y, barWidth * ratio, barHeight, 0.0f, 0.9f, 0.1f, 0.9f);
        }
    }
}