package ru.mesozoa.sim.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import ru.mesozoa.sim.MesozoaVisualApp;
import ru.mesozoa.sim.simulation.GameSimulation;

import static ru.mesozoa.sim.config.GraphicsConfig.MAX_ZOOM;
import static ru.mesozoa.sim.config.GraphicsConfig.MIN_ZOOM;

public class InputHandler {

    private final GameSimulation simulation;
    private final MesozoaVisualApp mesozoaVisualApp;

    public InputHandler(GameSimulation gameSimulation, MesozoaVisualApp mesozoaVisualApp) {
        this.simulation = gameSimulation;
        this.mesozoaVisualApp = mesozoaVisualApp;
    }

    public void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) mesozoaVisualApp.paused = !mesozoaVisualApp.paused;

        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            if (isCtrlPressed()) {
                simulation.stepRound();
            } else {
                simulation.stepOneTurn();
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) mesozoaVisualApp.restart();
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) mesozoaVisualApp.centerCameraOnBase();

        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) mesozoaVisualApp.showGrid = !mesozoaVisualApp.showGrid;
        if (Gdx.input.isKeyJustPressed(Input.Keys.K) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            mesozoaVisualApp.showSpawnDebug = !mesozoaVisualApp.showSpawnDebug;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.J) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            mesozoaVisualApp.showDebug = !mesozoaVisualApp.showDebug;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.PLUS) || Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)) {
            mesozoaVisualApp.stepDelay = Math.max(0.05f, mesozoaVisualApp.stepDelay * 0.75f);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) {
            mesozoaVisualApp.stepDelay = Math.min(2.0f, mesozoaVisualApp.stepDelay * 1.25f);
        }

        updateCameraFromKeyboard();
        updateCameraFromMouse();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
    }

    private boolean isCtrlPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    public void updateCameraFromKeyboard() {
        float dt = Gdx.graphics.getDeltaTime();
        float cameraSpeedTilesPerSecond = 8f;

        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
            cameraSpeedTilesPerSecond = 16f;
        }

        float delta = cameraSpeedTilesPerSecond * dt;

        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) mesozoaVisualApp.cameraY += delta;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) mesozoaVisualApp.cameraX -= delta;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) mesozoaVisualApp.cameraY -= delta;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mesozoaVisualApp.cameraX += delta;
    }

    public void updateCameraFromMouse() {
        int mouseX = Gdx.input.getX();
        if (mouseX >= mesozoaVisualApp.boardViewportWidth()) return;

        boolean dragPressed =
                Gdx.input.isButtonPressed(Input.Buttons.RIGHT)
                        || Gdx.input.isButtonPressed(Input.Buttons.MIDDLE);

        if (!dragPressed) return;

        mesozoaVisualApp.cameraX -= (float) Gdx.input.getDeltaX() / mesozoaVisualApp.tilePixelSize();
        mesozoaVisualApp.cameraY += (float) Gdx.input.getDeltaY() / mesozoaVisualApp.tilePixelSize();
    }

    public void zoomAtMouse(float factor) {
        float oldZoom = mesozoaVisualApp.zoom;
        float newZoom = clamp(mesozoaVisualApp.zoom * factor, MIN_ZOOM, MAX_ZOOM);

        if (Math.abs(newZoom - oldZoom) < 0.0001f) {
            return;
        }

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();

        float worldXBefore = mesozoaVisualApp.cameraX + (mouseX - mesozoaVisualApp.boardCenterX()) / mesozoaVisualApp.tilePixelSize();
        float worldYBefore = mesozoaVisualApp.cameraY + (mouseY - mesozoaVisualApp.boardCenterY()) / mesozoaVisualApp.tilePixelSize();

        mesozoaVisualApp.zoom = newZoom;

        mesozoaVisualApp.cameraX = worldXBefore - (mouseX - mesozoaVisualApp.boardCenterX()) / mesozoaVisualApp.tilePixelSize();
        mesozoaVisualApp.cameraY = worldYBefore - (mouseY - mesozoaVisualApp.boardCenterY()) / mesozoaVisualApp.tilePixelSize();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
