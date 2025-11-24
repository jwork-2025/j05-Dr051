package com.gameengine.core;

import com.gameengine.graphics.IRenderer;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.graphics.RendererFactory;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;


public class GameEngine {
    private IRenderer renderer;
    private InputManager inputManager;
    private Scene currentScene;
    private PhysicsSystem physicsSystem;
    private boolean running;
    private float targetFPS;
    private float deltaTime;
    private long lastTime;
    @SuppressWarnings("unused")
    private String title;
    // 新录制服务（可选）
    private com.gameengine.recording.RecordingService recordingService;
    
    public GameEngine(int width, int height, String title) {
        this(width, height, title, RenderBackend.GPU);
    }
    
    public GameEngine(int width, int height, String title, RenderBackend backend) {
        this.title = title;
        this.renderer = RendererFactory.createRenderer(backend, width, height, title);
        this.inputManager = InputManager.getInstance();
        this.running = false;
        this.targetFPS = 60.0f;
        this.deltaTime = 0.0f;
        this.lastTime = System.nanoTime();
        
    }
    
    public boolean initialize() {
        return true;
    }
    
    public void run() {
        if (!initialize()) {
            System.err.println("游戏引擎初始化失败");
            return;
        }
        
        running = true;
        
        if (currentScene != null) {
            currentScene.initialize();
            if (currentScene.getName().equals("MainMenu")) {
                physicsSystem = null;
            } else {
                physicsSystem = new PhysicsSystem(currentScene, renderer.getWidth(), renderer.getHeight());
            }
            
        }
        
        long lastFrameTime = System.nanoTime();
        long frameTimeNanos = (long)(1_000_000_000.0 / targetFPS);
        
        while (running) {
            long currentTime = System.nanoTime();
            
            if (currentTime - lastFrameTime >= frameTimeNanos) {
                update();
                if (running) {
                    render();
                }
                lastFrameTime = currentTime;
            }
            
            // Only poll events if still running, to avoid polling a closing window if update() set running=false
            if (running) {
                renderer.pollEvents();
            }
            
            // Check window close request
            if (running && renderer.shouldClose()) {
                running = false;
            }
            
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                break;
            }
        }
        
        // Cleanup only after the loop finishes
        cleanup();
    }
    
    private void update() {
        long currentTime = System.nanoTime();
        deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
        lastTime = currentTime;
        
        // renderer.pollEvents() moved to main loop
        
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
        
        if (physicsSystem != null) {
            physicsSystem.update(deltaTime);
        }
        
        if (recordingService != null && recordingService.isRecording()) {
            recordingService.update(deltaTime, currentScene, inputManager);
        }
        
        inputManager.update();
        
        if (inputManager.isKeyPressed(27)) {
            // If we are in MainMenu, ESC exits the application.
            // If we are in GameScene or ReplayScene, the scene itself should handle ESC (e.g., return to menu).
            if (currentScene != null && "MainMenu".equals(currentScene.getName())) {
                running = false;
                // Cleanup will happen after loop
            }
            // Otherwise, let the scene handle it in its update() method.
        }
        
        // renderer.shouldClose() check moved to main loop
    }
    
    private void render() {
        if (renderer == null) return;
        
        renderer.beginFrame();
        
        if (currentScene != null) {
            currentScene.render();
        }
        
        renderer.endFrame();
    }
    
    public void setScene(Scene scene) {
        if (currentScene != null) {
            if (physicsSystem != null) {
                physicsSystem.cleanup();
                physicsSystem = null;
            }
            currentScene.clear();
        }
        this.currentScene = scene;
        if (scene != null) {
            if (running) {
                scene.initialize();
                if (!scene.getName().equals("MainMenu") && !scene.getName().equals("Replay")) {
                    physicsSystem = new PhysicsSystem(scene, renderer.getWidth(), renderer.getHeight());
                }
            }
        }
    }
    
    public Scene getCurrentScene() {
        return currentScene;
    }
    
    public void stop() {
        running = false;
    }
    
    public void cleanup() {
        if (recordingService != null && recordingService.isRecording()) {
            try { recordingService.stop(); } catch (Exception ignored) {}
        }
        if (physicsSystem != null) {
            physicsSystem.cleanup();
        }
        if (currentScene != null) {
            currentScene.clear();
        }
        renderer.cleanup();
    }

    private String currentRecordingPath;

    // 可选：外部启用录制（按需调用）
    public void enableRecording(com.gameengine.recording.RecordingService service) {
        this.recordingService = service;
        try {
            if (service != null && currentScene != null) {
                // Assuming service has a way to get path or we rely on caller passing it?
                // Service.start() was called by caller? No, enableRecording calls start.
                // But start() signature is start(Scene, w, h).
                // Service already has config with path.
                // I can't get path from service easily.
                // Let's assume enableRecording is passed the path too?
                // Existing calls: engine.enableRecording(svc).
                // I will overload or change logic.
                
                service.start(currentScene, renderer.getWidth(), renderer.getHeight());
            }
        } catch (Exception e) {
            System.err.println("录制启动失败: " + e.getMessage());
        }
    }
    
    public void setRecordingPath(String path) {
        this.currentRecordingPath = path;
    }

    public void discardRecording() {
        if (recordingService != null && recordingService.isRecording()) {
            try { recordingService.stop(); } catch (Exception ignored) {}
        }
        recordingService = null;
        if (currentRecordingPath != null) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(currentRecordingPath));
                System.out.println("Recording discarded: " + currentRecordingPath);
            } catch (Exception e) {
                System.err.println("Failed to delete recording: " + e.getMessage());
            }
            currentRecordingPath = null;
        }
    }

    public void disableRecording() {
        if (recordingService != null && recordingService.isRecording()) {
            try { recordingService.stop(); } catch (Exception ignored) {}
        }
        recordingService = null;
        // Keep file
    }
    
    
    
    public IRenderer getRenderer() {
        return renderer;
    }
    
    public InputManager getInputManager() {
        return inputManager;
    }
    
    public float getDeltaTime() {
        return deltaTime;
    }
    
    public void setTargetFPS(float fps) {
        this.targetFPS = fps;
    }
    
    public float getTargetFPS() {
        return targetFPS;
    }
    
    public boolean isRunning() {
        return running;
    }
}
