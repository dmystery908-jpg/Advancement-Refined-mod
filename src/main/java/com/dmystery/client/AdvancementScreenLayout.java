package com.dmystery.client;

public class AdvancementScreenLayout {
    private static int windowWidth = 252;
    private static int windowHeight = 140;
    private static float zoom = 0.65f;

    public static void update(int screenWidth, int screenHeight, boolean hasBottomTabs) {
        // Full screen layout with clean margins leaving room for top progress bar, tabs, and optional bottom tabs
        int targetWidth = Math.max(252, screenWidth - 24);
        int bottomReserved = hasBottomTabs ? 34 : 8;
        int targetHeight = Math.max(140, screenHeight - 48 - bottomReserved);
        windowWidth = targetWidth;
        windowHeight = targetHeight;
    }

    public static int getWindowWidth() {
        return windowWidth;
    }

    public static int getWindowHeight() {
        return windowHeight;
    }

    public static int getInsideWidth() {
        return windowWidth - 18;
    }

    public static int getInsideHeight() {
        return windowHeight - 27;
    }

    public static float getZoom() {
        return AdvancementProgressConfig.getInstance().treeZoom;
    }
}
