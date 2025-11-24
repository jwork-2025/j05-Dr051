package com.gameengine.example;

import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.io.File;
import java.util.*;

public class ReplayScene extends Scene {
    private enum State {
        SUB_MENU,
        FILE_SELECT,
        PLAYING
    }

    private final GameEngine engine;
    private String recordingPath;
    private IRenderer renderer;
    private InputManager input;
    
    private State currentState;
    
    // Sub-menu
    private int menuIndex = 0;
    
    // File select
    private List<File> recordingFiles;
    private int fileIndex = 0;
    
    // Playing
    private float time;
    private final List<Keyframe> keyframes = new ArrayList<>();
    private final Map<Long, GameObject> activeObjects = new HashMap<>();

    private static class Keyframe {
        static class EntityInfo {
            long uid;
            Vector2 pos;
            String rt;
            float w, h;
            float r=0.9f,g=0.9f,b=0.2f,a=1.0f;
            String id;
        }
        double t;
        java.util.List<EntityInfo> entities = new ArrayList<>();
    }

    public ReplayScene(GameEngine engine, String path) {
        super("Replay");
        this.engine = engine;
        this.recordingPath = path;
        // If path provided, play immediately; otherwise sub-menu
        this.currentState = (path != null) ? State.PLAYING : State.SUB_MENU;
    }

    @Override
    public void initialize() {
        super.initialize();
        System.out.println("ReplayScene initializing...");
        this.renderer = engine.getRenderer();
        this.input = engine.getInputManager();
        
        this.time = 0f;
        this.keyframes.clear();
        this.activeObjects.clear();
        
        if (recordingPath != null) {
            System.out.println("Loading recording: " + recordingPath);
            loadRecording(recordingPath);
            this.currentState = State.PLAYING;
        } else {
            System.out.println("Entering SUB_MENU");
            this.currentState = State.SUB_MENU;
            this.menuIndex = 0;
        }
    }

    @Override
    public void update(float deltaTime) {
        if (input == null) return;

        // Global ESC: Return to Main Menu
        if (input.isKeyJustPressed(27) || input.isKeyJustPressed(256)) {
            System.out.println("ESC pressed, returning to MainMenu");
            engine.setScene(new MenuScene(engine, "MainMenu"));
            return;
        }

        super.update(deltaTime);

        try {
            switch (currentState) {
                case SUB_MENU:
                    updateSubMenu();
                    break;
                case FILE_SELECT:
                    updateFileSelect();
                    break;
                case PLAYING:
                    updatePlaying(deltaTime);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSubMenu() {
        // UP
        if (input.isKeyJustPressed(38) || input.isKeyJustPressed(265)) {
            menuIndex = (menuIndex - 1 + 2) % 2;
        }
        // DOWN
        if (input.isKeyJustPressed(40) || input.isKeyJustPressed(264)) {
            menuIndex = (menuIndex + 1) % 2;
        }
        // ENTER/SPACE
        if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32) || input.isKeyJustPressed(257) || input.isKeyJustPressed(335)) {
            executeSubMenuAction(menuIndex);
        }
        
        // Mouse
        if (input.isMouseButtonJustPressed(0)) {
            Vector2 mouse = input.getMousePosition();
            float cx = renderer.getWidth() / 2.0f;
            float cy = renderer.getHeight() / 2.0f;
            
            // SAVE button (index 0): cy - 40
            if (isMouseInButton(mouse, cx, cy - 40)) {
                executeSubMenuAction(0);
            }
            // RETURN button (index 1): cy + 40
            else if (isMouseInButton(mouse, cx, cy + 40)) {
                executeSubMenuAction(1);
            }
        }
    }
    
    private void executeSubMenuAction(int index) {
        if (index == 0) { // SAVE
            ensureFilesListed();
            currentState = State.FILE_SELECT;
            fileIndex = 0;
        } else { // RETURN
            engine.setScene(new MenuScene(engine, "MainMenu"));
        }
    }

    private void updateFileSelect() {
        ensureFilesListed();
        if (recordingFiles.isEmpty()) return;

        // UP
        if (input.isKeyJustPressed(38) || input.isKeyJustPressed(265)) {
            fileIndex = (fileIndex - 1 + recordingFiles.size()) % recordingFiles.size();
        }
        // DOWN
        if (input.isKeyJustPressed(40) || input.isKeyJustPressed(264)) {
            fileIndex = (fileIndex + 1) % recordingFiles.size();
        }
        // ENTER/SPACE
        if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32) || input.isKeyJustPressed(257) || input.isKeyJustPressed(335)) {
            playFile(fileIndex);
        }
        
