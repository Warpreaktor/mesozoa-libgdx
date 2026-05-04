package ru.mesozoa.sim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import ru.mesozoa.sim.config.InventoryConfig;
import ru.mesozoa.sim.config.GameMechanicConfig;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.input.InputHandler;
import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.simulation.GameSimulation;
import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.tile.BaseTile;
import ru.mesozoa.sim.tile.Tile;
import ru.mesozoa.sim.view.AssetCatalog;
import ru.mesozoa.sim.view.BoardOccupancy;
import ru.mesozoa.sim.view.BoardPiece;
import ru.mesozoa.sim.view.Palette;
import ru.mesozoa.sim.view.RussianFontFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ru.mesozoa.sim.config.GraphicsConfig.BASE_ZOOM;
import static ru.mesozoa.sim.config.GraphicsConfig.MOUSE_WHEEL_ZOOM_STEP;

/**
 * Класс отвечает только за визуализацию игры
 */
public final class MesozoaVisualApp extends ApplicationAdapter {

    public static final long RANDOM_SEED = Long.MIN_VALUE;

    private static final int HUD_WIDTH = 390;

    private static final int TILE_SIZE = 192;

    private static final float TRANSITION_STRIP = 10f;
    private static final float TRANSITION_INSET = 12f;
    private static final float CORNER_ARM_RATIO = 0.20f;
    private static final float HATCH_SPACING = 4.5f;
    private static final float HATCH_WIDTH = 1.8f;

    private static final int PIECE_GRID_SIZE = 4;

    private final long seed;
    public float stepDelay;
    private GameSimulation simulation;

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private AssetCatalog assets;

    public boolean paused = true;
    public boolean showGrid = true;
    public boolean showSpawnDebug = true;
    public boolean showDebug = true;
    private float timer = 0f;

    public float cameraX = 0f;
    public float cameraY = 0f;
    public float zoom = BASE_ZOOM;

    private int restartCounter = 0;
    private long currentSeed = 0L;

    private InputHandler inputHandler;

