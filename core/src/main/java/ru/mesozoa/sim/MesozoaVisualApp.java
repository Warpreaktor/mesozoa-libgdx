package ru.mesozoa.sim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import ru.mesozoa.sim.model.*;
import ru.mesozoa.sim.rules.GameSimulation;
import ru.mesozoa.sim.rules.SimulationConfig;
import ru.mesozoa.sim.view.AssetCatalog;
import ru.mesozoa.sim.view.Palette;
import ru.mesozoa.sim.view.RussianFontFactory;

import java.util.Locale;
import java.util.Map;

public final class MesozoaVisualApp extends ApplicationAdapter {
    private final long seed;
    private float stepDelay;
    private GameSimulation simulation;

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private AssetCatalog assets;

    private boolean paused = true;
    private boolean showGrid = true;
    private boolean showSpawnDebug = true;
    private boolean showDebug = true;
    private float timer = 0f;

    private final int tileSize = 48;
    private final int offsetX = 24;
    private final int offsetY = 24;

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
        restart();
    }

    private void restart() {
        SimulationConfig config = new SimulationConfig();
        simulation = new GameSimulation(config, seed);
        paused = true;
        timer = 0f;
    }

    @Override
    public void render() {
        handleInput();

        if (!paused && !simulation.gameOver) {
            timer += Gdx.graphics.getDeltaTime();
            if (timer >= stepDelay) {
                timer = 0f;
                simulation.stepRound();
            }
        }

        Gdx.gl.glClearColor(0.06f, 0.07f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawTiles();
        drawPlacementFrontier();
        drawTraps();
        drawDinosaurs();
        drawRangers();
        drawHud();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused;
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) simulation.stepRound();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) restart();
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) showGrid = !showGrid;
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) showSpawnDebug = !showSpawnDebug;
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) showDebug = !showDebug;
        if (Gdx.input.isKeyJustPressed(Input.Keys.PLUS) || Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)) {
            stepDelay = Math.max(0.05f, stepDelay * 0.75f);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) {
            stepDelay = Math.min(2.0f, stepDelay * 1.25f);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
    }

    private float screenX(Point p) {
        return offsetX + (p.x - simulation.map.minX()) * tileSize;
    }

    private float screenY(Point p) {
        return offsetY + (p.y - simulation.map.minY()) * tileSize;
    }

    private void drawTiles() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point p = entry.getKey();
            Tile tile = entry.getValue();

            float sx = screenX(p);
            float sy = screenY(p);

            shapes.setColor(Palette.biome(tile.biome));
            shapes.rect(sx, sy, tileSize - 2, tileSize - 2);
        }
        shapes.end();

        batch.begin();
        for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
            Point p = entry.getKey();
            Tile tile = entry.getValue();

            float sx = screenX(p);
            float sy = screenY(p);

            Texture texture = assets.get(tile.imagePath);
            if (texture != null) {
                batch.draw(texture, sx, sy, tileSize - 2, tileSize - 2);
            }

            font.draw(batch, shortBiome(tile.biome), sx + 4, sy + 16);

            if (tile.hasSpawn() && showSpawnDebug) {
                font.draw(batch, tile.spawnSpecies.shortCode, sx + tileSize - 18, sy + tileSize - 8);
            }
        }
        batch.end();

        if (showGrid) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(0f, 0f, 0f, 0.45f);
            for (Map.Entry<Point, Tile> entry : simulation.map.entries()) {
                Point p = entry.getKey();
                shapes.rect(screenX(p), screenY(p), tileSize - 2, tileSize - 2);
            }
            shapes.end();
        }
    }

    private void drawPlacementFrontier() {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.75f, 0.75f, 0.75f, 0.40f);
        for (Point p : simulation.map.availablePlacementPoints()) {
            float sx = screenX(p);
            float sy = screenY(p);
            shapes.rect(sx + 6, sy + 6, tileSize - 14, tileSize - 14);
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
            case LANDING -> "БЗ";
        };
    }

    private void drawTraps() {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 1f);
        for (PlayerState player : simulation.players) {
            for (Trap trap : player.traps) {
                if (!trap.active) continue;
                float x = screenX(trap.position) + tileSize * 0.20f;
                float y = screenY(trap.position) + tileSize * 0.20f;
                shapes.line(x, y, x + tileSize * 0.55f, y + tileSize * 0.55f);
                shapes.line(x + tileSize * 0.55f, y, x, y + tileSize * 0.55f);
            }
        }
        shapes.end();
    }

    private void drawDinosaurs() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Dinosaur dino : simulation.dinosaurs) {
            if (dino.captured || dino.removed) continue;

            float cx = screenX(dino.position) + tileSize * 0.50f;
            float cy = screenY(dino.position) + tileSize * 0.50f;

            shapes.setColor(Palette.species(dino.species));
            shapes.circle(cx, cy, 12f);
        }
        shapes.end();

        batch.begin();
        for (Dinosaur dino : simulation.dinosaurs) {
            if (dino.captured || dino.removed) continue;

            float sx = screenX(dino.position) + 12;
            float sy = screenY(dino.position) + 12;

            Texture texture = assets.get("dinos/" + dino.species.imagePath);
            if (texture != null) {
                batch.draw(texture, sx, sy, 26, 26);
            } else {
                font.draw(batch, dino.species.shortCode + dino.id, sx - 2, sy + 22);
            }
        }
        batch.end();
    }

    private void drawRangers() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (PlayerState player : simulation.players) {
            drawRangerSquare(player.scout, 0, 0.2f, 0.8f, 1f);
            drawRangerSquare(player.hunter, 1, 0.9f, 0.2f, 0.2f);
            drawRangerSquare(player.engineer, 2, 0.8f, 0.8f, 0.15f);
        }
        shapes.end();
    }

    private void drawRangerSquare(Point p, int offsetIndex, float r, float g, float b) {
        float sx = screenX(p) + 4 + offsetIndex * 12;
        float sy = screenY(p) + 4;
        shapes.setColor(r, g, b, 1f);
        shapes.rect(sx, sy, 10, 10);
    }

    private void drawHud() {
        batch.begin();

        float boardWidth = simulation.map.widthInTiles() * tileSize;
        float x = offsetX + boardWidth + 48;
        float y = Gdx.graphics.getHeight() - 24;

        font.draw(batch, "Мезозоя Visual Simulator", x, y); y -= 22;
        font.draw(batch, "Раунд: " + simulation.round + (paused ? "  [PAUSE]" : ""), x, y); y -= 18;
        font.draw(batch, "Выложено тайлов: " + simulation.map.openedCount(), x, y); y -= 18;
        font.draw(batch, "Тайлов в мешке: " + simulation.tileBag.remaining(), x, y); y -= 18;
        font.draw(batch, "Динозавров: " + aliveDinos() + " / всего " + simulation.dinosaurs.size(), x, y); y -= 18;
        font.draw(batch, "Скорость: " + String.format(Locale.US, "%.2f", stepDelay) + " сек/раунд", x, y); y -= 24;

        font.draw(batch, "SPACE пауза, N шаг, R рестарт", x, y); y -= 18;
        font.draw(batch, "G сетка, S спауны, D debug", x, y); y -= 28;

        if (showDebug) {
            font.draw(batch, "Лог:", x, y); y -= 18;
            for (String line : simulation.log) {
                font.draw(batch, trim(line, 58), x, y);
                y -= 16;
            }
        }

        batch.end();
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
}