        // Mouse
        if (input.isMouseButtonJustPressed(0)) {
            Vector2 mouse = input.getMousePosition();
            float startY = 100f;
            float itemH = 30f;
            for (int i = 0; i < recordingFiles.size(); i++) {
                float y = startY + i * itemH;
                if (mouse.y >= y && mouse.y < y + itemH) {
                    playFile(i);
                    break;
                }
            }
        }
    }
    
    private void playFile(int index) {
        if (index >= 0 && index < recordingFiles.size()) {
            String path = recordingFiles.get(index).getAbsolutePath();
            this.recordingPath = path;
            this.currentState = State.PLAYING;
            this.activeObjects.clear();
            super.clear(); // Clear Scene objects
            loadRecording(path);
        }
    }

    private void updatePlaying(float deltaTime) {
        if (keyframes.isEmpty()) return;
        
        time += deltaTime;
        double lastT = keyframes.get(keyframes.size() - 1).t;
        if (time > lastT) time = (float)lastT;

        Keyframe a = keyframes.get(0);
        Keyframe b = keyframes.get(keyframes.size() - 1);
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);
            if (time >= k1.t && time <= k2.t) { a = k1; b = k2; break; }
        }
        double span = Math.max(1e-6, b.t - a.t);
        double u = Math.min(1.0, Math.max(0.0, (time - a.t) / span));
        updateInterpolatedPositions(a, b, (float)u);
    }

    @Override
    public void render() {
        try {
            // Background: Dark Blue/Green mix to be distinct
            renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.05f, 0.2f, 0.3f, 1.0f);

            switch (currentState) {
                case SUB_MENU:
                    renderSubMenu();
                    break;
                case FILE_SELECT:
                    renderFileSelect();
                    break;
                case PLAYING:
                    super.render(); // Draw interpolated objects
                    renderer.drawText(10, 10, "PLAYING (ESC to Exit)", 1f, 1f, 0f, 1f);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            renderer.drawText(10, 50, "RENDER ERROR", 1f, 0f, 0f, 1f);
        }
    }

    private void renderSubMenu() {
        float w = renderer.getWidth();
        float h = renderer.getHeight();
        float cx = w / 2.0f;
        float cy = h / 2.0f;

        String title = "REPLAY OPTIONS";
        renderer.drawText(cx - (title.length()*16f)/2f, cy - 120, title, 1f, 1f, 1f, 1f);

        // Button 0: SAVE
        drawCenteredButton("SAVE", cx, cy - 40, menuIndex == 0);
        
        // Button 1: RETURN
        drawCenteredButton("RETURN", cx, cy + 40, menuIndex == 1);
        
        String hint = "UP/DOWN Select, ENTER Confirm";
        renderer.drawText(cx - (hint.length()*12f)/2f, h - 50, hint, 0.6f, 0.6f, 0.6f, 1f);
    }
    
    private void drawCenteredButton(String text, float cx, float cy, boolean selected) {
        float w = 300f;
        float h = 50f;
        float x = cx - w/2;
        float y = cy - h/2;
        
        if (selected) {
            renderer.drawRect(x, y, w, h, 0.4f, 0.6f, 0.8f, 0.9f);
            renderer.drawText(x + w/2 - (text.length()*16f)/2 + 30, y + 15, text, 1f, 1f, 0f, 1f); // Yellow text
        } else {
            renderer.drawRect(x, y, w, h, 0.2f, 0.2f, 0.3f, 0.5f);
            renderer.drawText(x + w/2 - (text.length()*16f)/2 + 30, y + 15, text, 0.8f, 0.8f, 0.8f, 1f); // Grey text
        }
    }
    
    private boolean isMouseInButton(Vector2 mouse, float cx, float cy) {
        return mouse.x >= cx - 150 && mouse.x <= cx + 150 &&
               mouse.y >= cy - 25 && mouse.y <= cy + 25;
    }

    private void renderFileSelect() {
        int w = renderer.getWidth();
        int h = renderer.getHeight();
        
        String title = "SELECT RECORDING (ESC Back)";
        renderer.drawText(20, 20, title, 1f, 1f, 1f, 1f);

        if (recordingFiles == null || recordingFiles.isEmpty()) {
            renderer.drawText(w/2f - 100, h/2f, "NO FILES FOUND", 1f, 0.5f, 0.5f, 1f);
            return;
        }

        float startY = 100f;
        float itemH = 30f;
        
        for (int i = 0; i < recordingFiles.size(); i++) {
            float y = startY + i * itemH;
            if (y > h - 30) break;
            
            String name = recordingFiles.get(i).getName();
            if (i == fileIndex) {
                renderer.drawRect(40, y, w - 80, itemH, 0.3f, 0.5f, 0.7f, 0.8f);
                renderer.drawText(50, y + 5, "> " + name, 1f, 1f, 0f, 1f);
            } else {
                renderer.drawText(50, y + 5, "  " + name, 0.8f, 0.8f, 0.8f, 1f);
            }
        }
    }

    private void ensureFilesListed() {
        if (recordingFiles != null) return;
        try {
            File dir = new File("recordings");
            if (dir.exists()) {
                File[] files = dir.listFiles((d, n) -> n.endsWith(".jsonl") || n.endsWith(".json"));
                if (files != null) {
                    Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
                    recordingFiles = new ArrayList<>(Arrays.asList(files));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (recordingFiles == null) recordingFiles = new ArrayList<>();
    }

    private void loadRecording(String path) {
        keyframes.clear();
        com.gameengine.recording.RecordingStorage storage = new com.gameengine.recording.FileRecordingStorage();
        try {
            for (String line : storage.readLines(path)) {
                if (!line.contains("\"type\":\"keyframe\"")) continue;
                Keyframe kf = new Keyframe();
                // Simplified JSON parsing for robustness
                kf.t = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "t"));
                
                int idx = line.indexOf("\"entities\":[");
                if (idx >= 0) {
                    int bracket = line.indexOf('[', idx);
                    String arr = bracket >= 0 ? com.gameengine.recording.RecordingJson.extractArray(line, bracket) : "";
                    String[] parts = com.gameengine.recording.RecordingJson.splitTopLevel(arr);
                    for (String p : parts) {
                        Keyframe.EntityInfo ei = new Keyframe.EntityInfo();
                        ei.id = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "id"));
                        String uid = com.gameengine.recording.RecordingJson.field(p, "uid");
                        ei.uid = (uid != null) ? Long.parseLong(uid) : -1L;
                        
                        ei.pos = new Vector2(
                            (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "x")),
                            (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "y"))
                        );
                        
                        ei.rt = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "rt"));
                        ei.w = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "w"));
                        ei.h = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "h"));
                        
                        // Color parsing
                        String c = com.gameengine.recording.RecordingJson.field(p, "color");
                        if (c != null && c.startsWith("[")) {
                            String inner = c.substring(1, c.indexOf(']'));
                            String[] rgba = inner.split(",");
                            if (rgba.length >= 3) {
                                try {
                                    ei.r = Float.parseFloat(rgba[0]);
                                    ei.g = Float.parseFloat(rgba[1]);
                                    ei.b = Float.parseFloat(rgba[2]);
                                    if (rgba.length > 3) ei.a = Float.parseFloat(rgba[3]);
                                } catch (Exception ignored) {}
                            }
                        }
                        kf.entities.add(ei);
                    }
                }
                keyframes.add(kf);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        keyframes.sort(Comparator.comparingDouble(k -> k.t));
    }

    private void updateInterpolatedPositions(Keyframe a, Keyframe b, float u) {
        Set<Long> currentUIDs = new HashSet<>();
        Map<Long, Keyframe.EntityInfo> bMap = new HashMap<>();
        for (Keyframe.EntityInfo ei : b.entities) bMap.put(ei.uid, ei);

        for (Keyframe.EntityInfo eiA : a.entities) {
            currentUIDs.add(eiA.uid);
            GameObject obj = activeObjects.computeIfAbsent(eiA.uid, k -> {
                GameObject newObj = buildObjectFromEntity(eiA);
                addGameObject(newObj);
                return newObj;
            });
            
            Vector2 target = eiA.pos;
            if (bMap.containsKey(eiA.uid)) {
                Keyframe.EntityInfo eiB = bMap.get(eiA.uid);
                target = new Vector2(
                    (float)((1-u)*eiA.pos.x + u*eiB.pos.x),
                    (float)((1-u)*eiA.pos.y + u*eiB.pos.y)
                );
            }
            
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc != null) tc.setPosition(target);
            obj.setActive(true);
        }
        
        activeObjects.entrySet().removeIf(e -> {
            if (!currentUIDs.contains(e.getKey())) {
                e.getValue().setActive(false);
                return true;
            }
            return false;
        });
    }

        private GameObject buildObjectFromEntity(Keyframe.EntityInfo ei) {
            GameObject obj;
            if ("Player".equalsIgnoreCase(ei.id)) {
                obj = com.gameengine.example.EntityFactory.createPlayerVisual(renderer);
            } else if ("AIPlayer".equalsIgnoreCase(ei.id)) {
                float w2 = (ei.w > 0 ? ei.w : 20);
                float h2 = (ei.h > 0 ? ei.h : 20);
                obj = com.gameengine.example.EntityFactory.createAIVisual(renderer, w2, h2, ei.r, ei.g, ei.b, ei.a);
            } else if ("Enemy".equalsIgnoreCase(ei.id)) {
                obj = com.gameengine.example.EntityFactory.createEnemyVisual(renderer);
            } else if ("Fireball".equalsIgnoreCase(ei.id)) {
                obj = com.gameengine.example.EntityFactory.createFireballVisual(renderer);
            } else if ("Decoration".equalsIgnoreCase(ei.id)) {
                obj = com.gameengine.example.EntityFactory.createDecorationVisual(renderer);
            } else {
                if ("CIRCLE".equals(ei.rt)) {
                    GameObject tmp = new GameObject(ei.id == null ? ("Obj#"+ei.uid) : ei.id);
                    tmp.addComponent(new TransformComponent(new Vector2(0,0)));
                    com.gameengine.components.RenderComponent rc = tmp.addComponent(
                        new com.gameengine.components.RenderComponent(
                            com.gameengine.components.RenderComponent.RenderType.CIRCLE,
                            new Vector2(Math.max(1, ei.w), Math.max(1, ei.h)),
                            new com.gameengine.components.RenderComponent.Color(ei.r, ei.g, ei.b, ei.a)
                        )
                    );
                    rc.setRenderer(renderer);
                    obj = tmp;
                } else {
                    obj = com.gameengine.example.EntityFactory.createAIVisual(renderer, Math.max(1, ei.w>0?ei.w:10), Math.max(1, ei.h>0?ei.h:10), ei.r, ei.g, ei.b, ei.a);
                }
                obj.setName(ei.id == null ? ("Obj#"+ei.uid) : ei.id);
            }
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc == null) obj.addComponent(new TransformComponent(new Vector2(ei.pos)));
            else tc.setPosition(new Vector2(ei.pos));
            return obj;
        }}