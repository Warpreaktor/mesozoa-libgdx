package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.model.Species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Генерирует карты задания штаба для игроков.
 *
 * В новом очковом режиме это настоящая колода: каждый доступный вид кладётся в
 * неё несколькими копиями, затем игроки тянут карты на руки. Дубли не
 * схлопываются, а дают отдельные бонусные доставки с x2 очками.
 */
public final class HeadquartersTaskGenerator {

    /** Игровой конфиг, из которого берутся размер руки и доступные виды. */
    private final GameConfig config;

    /**
     * Создаёт генератор заданий штаба.
     *
     * @param config игровой конфиг партии
     */
    public HeadquartersTaskGenerator(GameConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Создаёт и перемешивает общую колоду карт задания штаба.
     *
     * @param random источник случайности партии
     * @return перемешанная колода, из которой игроки будут тянуть карты
     */
    public List<Species> createTaskDeck(Random random) {
        Objects.requireNonNull(random, "random");

        ArrayList<Species> deck = new ArrayList<>();
        int copies = Math.max(0, config.headquartersTaskCardsPerSpecies);
        for (Species species : availableTaskSpecies()) {
            for (int i = 0; i < copies; i++) {
                deck.add(species);
            }
        }
        Collections.shuffle(deck, random);
        return deck;
    }

    /**
     * Тянет карты задания для одного игрока из общей колоды.
     *
     * Если в экспериментальной конфигурации колода оказалась короче числа игроков
     * и карт на руках, метод просто выдаст столько, сколько осталось, без
     * драматичной сцены с NullPointerException. У нас тут всё-таки настолка, а не
     * корпоративная интеграция.
     *
     * @param deck общая колода задания штаба
     * @return список карт игрока, дубли сохраняются
     */
    public List<Species> drawTaskCards(List<Species> deck) {
        Objects.requireNonNull(deck, "deck");

        int count = Math.max(0, config.headquartersTaskDinosaurCount);
        ArrayList<Species> cards = new ArrayList<>(count);
        for (int i = 0; i < count && !deck.isEmpty(); i++) {
            cards.add(deck.remove(0));
        }
        return cards;
    }

    /**
     * Старый совместимый метод: создаёт независимую руку и возвращает уникальные виды.
     *
     * Новый код симуляции использует {@link #createTaskDeck(Random)} и
     * {@link #drawTaskCards(List)}, но этот метод оставлен, чтобы старые тесты не
     * спотыкались об переименование.
     *
     * @param random источник случайности
     * @return уникальные виды из случайной руки
     */
    public Set<Species> createTask(Random random) {
        List<Species> deck = createTaskDeck(random);
        return drawTaskCards(deck).stream().collect(() -> EnumSet.noneOf(Species.class), Set::add, Set::addAll);
    }

    /** Возвращает виды, которые разрешено класть в колоду задания. */
    private List<Species> availableTaskSpecies() {
        return config.spawnTiles.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
