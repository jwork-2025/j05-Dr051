package com.gameengine.core;

import java.util.*;

public class GameObject {
    private static long nextId = 0;
    protected final long id;
    protected boolean active;
    protected String name;
    protected final List<Component<?>> components;
    
    public GameObject() {
        this.id = nextId++;
        this.active = true;
        this.name = "GameObject";
        this.components = new ArrayList<>();
    }
    
    public GameObject(String name) {
        this();
        this.name = name;
    }
    
    public long getId() {
        return id;
    }
    
    public void update(float deltaTime) {
        updateComponents(deltaTime);
    }
    
    public void render() {
        renderComponents();
    }
    
    public void initialize() {
    }
    
    public void destroy() {
        this.active = false;
        for (Component<?> component : components) {
            component.destroy();
        }
        components.clear();
    }
    
    public <T extends Component<T>> T addComponent(T component) {
        component.setOwner(this);
        components.add(component);
        component.initialize();
        return component;
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Component<T>> T getComponent(Class<T> componentType) {
        for (Component<?> component : components) {
            if (componentType.isInstance(component)) {
                return (T) component;
            }
        }
        return null;
    }
    
    public <T extends Component<T>> boolean hasComponent(Class<T> componentType) {
        for (Component<?> component : components) {
            if (componentType.isInstance(component)) {
                return true;
            }
        }
        return false;
    }
    
    public void updateComponents(float deltaTime) {
        for (Component<?> component : components) {
            if (component.isEnabled()) {
                component.update(deltaTime);
            }
        }
    }
    
    public void renderComponents() {
        for (Component<?> component : components) {
            if (component.isEnabled()) {
                component.render();
            }
        }
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
