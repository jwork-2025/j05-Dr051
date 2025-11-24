package com.gameengine.math;

import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;

/**
 * Utility helpers for axis-aligned bounding box calculations used by the game example.
 */
public final class CollisionUtils {
    public static final float PLAYER_HALF_WIDTH = 13f;
    public static final float PLAYER_TOP_OFFSET = 22f;
    public static final float PLAYER_BOTTOM_OFFSET = 10f;

    private CollisionUtils() {
        // Utility class
    }

    public static Rect playerBounds(Vector2 centerPosition) {
        if (centerPosition == null) {
            return null;
        }
        float left = centerPosition.x - PLAYER_HALF_WIDTH;
        float right = centerPosition.x + PLAYER_HALF_WIDTH;
        float top = centerPosition.y - PLAYER_TOP_OFFSET;
        float bottom = centerPosition.y + PLAYER_BOTTOM_OFFSET;
        return new Rect(left, top, right, bottom);
    }

    public static Rect enemyBounds(GameObject enemy, TransformComponent transform) {
        if (enemy == null || transform == null) {
            return null;
        }
        Vector2 position = transform.getPosition();
        float width = 20f;
        float height = 20f;

        RenderComponent renderComponent = enemy.getComponent(RenderComponent.class);
        if (renderComponent != null) {
            Vector2 size = renderComponent.getSize();
            width = size.x;
            height = size.y;
        }

        float left = position.x;
        float top = position.y;
        float right = left + width;
        float bottom = top + height;
        return new Rect(left, top, right, bottom);
    }

    public static Rect circleBounds(Vector2 center, float radius) {
        if (center == null) {
            return null;
        }
        float left = center.x - radius;
        float top = center.y - radius;
        float right = center.x + radius;
        float bottom = center.y + radius;
        return new Rect(left, top, right, bottom);
    }

    public static final class Rect {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Rect(float left, float top, float right, float bottom) {
            this.left = Math.min(left, right);
            this.top = Math.min(top, bottom);
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
        }

        public boolean intersects(Rect other) {
            if (other == null) {
                return false;
            }
            return this.left < other.right
                && this.right > other.left
                && this.top < other.bottom
                && this.bottom > other.top;
        }
    }
}
