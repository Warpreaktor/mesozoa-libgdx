package ru.mesozoa.sim.ui;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Species;
import ru.mesozoa.sim.model.Trap;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Locale;

/**
 * Scene2D HUD поверх ручной отрисовки карты.
 *
 * Карта остаётся в SpriteBatch/ShapeRenderer, а правая панель теперь собирается
 * таблицами Scene2D. Так интерфейс перестаёт быть ручной простынёй координат и
 * начинает напоминать нормальный экспедиционный планшет, а не бухгалтерию на
 * костях бедного font.draw().
 */
public final class MesozoaHudStage {
    private static final int PANEL_PADDING = 12;
    private static final int INNER_PADDING = 8;
    private static final int MAX_LOG_LINES = 12;
    private static final int MAX_BAIT = 3;

    private final int hudWidth;
    private final Stage stage;
    private final Skin skin;
    private final Table root;
    private final Table panel;

    /**
     * Создаёт HUD-слой с фиксированной правой панелью.
     *
     * @param hudWidth ширина правой панели в пикселях
     */
    public MesozoaHudStage(int hudWidth) {
        this.hudWidth = hudWidth;
        this.stage = new Stage(new ScreenViewport());
        this.skin = MesozoaSkinFactory.create();
        this.root = new Table();
        this.panel = new Table(skin);

        root.setFillParent(true);
        root.top().right();
        stage.addActor(root);
    }

    /**
     * Пересобирает содержимое правой панели после изменения игрового состояния.
     *
     * Метод намеренно вызывается после шага симуляции, рестарта или изменения
     * настроек, а не из render-loop. UI должен показывать состояние, а не заново
     * изобретать игру 60 раз в секунду, как будто у него кризис идентичности.
     *
     * @param simulation симуляция, из которой читается состояние партии
     * @param nextStepLabel заранее рассчитанная подпись следующего шага
     * @param paused стоит ли автопрогон на паузе
     * @param showDebug нужно ли показывать лог
     * @param zoomPercent текущий масштаб карты в процентах
     * @param stepDelay задержка между шагами автопрогона
     */
    public void update(
            GameSimulation simulation,
            String nextStepLabel,
            boolean paused,
            boolean showDebug,
            float zoomPercent,
            float stepDelay
    ) {
        root.clearChildren();
        panel.clearChildren();
        panel.setBackground(skin.getDrawable("hud-background"));
        panel.pad(PANEL_PADDING);
        panel.defaults().growX().left();

        addHeader(simulation, nextStepLabel, paused, zoomPercent, stepDelay);

        PlayerState player = simulation.playerForHud();
        if (player == null) {
            addSectionTitle("Игрок");
            panel.add(label("Игроки ещё не созданы", "muted")).padBottom(8).row();
        } else {
            addPlayerInventory(simulation, player);
        }

        if (showDebug) {
            addLog(simulation);
        }

        root.add(panel).width(hudWidth).growY();
    }

