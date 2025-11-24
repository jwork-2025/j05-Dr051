package com.gameengine.example;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.core.GameObject;
import com.gameengine.core.ParticleSystem;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.CollisionUtils;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameScene extends Scene {
    private final GameEngine engine;
    private IRenderer renderer;
    private Random random;
    private float elapsedTime;
    private float spawnTimer;
    private GameLogic gameLogic;
    private InputManager inputManager;
    private GameObject player;
    private final List<GameObject> fireballs = new ArrayList<>();
    private boolean wasLeftMousePressed;
    private int score;
    private int maxHealth;
    private int playerHealth;
    private boolean playerDead;
    
    private final float FIREBALL_SPEED = 350f;
    private final float FIREBALL_RADIUS = 6f;
    private final int LEFT_MOUSE_BUTTON = 0; // GLFW mouse button 0 is usually Left
    private final float MIN_SPAWN_INTERVAL = 0.1f;
    private final float BASE_SPAWN_INTERVAL = 0.5f;
    
    private boolean awaitingRestartConfirmation = false;
    
    // Optional: Particles
    private List<ParticleSystem> explosionParticles;

    public GameScene(GameEngine engine) {
        super("GameScene");
        this.engine = engine;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.random = new Random();
        this.elapsedTime = 0f;
        this.spawnTimer = 0f;
        this.gameLogic = new GameLogic(this);
        this.gameLogic.setGameEngine(engine);
        this.inputManager = InputManager.getInstance();
        this.wasLeftMousePressed = false;
        this.score = 0;
        this.maxHealth = 15;
        this.playerHealth = maxHealth;
        this.playerDead = false;
        this.gameLogic.setOnPlayerEnemyCollision(this::handlePlayerEnemyCollision);
        this.explosionParticles = new ArrayList<>();

        createPlayer();
        createEnemies(10);
        createDecorations();
    }

    @Override
    public void update(float deltaTime) {
        // Always check for ESC to pause/exit
        if (inputManager.isKeyJustPressed(256)) { // ESC (GLFW 256)
            if (!playerDead) {
                // Exit while playing (Not Game Over) -> Discard recording
                engine.discardRecording();
                engine.setScene(new MenuScene(engine, "MainMenu"));
                return;
            }
            // If playerDead (Game Over), fall through to awaitingRestartConfirmation logic
        }

        if (awaitingRestartConfirmation) {
            // Handle restart input
            if (inputManager.isKeyJustPressed(257) || inputManager.isKeyJustPressed(335)) { // Enter
                // Game Over -> Restart
                // Current recording should be saved (default behavior on stop/disable).
                // We need to ensure the file is closed before starting new one.
                engine.disableRecording(); 
                
                // Restart game logic:
                // We can just switch to a new GameScene which initializes everything fresh.
                // MenuScene usually handles setting up recording.
                // We need to duplicate that logic or route through Menu.
                // Simplest: Route through MenuScene but auto-start?
                // Or just manually set up new recording here.
                
                try {
                    // Start new session
                    String path = "recordings/session_" + System.currentTimeMillis() + ".jsonl";
                    com.gameengine.recording.RecordingConfig cfg = new com.gameengine.recording.RecordingConfig(path);
                    com.gameengine.recording.RecordingService svc = new com.gameengine.recording.RecordingService(cfg);
                    engine.setRecordingPath(path);
                    engine.enableRecording(svc);
                    
                    // Reset Scene
                    resetGame(); // resetGame resets state but keeps scene. 
                    // Actually resetGame() in this class just resets vars.
                    // It does NOT clear the engine state fully if we want "fresh".
                    // But resetGame() logic seems to reset player/enemies.
                    // It sets elapsedTime = 0.
                    // So "resetGame()" is effectively a new game in the same scene instance.
                    // That works.
                    awaitingRestartConfirmation = false;
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
            } else if (inputManager.isKeyJustPressed(256)) { // ESC
                // Game Over -> Return to Menu
                // Save recording (default behavior of disableRecording called by MenuScene init)
                engine.setScene(new MenuScene(engine, "MainMenu"));
            }
            return;
        }

        handleShooting();
        
        // Important: Super update calls physics system!
        super.update(deltaTime);
        
        handleFireballEnemyCollisions();
        cleanupInactiveFireballs();
        elapsedTime += deltaTime;
        spawnTimer += deltaTime;

        gameLogic.handlePlayerInput(deltaTime);
        // Physics is handled by systems in super.update(), but we need collision checks
        gameLogic.checkCollisions();

        float spawnInterval = Math.max(MIN_SPAWN_INTERVAL, BASE_SPAWN_INTERVAL - elapsedTime * 0.05f);
        if (spawnTimer > spawnInterval) {
            createEnemy();
            spawnTimer = 0f;
        }
        
        updateParticles(deltaTime);
    }

    private void updateParticles(float deltaTime) {
        for (int i = explosionParticles.size() - 1; i >= 0; i--) {
            ParticleSystem ps = explosionParticles.get(i);
            ps.update(deltaTime);
            if (ps.getParticleCount() == 0) { // Simple check if done
                explosionParticles.remove(i);
            }
        }
    }

    @Override
    public void render() {
        // Draw background
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.1f, 0.1f, 0.2f, 1.0f);

        // Render all dynamic objects
        super.render();
        
        // Render particles
        for (ParticleSystem ps : explosionParticles) {
            ps.render();
        }

        // UI
        renderer.drawText(20, 30, "Score: " + score, 1.0f, 1.0f, 1.0f, 1.0f);
        renderer.drawText(600, 30, String.format("Time: %.1f s", elapsedTime), 1.0f, 1.0f, 1.0f, 1.0f);
        
        if (awaitingRestartConfirmation) {
            drawGameOverScreen();
        }
    }
    
    private void drawGameOverScreen() {
        float w = renderer.getWidth();
        float h = renderer.getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        
        renderer.drawRect(0, 0, w, h, 0f, 0f, 0f, 0.7f);
        
        String t1 = "GAME OVER";
        renderer.drawText(cx - 60, cy - 50, t1, 1f, 0.2f, 0.2f, 1f);
        
        String t2 = "Final Score: " + score;
        renderer.drawText(cx - 70, cy, t2, 1f, 1f, 1f, 1f);
        
        String t3 = "Press ENTER to Restart";
        renderer.drawText(cx - 100, cy + 50, t3, 0.8f, 0.8f, 0.8f, 1f);
    }

    private void createPlayer() {
        GameObject player = new GameObject("Player") {
            private Vector2 basePosition;

            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform != null) {
                    basePosition = transform.getPosition();
                }
            }

            @Override
            public void render() {
                if (basePosition == null) return;
                // Custom player rendering from j03
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

        player.addComponent(new TransformComponent(new Vector2(400, 300)));
        PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.95f);

        addGameObject(player);
        this.player = player;
    }
    
    private void drawPlayerHealthBar(Vector2 basePosition) {
        if (maxHealth <= 0 || renderer == null) return;

        float barWidth = 50f;
        float barHeight = 6f;
        float x = basePosition.x - barWidth / 2f;
        float y = basePosition.y - CollisionUtils.PLAYER_TOP_OFFSET - 8f;

        renderer.drawRect(x, y, barWidth, barHeight, 0.2f, 0.0f, 0.0f, 0.7f);
        if (playerHealth > 0) {
            float ratio = Math.max(0f, Math.min(1f, playerHealth / (float) maxHealth));
            renderer.drawRect(x, y, barWidth * ratio, barHeight, 0.0f, 0.9f, 0.1f, 0.9f);
        }
    }

    private void createEnemies(int count) {
        for (int i = 0; i < count; i++) {
            createEnemy();
        }
    }

    private void createEnemy() {
        final float chaseSpeed = 10f + random.nextFloat() * 5f; 
        // Using higher speed for PhysicsSystem because it handles velocity differently (units/sec)
        // j03 used 10f + rand*5f with direct position translation in update() OR velocity
        // j03: desiredVelocity = direction.normalize().multiply(chaseSpeed); 
        // BUT in j03 createEnemy, it said "significantly reduced enemy speed".
        // The PhysicsSystem in j05 applies Velocity * deltaTime.
        // If chaseSpeed is 15, then 15 pixels/sec. That's slow but visible.
        
        GameObject enemy = new GameObject("Enemy") {
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                pursuePlayer(deltaTime);
            }

            private void pursuePlayer(float deltaTime) {
                if (player == null || !player.isActive()) {
                    slowDown();
                    return;
                }

                TransformComponent enemyTransform = getComponent(TransformComponent.class);
                TransformComponent playerTransform = player.getComponent(TransformComponent.class);
                if (enemyTransform == null || playerTransform == null) {
                    slowDown();
                    return;
                }

                Vector2 direction = playerTransform.getPosition().subtract(enemyTransform.getPosition());
                if (direction.magnitude() < 1f) {
                    slowDown();
                    return;
                }

                // Speed adjustment for j05 physics
                float effectiveSpeed = chaseSpeed * 5.0f; // Boost it a bit for j05 feel

                Vector2 desiredVelocity = direction.normalize().multiply(effectiveSpeed);
                PhysicsComponent physics = getComponent(PhysicsComponent.class);
                if (physics != null) {
                    physics.setVelocity(desiredVelocity);
                }
            }

            private void slowDown() {
                PhysicsComponent physics = getComponent(PhysicsComponent.class);
                if (physics != null) {
                    physics.setVelocity(physics.getVelocity().multiply(0.8f));
                }
            }
        };

        Vector2 position = new Vector2(
            random.nextFloat() * renderer.getWidth(),
            random.nextFloat() * renderer.getHeight()
        );

        enemy.addComponent(new TransformComponent(position));

        RenderComponent render = enemy.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE,
            new Vector2(20, 20),
            new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)
        ));
        render.setRenderer(renderer);

        PhysicsComponent physics = enemy.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(0, 0);
        physics.setFriction(0.92f);

        addGameObject(enemy);
    }

    private void createDecorations() {
        for (int i = 0; i < 5; i++) {
            createDecoration();
        }
    }

    private void createDecoration() {
        GameObject decoration = new GameObject("Decoration");
        Vector2 position = new Vector2(
            random.nextFloat() * renderer.getWidth(),
            random.nextFloat() * renderer.getHeight()
        );

        decoration.addComponent(new TransformComponent(position));

        RenderComponent render = decoration.addComponent(new RenderComponent(
            RenderComponent.RenderType.CIRCLE,
            new Vector2(5, 5),
            new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)
        ));
        render.setRenderer(renderer);

        addGameObject(decoration);
    }

    private void handleShooting() {
        if (player == null || inputManager == null || playerDead || playerHealth <= 0 || !player.isActive()) {
            return;
        }

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
        if (normalizedDirection.magnitude() == 0f) return;

        GameObject fireball = new GameObject("Fireball") {
            // We'll rely on PhysicsComponent for movement in j05
            
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime); // Updates physics
                
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform == null) return;
                
                // Check bounds manually to deactivate
                if (isOutOfBounds(transform.getPosition(), FIREBALL_RADIUS)) {
                    setActive(false);
                }
            }
        };

        fireball.addComponent(new TransformComponent(new Vector2(startPosition)));
        
        // Fireball visuals
        RenderComponent rc = fireball.addComponent(new RenderComponent(
            RenderComponent.RenderType.CIRCLE,
            new Vector2(FIREBALL_RADIUS, FIREBALL_RADIUS),
            new RenderComponent.Color(1.0f, 0.4f, 0.0f, 1.0f)
        ));
        rc.setRenderer(renderer);

        // Fireball physics
        PhysicsComponent pc = fireball.addComponent(new PhysicsComponent(1.0f));
        pc.setVelocity(normalizedDirection.multiply(FIREBALL_SPEED));
        pc.setFriction(1.0f); // No friction for fireball

        addGameObject(fireball);
        fireballs.add(fireball);
    }

    private void handleFireballEnemyCollisions() {
        if (fireballs.isEmpty()) return;

        // Collect active enemies
        List<GameObject> enemies = new ArrayList<>();
        for (GameObject obj : getGameObjects()) {
            if (obj.isActive() && "Enemy".equals(obj.getName())) {
                enemies.add(obj);
            }
        }
        if (enemies.isEmpty()) return;

        for (GameObject fireball : new ArrayList<>(fireballs)) {
            if (!fireball.isActive()) continue;

            TransformComponent fireballTransform = fireball.getComponent(TransformComponent.class);
            CollisionUtils.Rect fireballRect = CollisionUtils.circleBounds(
                fireballTransform != null ? fireballTransform.getPosition() : null,
                FIREBALL_RADIUS
            );
            if (fireballRect == null) continue;

            for (GameObject enemy : enemies) {
                if (!enemy.isActive()) continue;

                TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
                CollisionUtils.Rect enemyRect = CollisionUtils.enemyBounds(enemy, enemyTransform);
                
                if (enemyRect != null && fireballRect.intersects(enemyRect)) {
                    enemy.setActive(false);
                    fireball.setActive(false);
                    score += 1;
                    spawnExplosion(enemyTransform.getPosition());
                    break;
                }
            }
        }
    }

    private void spawnExplosion(Vector2 pos) {
        ParticleSystem.Config cfg = new ParticleSystem.Config();
        cfg.initialCount = 0;
        cfg.spawnRate = 9999f; // burst
        cfg.burstSpeedMin = 100f;
        cfg.burstSpeedMax = 300f;
        cfg.burstLifeMin = 0.2f;
        cfg.burstLifeMax = 0.5f;
        cfg.burstSizeMin = 5f;
        cfg.burstSizeMax = 15f;
        cfg.burstR = 1.0f;
        cfg.burstGMin = 0.4f;
        cfg.burstGMax = 0.6f;
        cfg.burstB = 0.0f;
        
        ParticleSystem ps = new ParticleSystem(renderer, pos, cfg);
        ps.burst(20);
        explosionParticles.add(ps);
    }

    private void cleanupInactiveFireballs() {
        fireballs.removeIf(fireball -> !fireball.isActive());
    }

    private boolean isOutOfBounds(Vector2 position, float radius) {
        float minX = radius;
        float minY = radius;
        float maxX = renderer.getWidth() - radius;
        float maxY = renderer.getHeight() - radius;
        return position.x <= minX || position.x >= maxX || position.y <= minY || position.y >= maxY;
    }

    private void resetGame() {
        score = 0;
        elapsedTime = 0f;
        spawnTimer = 0f;
        wasLeftMousePressed = false;
        playerDead = false;
        playerHealth = maxHealth;
        gameLogic.setGameOver(false);

        if (player != null) {
            player.setActive(true);
            TransformComponent transform = player.getComponent(TransformComponent.class);
            if (transform != null) {
                transform.setPosition(new Vector2(400, 300));
            }
            PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
            if (physics != null) {
                physics.setVelocity(0, 0);
            }
        }

        for (GameObject obj : getGameObjects()) {
            if ("Enemy".equals(obj.getName())) {
                obj.setActive(false);
            }
        }

        for (GameObject fireball : fireballs) {
            fireball.setActive(false);
        }
        fireballs.clear();
        explosionParticles.clear();

        createEnemies(3);
    }

    private void handlePlayerEnemyCollision(GameObject enemy) {
        if (enemy != null) {
            enemy.setActive(false);
            spawnExplosion(enemy.getComponent(TransformComponent.class).getPosition());
        }

        if (playerDead) return;

        score += 1;
        playerHealth = Math.max(0, playerHealth - 1);

        if (playerHealth <= 0) {
            handlePlayerDeath();
        }
    }

    private void handlePlayerDeath() {
        playerDead = true;
        gameLogic.setGameOver(true);
        if (player != null) {
            player.setActive(false);
            spawnExplosion(player.getComponent(TransformComponent.class).getPosition());
        }
        promptRestart();
    }

    private void promptRestart() {
        awaitingRestartConfirmation = true;
    }
    
    @Override
    public void clear() {
        if (gameLogic != null) {
            gameLogic.cleanup();
        }
        explosionParticles.clear();
        super.clear();
    }
}