    public MesozoaVisualApp(long seed, float stepDelay) {
        this.seed = seed;
        this.stepDelay = stepDelay;
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = RussianFontFactory.create(16);
        assets = new AssetCatalog();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (Gdx.input.getX() >= boardViewportWidth()) {
                    return false;
                }

                float factor = (float) Math.pow(MOUSE_WHEEL_ZOOM_STEP, -amountY);
                inputHandler.zoomAtMouse(factor);
                return true;
            }
        });

        restart();
    }

    public void restart() {
        GameConfig config = new GameConfig();
        InventoryConfig inventoryConfig = new InventoryConfig();
        GameMechanicConfig gameMechanicConfig = new GameMechanicConfig();
        currentSeed = nextSimulationSeed();
        simulation = new GameSimulation(config, inventoryConfig, gameMechanicConfig, currentSeed);
        inputHandler = new InputHandler(simulation, this);
        paused = true;
        timer = 0f;
        zoom = BASE_ZOOM;
        centerCameraOnBase();
    }

    private long nextSimulationSeed() {
        if (seed != RANDOM_SEED) {
            return seed;
        }

        restartCounter++;
        long mixedTime = System.nanoTime() ^ System.currentTimeMillis();
        return mixedTime ^ ((long) restartCounter << 32);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || shapes == null || batch == null) {
            return;
        }

        Gdx.gl.glViewport(0, 0, width, height);
        Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, width, height);
        shapes.setProjectionMatrix(projection);
        batch.setProjectionMatrix(projection);
    }

    public void centerCameraOnBase() {
        cameraX = simulation.map.base.x;
        cameraY = simulation.map.base.y;
    }

    @Override
    public void render() {
        inputHandler.handleInput();
        updateSimulation();
        drawFrame();
    }

    private void updateSimulation() {
        if (!paused && !simulation.gameOver) {
            timer += Gdx.graphics.getDeltaTime();
            if (timer >= stepDelay) {
                timer = 0f;
                simulation.stepOneTurn();
            }
        }
    }

    private void drawFrame() {
        Gdx.gl.glClearColor(0.06f, 0.07f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawBoardBackground();
        drawTiles();
        drawPlacementFrontier();
        drawTraps();
        drawPieces();
        drawHud();
    }

    public float tilePixelSize() {
        return TILE_SIZE * zoom;
    }

    private float pixelScale() {
        return tilePixelSize() / TILE_SIZE;
    }

    private float zoomPercent() {
        return zoom / BASE_ZOOM * 100f;
    }

    public int boardViewportWidth() {
        return Math.max(100, Gdx.graphics.getWidth() - HUD_WIDTH);
    }

    private int boardViewportHeight() {
        return Gdx.graphics.getHeight();
    }

    public float boardCenterX() {
        return boardViewportWidth() / 2f;
    }

    public float boardCenterY() {
        return boardViewportHeight() / 2f;
    }

    private float screenX(Point p) {
        return boardCenterX() + (p.x - cameraX) * tilePixelSize();
    }

    private float screenY(Point p) {
        return boardCenterY() + (p.y - cameraY) * tilePixelSize();
    }

    private boolean isVisibleOnBoard(Point p) {
        float sx = screenX(p);
        float sy = screenY(p);
        float size = tilePixelSize();

        return sx > -size
                && sx < boardViewportWidth() + size
                && sy > -size
                && sy < boardViewportHeight() + size;
    }

    private void drawBoardBackground() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.045f, 0.052f, 0.060f, 1f);
        shapes.rect(0, 0, boardViewportWidth(), boardViewportHeight());
        shapes.end();
    }

    private void drawTiles() {
        float size = tilePixelSize();

        drawBaseTile(size);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point p = entry.getKey();
            if (!isVisibleOnBoard(p)) continue;

            Tile tile = entry.getValue();
            float sx = screenX(p);
            float sy = screenY(p);

            shapes.setColor(Palette.biome(tile.biome));
            shapes.rect(sx, sy, size - 2f, size - 2f);
        }
        shapes.end();

        batch.begin();
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point p = entry.getKey();
            if (!isVisibleOnBoard(p)) continue;

            Tile tile = entry.getValue();
            float sx = screenX(p);
            float sy = screenY(p);

            Texture texture = assets.get(tile.imagePath);
            if (texture != null) {
                batch.draw(
                        texture,
                        sx,
                        sy,
                        (size - 2f) / 2f,
                        (size - 2f) / 2f,
                        size - 2f,
                        size - 2f,
                        1f,
                        1f,
                        tile.rotationDegreesForRendering(),
                        0,
                        0,
                        texture.getWidth(),
                        texture.getHeight(),
                        false,
                        false
                );
            }

            font.draw(batch, shortBiome(tile.biome), sx + 4f * pixelScale(), sy + 16f * pixelScale());

            if (tile.hasSpawn() && showSpawnDebug) {
                font.draw(batch, tile.spawnSpecies.shortCode, sx + size - 18f * pixelScale(), sy + size - 8f * pixelScale());
            }
        }
        batch.end();

        drawTransitionOverlays();
        drawTransportOverlays();

        if (showGrid) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0f, 0f, 0f, 0.45f);
            for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
                Point p = entry.getKey();
                if (!isVisibleOnBoard(p)) continue;
                shapes.rect(screenX(p), screenY(p), size - 2f, size - 2f);
            }
            shapes.end();
        }
    }

    /**
     * Рисует стартовую базу отдельно от обычных тайлов биомов.
     *
     * База больше не является Tile и не имеет Biome, поэтому она не участвует
     * в общем цикле отрисовки terrain-тайлов. Да, базовый лагерь наконец-то
     * перестал притворяться лугом с административными привилегиями.
     */
    private void drawBaseTile(float size) {
        Point basePoint = simulation.map.baseTile.position;
        if (!isVisibleOnBoard(basePoint)) return;

        float sx = screenX(basePoint);
        float sy = screenY(basePoint);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.85f, 0.74f, 0.35f, 1f);
        shapes.rect(sx, sy, size - 2f, size - 2f);
        shapes.end();

        batch.begin();
        Texture texture = assets.get(BaseTile.IMAGE_PATH);
        if (texture != null) {
            batch.draw(texture, sx, sy, size - 2f, size - 2f);
        }
        font.draw(batch, "БЗ", sx + 4f * pixelScale(), sy + 16f * pixelScale());
        batch.end();

        if (showGrid) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0f, 0f, 0f, 0.45f);
            shapes.rect(sx, sy, size - 2f, size - 2f);
            shapes.end();
        }
    }

    private void drawTransitionOverlays() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point p = entry.getKey();
            if (!isVisibleOnBoard(p)) continue;

            Tile tile = entry.getValue();
            if (!tile.hasExpansion()) continue;

            float sx = screenX(p);
            float sy = screenY(p);

            for (Direction direction : tile.expansionDirections) {
                drawTransitionBase(sx, sy, direction);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point p = entry.getKey();
            if (!isVisibleOnBoard(p)) continue;

            Tile tile = entry.getValue();
            if (!tile.hasExpansion()) continue;

            float sx = screenX(p);
            float sy = screenY(p);

            for (Direction direction : tile.expansionDirections) {
                drawTransitionHatching(sx, sy, direction);
            }
        }
        shapes.end();
    }

    private void drawTransitionBase(float sx, float sy, Direction direction) {
        shapes.setColor(0.08f, 0.58f, 1f, 0.55f);

        switch (direction) {
            case NORTH, SOUTH, EAST, WEST -> drawCardinalTransitionBase(sx, sy, direction);
            case NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST -> drawDiagonalTransitionBase(sx, sy, direction);
        }
    }

    private void drawTransitionHatching(float sx, float sy, Direction direction) {
        shapes.setColor(0.03f, 0.28f, 0.95f, 0.95f);

        switch (direction) {
            case NORTH, SOUTH, EAST, WEST -> drawCardinalTransitionHatching(sx, sy, direction);
            case NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST -> drawDiagonalTransitionHatching(sx, sy, direction);
        }
    }

    private void drawCardinalTransitionBase(float sx, float sy, Direction direction) {
        float size = tilePixelSize() - 2f;
        float strip = TRANSITION_STRIP * pixelScale();
        float inset = TRANSITION_INSET * pixelScale();

        switch (direction) {
            case NORTH -> shapes.rect(sx + inset, sy + size - strip, size - inset * 2f, strip);
            case SOUTH -> shapes.rect(sx + inset, sy, size - inset * 2f, strip);
            case EAST -> shapes.rect(sx + size - strip, sy + inset, strip, size - inset * 2f);
            case WEST -> shapes.rect(sx, sy + inset, strip, size - inset * 2f);
            default -> {
            }
        }
    }

    private void drawCardinalTransitionHatching(float sx, float sy, Direction direction) {
        float size = tilePixelSize() - 2f;
        float strip = TRANSITION_STRIP * pixelScale();
        float inset = TRANSITION_INSET * pixelScale();

        switch (direction) {
            case NORTH -> drawHatchedRect(sx + inset, sy + size - strip, size - inset * 2f, strip);
            case SOUTH -> drawHatchedRect(sx + inset, sy, size - inset * 2f, strip);
            case EAST -> drawHatchedRect(sx + size - strip, sy + inset, strip, size - inset * 2f);
            case WEST -> drawHatchedRect(sx, sy + inset, strip, size - inset * 2f);
            default -> {
            }
        }
    }

    private void drawDiagonalTransitionBase(float sx, float sy, Direction direction) {
        float size = tilePixelSize() - 2f;
        float arm = size * CORNER_ARM_RATIO;

        switch (direction) {
            case NORTH_WEST -> shapes.triangle(sx, sy + size, sx + arm, sy + size, sx, sy + size - arm);
            case NORTH_EAST -> shapes.triangle(sx + size, sy + size, sx + size - arm, sy + size, sx + size, sy + size - arm);
            case SOUTH_WEST -> shapes.triangle(sx, sy, sx + arm, sy, sx, sy + arm);
            case SOUTH_EAST -> shapes.triangle(sx + size, sy, sx + size - arm, sy, sx + size, sy + arm);
            default -> {
            }
        }
    }

    private void drawDiagonalTransitionHatching(float sx, float sy, Direction direction) {
        float size = tilePixelSize() - 2f;
        float arm = size * CORNER_ARM_RATIO;

        switch (direction) {
            case NORTH_WEST -> drawCornerHatchingNorthWest(sx, sy + size, arm);
            case NORTH_EAST -> drawCornerHatchingNorthEast(sx + size, sy + size, arm);
            case SOUTH_WEST -> drawCornerHatchingSouthWest(sx, sy, arm);
            case SOUTH_EAST -> drawCornerHatchingSouthEast(sx + size, sy, arm);
            default -> {
            }
        }
    }

    private void drawHatchedRect(float x, float y, float width, float height) {
        float hatchSpacing = HATCH_SPACING * pixelScale();
        float hatchWidth = HATCH_WIDTH * pixelScale();

        for (float c = -height; c <= width; c += hatchSpacing) {
            ArrayList<float[]> pts = new ArrayList<>(4);

            if (c >= -height && c <= 0f) pts.add(new float[]{0f, -c});
            if (c >= width - height && c <= width) pts.add(new float[]{width, width - c});
            if (c >= 0f && c <= width) pts.add(new float[]{c, 0f});
            if (c >= -height && c <= width - height) pts.add(new float[]{height + c, height});

            if (pts.size() >= 2) {
                float[] p1 = pts.get(0);
                float[] p2 = pts.get(1);
                if (Math.abs(p1[0] - p2[0]) > 0.01f || Math.abs(p1[1] - p2[1]) > 0.01f) {
                    shapes.rectLine(x + p1[0], y + p1[1], x + p2[0], y + p2[1], hatchWidth);
                }
            }
        }
    }

    private void drawCornerHatchingNorthWest(float xLeft, float yTop, float arm) {
        float hatchSpacing = HATCH_SPACING * pixelScale();
        float hatchWidth = HATCH_WIDTH * pixelScale();

        for (float t = hatchSpacing; t < arm; t += hatchSpacing) {
            shapes.rectLine(xLeft + t, yTop, xLeft, yTop - t, hatchWidth);
        }
    }

    private void drawCornerHatchingNorthEast(float xRight, float yTop, float arm) {
        float hatchSpacing = HATCH_SPACING * pixelScale();
        float hatchWidth = HATCH_WIDTH * pixelScale();

        for (float t = hatchSpacing; t < arm; t += hatchSpacing) {
            shapes.rectLine(xRight - t, yTop, xRight, yTop - t, hatchWidth);
        }
    }

    private void drawCornerHatchingSouthWest(float xLeft, float yBottom, float arm) {
        float hatchSpacing = HATCH_SPACING * pixelScale();
        float hatchWidth = HATCH_WIDTH * pixelScale();

        for (float t = hatchSpacing; t < arm; t += hatchSpacing) {
            shapes.rectLine(xLeft + t, yBottom, xLeft, yBottom + t, hatchWidth);
        }
    }

    private void drawCornerHatchingSouthEast(float xRight, float yBottom, float arm) {
        float hatchSpacing = HATCH_SPACING * pixelScale();
        float hatchWidth = HATCH_WIDTH * pixelScale();

        for (float t = hatchSpacing; t < arm; t += hatchSpacing) {
            shapes.rectLine(xRight - t, yBottom, xRight, yBottom + t, hatchWidth);
        }
    }


    /**
     * Рисует транспортную инфраструктуру поверх тайла:
     * - мостик внутри тайла;
     * - дороги на стыках тайлов.
     *
     * Дорога — это связь между двумя соседними тайлами, поэтому визуально она
     * кладётся на границу/угол между клетками, а не в центр тайла. Мостик,
     * наоборот, лежит внутри тайла и разрешает водителю выезд в любую соседнюю
     * открытую клетку. Мир снова усложнился из-за картонки, но хотя бы честно.
     */
    private void drawTransportOverlays() {
        drawRoadOverlays();
        drawBridgeOverlays();
    }

    private void drawRoadOverlays() {
        Texture roadTexture = assets.get("routes/road.png");

        if (roadTexture != null) {
            batch.begin();
            for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
                Point point = entry.getKey();
                if (!isVisibleOnBoard(point)) continue;

                Tile tile = entry.getValue();
                for (Direction direction : tile.roadDirections) {
                    drawRoadTexture(point, direction, roadTexture);
                }
            }
            batch.end();
            return;
        }

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.93f, 0.78f, 0.42f, 0.95f);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point point = entry.getKey();
            if (!isVisibleOnBoard(point)) continue;

            Tile tile = entry.getValue();
            for (Direction direction : tile.roadDirections) {
                drawRoadFallback(point, direction);
            }
        }
        shapes.end();
    }

    private void drawBridgeOverlays() {
        Texture bridgeTexture = assets.get("routes/bridge.png");
        float tileSize = tilePixelSize();

        if (bridgeTexture != null) {
            batch.begin();
            for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
                Point point = entry.getKey();
                if (!isVisibleOnBoard(point)) continue;

                Tile tile = entry.getValue();
                if (!tile.hasBridge) continue;

                float iconSize = tileSize * 0.34f;
                float x = screenX(point) + (tileSize - iconSize) / 2f;
                float y = screenY(point) + (tileSize - iconSize) / 2f;
                batch.draw(bridgeTexture, x, y, iconSize, iconSize);
            }
            batch.end();
            return;
        }

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.62f, 0.44f, 0.26f, 0.92f);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point point = entry.getKey();
            if (!isVisibleOnBoard(point)) continue;

            Tile tile = entry.getValue();
            if (!tile.hasBridge) continue;

            float sx = screenX(point);
            float sy = screenY(point);
            float bridgeWidth = tileSize * 0.42f;
            float bridgeHeight = tileSize * 0.13f;
            shapes.rect(
                    sx + (tileSize - bridgeWidth) / 2f,
                    sy + (tileSize - bridgeHeight) / 2f,
                    bridgeWidth,
                    bridgeHeight
            );
        }
        shapes.end();
    }

    private void drawRoadTexture(Point point, Direction direction, Texture texture) {
        float tileSize = tilePixelSize();
        float roadLength = isCardinal(direction) ? tileSize * 0.34f : tileSize * 0.30f;
        float roadWidth = tileSize * 0.10f;
        float sx = screenX(point);
        float sy = screenY(point);
        float cx = sx + tileSize / 2f;
        float cy = sy + tileSize / 2f;
        float centerX = roadCenterX(sx, tileSize, direction);
        float centerY = roadCenterY(sy, tileSize, direction);
        float rotation = roadRotationDegrees(direction);

        batch.draw(
                texture,
                centerX - roadLength / 2f,
                centerY - roadWidth / 2f,
                roadLength / 2f,
                roadWidth / 2f,
                roadLength,
                roadWidth,
                1f,
                1f,
                rotation,
                0,
                0,
                texture.getWidth(),
                texture.getHeight(),
                false,
                false
        );
    }

    private void drawRoadFallback(Point point, Direction direction) {
        float tileSize = tilePixelSize();
        float sx = screenX(point);
        float sy = screenY(point);
        float centerX = roadCenterX(sx, tileSize, direction);
        float centerY = roadCenterY(sy, tileSize, direction);
        float length = isCardinal(direction) ? tileSize * 0.32f : tileSize * 0.27f;
        float width = Math.max(3f, tileSize * 0.045f);

        float dx = direction.dx;
        float dy = direction.dy;
        float norm = (float) Math.sqrt(dx * dx + dy * dy);
        if (norm <= 0f) return;

        dx /= norm;
        dy /= norm;

        shapes.rectLine(
                centerX - dx * length / 2f,
                centerY - dy * length / 2f,
                centerX + dx * length / 2f,
                centerY + dy * length / 2f,
                width
        );
    }

    private float roadCenterX(float tileScreenX, float tileSize, Direction direction) {
        return tileScreenX + tileSize / 2f + direction.dx * tileSize / 2f;
    }

    private float roadCenterY(float tileScreenY, float tileSize, Direction direction) {
        return tileScreenY + tileSize / 2f + direction.dy * tileSize / 2f;
    }

    private boolean isCardinal(Direction direction) {
        return direction.dx == 0 || direction.dy == 0;
    }

    private float roadRotationDegrees(Direction direction) {
        return switch (direction) {
            case EAST, WEST -> 0f;
            case NORTH, SOUTH -> 90f;
            case NORTH_EAST, SOUTH_WEST -> 45f;
            case NORTH_WEST, SOUTH_EAST -> -45f;
        };
    }

    private void drawPlacementFrontier() {
        float size = tilePixelSize();
        float inset = 6f * pixelScale();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.75f, 0.75f, 0.75f, 0.40f);
        for (Point p : simulation.map.availablePlacementPoints()) {
            if (!isVisibleOnBoard(p)) continue;
            float sx = screenX(p);
            float sy = screenY(p);
            shapes.rect(sx + inset, sy + inset, size - inset * 2f - 2f, size - inset * 2f - 2f);
        }
        shapes.end();
    }

    private String shortBiome(Biome b) {
        return switch (b) {
            case BROADLEAF_FOREST -> "ЛЛ";
            case CONIFEROUS_FOREST -> "ХЛ";
            case MEADOW -> "Л";
            case SWAMP -> "Б";
            case RIVER -> "Р";
            case LAKE -> "О";
            case FLOODPLAIN -> "П";
            case MOUNTAIN -> "Г";
            case LANDING -> "БР";
        };
    }

    private void drawTraps() {
        float size = tilePixelSize();
        Texture trapTexture = assets.get("traps/trap.png");

        if (trapTexture != null) {
            batch.begin();
            for (PlayerState player : simulation.players) {
                for (Trap trap : player.traps) {
                    if (!trap.active || !isVisibleOnBoard(trap.position)) continue;

                    float iconSize = size * 0.34f;
                    float x = screenX(trap.position) + (size - iconSize) / 2f;
                    float y = screenY(trap.position) + (size - iconSize) / 2f;
                    batch.draw(trapTexture, x, y, iconSize, iconSize);
                }
            }
            batch.end();
            return;
        }

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 1f);
        for (PlayerState player : simulation.players) {
            for (Trap trap : player.traps) {
                if (!trap.active || !isVisibleOnBoard(trap.position)) continue;

                float x = screenX(trap.position) + size * 0.20f;
                float y = screenY(trap.position) + size * 0.20f;
                shapes.line(x, y, x + size * 0.55f, y + size * 0.55f);
                shapes.line(x + size * 0.55f, y, x, y + size * 0.55f);
            }
        }
        shapes.end();
    }

    private void drawPieces() {
        BoardOccupancy occupancy = BoardOccupancy.from(simulation);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPieceFallbacksAtPoint(simulation.map.baseTile.position, occupancy);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            drawPieceFallbacksAtPoint(entry.getKey(), occupancy);
        }
        shapes.end();

        batch.begin();
        drawPieceTexturesAtPoint(simulation.map.baseTile.position, occupancy);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            drawPieceTexturesAtPoint(entry.getKey(), occupancy);
        }
        batch.end();
    }

    private void drawPieceFallbacksAtPoint(Point tilePoint, BoardOccupancy occupancy) {
        if (!isVisibleOnBoard(tilePoint)) return;

        List<BoardPiece> pieces = occupancy.at(tilePoint);
        for (int i = 0; i < pieces.size(); i++) {
            drawPieceFallback(tilePoint, pieces.get(i), i, pieces.size());
        }
    }

    private void drawPieceTexturesAtPoint(Point tilePoint, BoardOccupancy occupancy) {
        if (!isVisibleOnBoard(tilePoint)) return;

        List<BoardPiece> pieces = occupancy.at(tilePoint);
        for (int i = 0; i < pieces.size(); i++) {
            drawPieceTextureOrLabel(tilePoint, pieces.get(i), i, pieces.size());
        }
    }

    private void drawPieceFallback(Point tilePoint, BoardPiece piece, int pieceIndex, int totalPieces) {
        Texture texture = pieceTexture(piece);
        if (texture != null) return;

        PieceRect rect = pieceRect(tilePoint, pieceIndex, totalPieces);

        if (piece.type == BoardPiece.Type.DINOSAUR) {
            shapes.setColor(Palette.species(piece.dinosaur.species));
            shapes.circle(rect.centerX(), rect.centerY(), rect.size() * 0.42f);
            return;
        }

        setRangerFallbackColor(piece.player.color);
        shapes.rect(rect.x(), rect.y(), rect.size(), rect.size());
    }

    private void drawPieceTextureOrLabel(Point tilePoint, BoardPiece piece, int pieceIndex, int totalPieces) {
        PieceRect rect = pieceRect(tilePoint, pieceIndex, totalPieces);
        Texture texture = pieceTexture(piece);

        if (texture != null) {
            batch.draw(texture, rect.x(), rect.y(), rect.size(), rect.size());
            return;
        }

        String label = piece.type == BoardPiece.Type.DINOSAUR
                ? piece.dinosaur.species.shortCode + piece.dinosaur.id
                : piece.role.assetPrefix.substring(0, 1).toUpperCase(Locale.ROOT);

        font.draw(batch, label, rect.x() + rect.size() * 0.18f, rect.y() + rect.size() * 0.70f);
    }

    private Texture pieceTexture(BoardPiece piece) {
        if (piece.type == BoardPiece.Type.DINOSAUR) {
            return assets.get("dinos/" + piece.dinosaur.species.imagePath);
        }

        return assets.get(piece.role.imagePath(piece.player.color));
    }

    private PieceRect pieceRect(Point tilePoint, int pieceIndex, int totalPieces) {
        int grid = totalPieces <= PIECE_GRID_SIZE * PIECE_GRID_SIZE
                ? PIECE_GRID_SIZE
                : Math.max(PIECE_GRID_SIZE + 1, (int) Math.ceil(Math.sqrt(totalPieces)));

        List<Slot> slotOrder = buildSlotOrder(grid);
        Slot slot = slotOrder.get(Math.min(pieceIndex, slotOrder.size() - 1));

        float tileSize = tilePixelSize() - 2f;
        float cellSize = tileSize / grid;
        float pieceSize = cellSize * 0.82f;

        float tileX = screenX(tilePoint);
        float tileY = screenY(tilePoint);

        float x = tileX + slot.col() * cellSize + (cellSize - pieceSize) / 2f;
        float y = tileY + slot.row() * cellSize + (cellSize - pieceSize) / 2f;

        return new PieceRect(x, y, pieceSize);
    }

    private static List<Slot> buildSlotOrder(int grid) {
        ArrayList<Slot> slots = new ArrayList<>();

        float center = (grid - 1) / 2f;
        for (int row = 0; row < grid; row++) {
            for (int col = 0; col < grid; col++) {
                slots.add(new Slot(row, col, Math.abs(row - center) + Math.abs(col - center)));
            }
        }

        slots.sort(Comparator
                .comparingDouble((Slot slot) -> slot.distanceToCenter)
                .thenComparingInt(slot -> slot.row)
                .thenComparingInt(slot -> slot.col));

        return slots;
    }

    private void setRangerFallbackColor(RangerColor color) {
        switch (color) {
            case RED -> shapes.setColor(0.85f, 0.12f, 0.10f, 1f);
            case BLUE -> shapes.setColor(0.12f, 0.38f, 0.95f, 1f);
            case GREEN -> shapes.setColor(0.15f, 0.70f, 0.20f, 1f);
            case YELLOW -> shapes.setColor(0.95f, 0.80f, 0.10f, 1f);
        }
    }

    private void drawHud() {
        drawHudPanel();

        batch.begin();

        float x = boardViewportWidth() + 18;
        float y = Gdx.graphics.getHeight() - 24;

        font.draw(batch, "Мезозоя Visual Simulator", x, y);
        y -= 22;
        font.draw(batch, "Раунд: " + simulation.round + (paused ? " [PAUSE]" : ""), x, y);
        y -= 18;
        font.draw(batch, "Выложено тайлов: " + simulation.map.openedCount(), x, y);
        y -= 18;
        font.draw(batch, "Основных тайлов: " + simulation.tileBag.remainingMain(), x, y);
        y -= 18;
        font.draw(batch, "Доп. тайлов: " + simulation.tileBag.remainingExtra(), x, y);
        y -= 18;
        font.draw(batch, "Динозавров: " + aliveDinos() + " / всего " + simulation.dinosaurs.size(), x, y);
        y -= 18;
        font.draw(batch, "Масштаб: " + Math.round(zoomPercent()) + "%", x, y);
        y -= 18;
        font.draw(batch, "Скорость: " + String.format(Locale.US, "%.2f", stepDelay) + " сек/ход", x, y);
        y -= 24;

        if (showDebug) {
            font.draw(batch, "Лог:", x, y);
            y -= 18;

            for (String line : simulation.log) {
                if (y < 22) break;
                font.draw(batch, trim(line, 47), x, y);
                y -= 16;
            }
        }

        batch.end();
    }

    private void drawHudPanel() {
        int hudX = boardViewportWidth();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.095f, 0.105f, 0.115f, 1f);
        shapes.rect(hudX, 0, HUD_WIDTH, Gdx.graphics.getHeight());

        shapes.setColor(0.18f, 0.20f, 0.23f, 1f);
        shapes.rect(hudX, 0, 2, Gdx.graphics.getHeight());

        shapes.setColor(0.13f, 0.145f, 0.16f, 1f);
        shapes.rect(hudX + 10, 10, HUD_WIDTH - 20, Gdx.graphics.getHeight() - 20);
        shapes.end();
    }

    private int aliveDinos() {
        int count = 0;
        for (Dinosaur d : simulation.dinosaurs) {
            if (!d.captured && !d.removed) count++;
        }
        return count;
    }

    private String trim(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        assets.dispose();
    }

    private record Slot(int row, int col, double distanceToCenter) {
    }

    private record PieceRect(float x, float y, float size) {
        float centerX() {
            return x + size / 2f;
        }

        float centerY() {
            return y + size / 2f;
        }
    }
}