    /**
     * Рисует HUD-панель.
     *
     * @param delta время с прошлого кадра
     */
    public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    /**
     * Обновляет viewport HUD при изменении размера окна.
     *
     * @param width новая ширина окна
     * @param height новая высота окна
     */
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /** Освобождает ресурсы Scene2D HUD. */
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }

    private void addHeader(
            GameSimulation simulation,
            String nextStepLabel,
            boolean paused,
            float zoomPercent,
            float stepDelay
    ) {
        Table card = card();
        card.add(label("Мезозоя", "title")).padBottom(8).row();
        card.add(label("Раунд: " + simulation.round + (paused ? " [PAUSE]" : ""), "default")).row();
        card.add(label("Следующий ход: " + nextStepLabel, "small")).padBottom(6).row();

        card.add(label("Выложено тайлов: " + simulation.map.openedCount(), "small")).row();
        card.add(label("Основных тайлов: " + simulation.tileBag.remainingMain(), "small")).row();
        card.add(label("Доп. тайлов: " + simulation.tileBag.remainingExtra(), "small")).row();
        card.add(label("Динозавров: " + aliveDinos(simulation) + " / всего " + simulation.dinosaurs.size(), "small")).row();
        card.add(label("Масштаб: " + Math.round(zoomPercent) + "%", "small")).row();
        card.add(label("Скорость: " + String.format(Locale.US, "%.2f", stepDelay) + " сек/ход", "small")).row();

        panel.add(card).padBottom(10).row();
    }

    private void addPlayerInventory(GameSimulation simulation, PlayerState player) {
        Table card = card();
        card.add(label("Игрок " + player.id + " · " + player.color.assetSuffix, "section")).padBottom(8).row();

        addTaskBlock(card, simulation, player);
        addInventoryBlock(card, simulation, player);
        addCapturedBlock(card, player);

        panel.add(card).padBottom(10).row();
    }

    private void addTaskBlock(Table parent, GameSimulation simulation, PlayerState player) {
        parent.add(sectionLabel("Задание штаба")).padTop(2).padBottom(5).row();

        if (player.task.isEmpty()) {
            parent.add(label("Задание не выдано", "muted")).padBottom(6).row();
            return;
        }

        Table taskTable = new Table(skin);
        taskTable.defaults().left().padBottom(3);

        for (Species species : player.task) {
            String status = taskStatus(simulation, player, species);
            String statusStyle = taskStatusStyle(simulation, player, species);

            taskTable.add(label(status, statusStyle)).width(74).top();
            taskTable.add(label(species.displayName, "default")).growX().padRight(8).top();
            taskTable.add(label(captureMethodText(species.captureMethod), "small")).width(90).right().top().row();
        }

        parent.add(taskTable).growX().padBottom(8).row();
    }

    private void addInventoryBlock(Table parent, GameSimulation simulation, PlayerState player) {
        int activeTraps = activeTrapCount(player);
        int maxTraps = simulation.inventoryConfig.maxTrapsPerPlayer;
        int freeTraps = Math.max(0, maxTraps - activeTraps);

        parent.add(separator()).height(1).padTop(3).padBottom(7).row();
        parent.add(sectionLabel("Инвентарь")).padBottom(5).row();

        Table inventory = new Table(skin);
        inventory.defaults().left().padBottom(4);

        inventory.add(label("Ловушки", "default")).growX();
        inventory.add(valueLabel(freeTraps + " / " + maxTraps + " свободно", freeTraps > 0 ? "good" : "warning")).width(118).right().row();

        inventory.add(label("На карте", "small")).growX();
        inventory.add(valueLabel(activeTraps + " активно", activeTraps > 0 ? "warning" : "muted")).width(118).right().row();

        inventory.add(label("Приманка", "default")).growX();
        inventory.add(valueLabel(player.hunterBait + " / " + MAX_BAIT, player.hunterBait > 0 ? "good" : "danger")).width(118).right().row();

        if (player.activeHunt != null) {
            inventory.add(label("Охота", "small")).growX();
            inventory.add(valueLabel("подг. " + player.activeHunt.preparationScore() + " / 10", "warning")).width(118).right().row();
        }

        parent.add(inventory).growX().padBottom(8).row();
    }

    private void addCapturedBlock(Table parent, PlayerState player) {
        parent.add(separator()).height(1).padTop(3).padBottom(7).row();
        parent.add(sectionLabel("Поймано")).padBottom(5).row();

        if (player.captured.isEmpty()) {
            parent.add(wrappingLabel("Пока никого. Экспедиция делает вид, что это план.", "muted")).padBottom(4).row();
            return;
        }

        for (Species species : player.captured) {
            parent.add(label(species.displayName, "good")).padBottom(3).row();
        }
    }

    private void addLog(GameSimulation simulation) {
        addSectionTitle("Лог");

        Table logTable = new Table(skin);
        logTable.defaults().left().growX().padBottom(2);

        int count = 0;
        for (String line : simulation.log) {
            if (count >= MAX_LOG_LINES) break;
            logTable.add(wrappingLabel(trim(line, 56), "small")).growX().row();
            count++;
        }

        ScrollPane scrollPane = new ScrollPane(logTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        panel.add(scrollPane).growX().height(230).row();
    }

    private void addSectionTitle(String text) {
        panel.add(label(text, "section")).padTop(4).padBottom(6).row();
    }

    private Table card() {
        Table table = new Table(skin);
        table.setBackground(skin.getDrawable("card-background"));
        table.pad(INNER_PADDING);
        table.defaults().left().growX();
        return table;
    }

    private Label sectionLabel(String text) {
        return label(text, "section");
    }

    /**
     * Создаёт обычный однострочный label без принудительного переноса.
     *
     * По умолчанию значения в таблицах должны оставаться в строку. Иначе узкая
     * правая колонка превращает слово «ловушка» в вертикальную поэму.
     */
    private Label label(String text, String style) {
        Label label = new Label(text, skin, style);
        label.setWrap(false);
        return label;
    }

    /**
     * Создаёт label с переносом строк для длинных описаний и логов.
     */
    private Label wrappingLabel(String text, String style) {
        Label label = new Label(text, skin, style);
        label.setWrap(true);
        return label;
    }

    /**
     * Создаёт правую колонку инвентаря фиксированной ширины, чтобы числовые
     * значения не схлопывались в узкий столбик символов.
     */
    private Label valueLabel(String text, String style) {
        Label label = label(text, style);
        label.setAlignment(com.badlogic.gdx.utils.Align.right);
        return label;
    }

    private Table separator() {
        Table line = new Table(skin);
        line.setBackground(skin.getDrawable("line"));
        return line;
    }

    private String taskStatus(GameSimulation simulation, PlayerState player, Species species) {
        if (player.captured.contains(species)) {
            return "пойман";
        }

        if (isTrappedNeededSpecies(simulation, player, species)) {
            return "в ловушке";
        }

        if (simulation.hasActiveHuntForSpecies(player, species)) {
            return "в засаде";
        }

        if (isVisibleNeededSpecies(simulation, species)) {
            return "на карте";
        }

        return "ищем";
    }

    private String taskStatusStyle(GameSimulation simulation, PlayerState player, Species species) {
        if (player.captured.contains(species)) {
            return "good";
        }

        if (isTrappedNeededSpecies(simulation, player, species)) {
            return "warning";
        }

        if (simulation.hasActiveHuntForSpecies(player, species)) {
            return "warning";
        }

        if (isVisibleNeededSpecies(simulation, species)) {
            return "warning";
        }

        return "muted";
    }

    /**
     * Проверяет, ждёт ли нужный вид вывоза из ловушки текущего игрока.
     *
     * @param simulation текущая симуляция
     * @param player игрок, чей HUD собирается
     * @param species вид из задания
     * @return true, если вид сидит в ловушке игрока и ещё не доставлен
     */
    private boolean isTrappedNeededSpecies(GameSimulation simulation, PlayerState player, Species species) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> simulation.isTrappedByPlayer(dinosaur, player))
                .anyMatch(dinosaur -> dinosaur.species == species);
    }

    private boolean isVisibleNeededSpecies(GameSimulation simulation, Species species) {
        return simulation.dinosaurs.stream()
                .filter(dinosaur -> !dinosaur.captured && !dinosaur.removed)
                .anyMatch(dinosaur -> dinosaur.species == species);
    }

    private String captureMethodText(CaptureMethod captureMethod) {
        return switch (captureMethod) {
            case TRAP -> "ловушка";
            case TRACKING -> "след";
            case HUNT -> "охота";
        };
    }

    private int activeTrapCount(PlayerState player) {
        int count = 0;
        for (Trap trap : player.traps) {
            if (trap.active) count++;
        }
        return count;
    }

    private int aliveDinos(GameSimulation simulation) {
        int count = 0;
        for (Dinosaur dinosaur : simulation.dinosaurs) {
            if (!dinosaur.captured && !dinosaur.removed) count++;
        }
        return count;
    }

    private String trim(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 1) + "…";
    }
}
