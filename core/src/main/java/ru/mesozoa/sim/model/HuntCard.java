package ru.mesozoa.sim.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Карта из колоды «Охота».
 *
 * Каждая карта хранит игровое название и стоимость подготовки. Дубликаты вроде
 * двух разных «Затаиться» заведены отдельными enum-константами, потому что Java,
 * как обычно, не умеет читать мысли автора настолки и требует уникальные имена.
 */
public enum HuntCard {
    CHECK_WIND("Проверить ветер", 1),
    HIDE_LOW("Затаиться", 1),
    COVER_TRACKS("Замести следы", 1),
    REMOVE_SMELL("Убрать запах", 1),
    LIE_IN_GRASS("Прилечь в траву", 1),

    CAMOUFLAGE("Маскировка", 2),
    WAITING("Выжидание", 2),
    CHOOSE_POSITION("Выбор позиции", 2),
    OBSERVATION("Наблюдение", 2),
    SET_BAIT("Установить приманку", 2),
    WAITING_EXTRA("Выжидание", 2),

    DOUBLE_CHECK("Ещё раз всё проверить", 3),
    LOAD_RIFLE("Зарядить ружьё", 3),
    CHOOSE_COVER("Выбрать укрытие", 3),
    HIDE_READY("Затаиться", 3),
    FILL_TRANQUILIZER("Наполнить транквилизатор", 3),

    STUDY_TERRAIN("Изучить территорию", 4),
    MAKE_BAIT("Сделать приманку", 4),
    CAREFUL_PREPARATION("Тщательная подготовка", 4),

    FELL_ASLEEP("Уснул в ожидании", 5);

    /** Человекочитаемое название карты для лога. */
    public final String displayName;

    /** Сколько очков подготовки добавляет карта. */
    public final int points;

    HuntCard(String displayName, int points) {
        this.displayName = displayName;
        this.points = points;
    }

    /**
     * Создаёт новую перемешанную колоду охоты.
     *
     * @param random генератор случайности партии
     * @return изменяемый список карт в порядке добора
     */
    public static List<HuntCard> createShuffledDeck(Random random) {
        ArrayList<HuntCard> deck = new ArrayList<>(List.of(values()));
        Collections.shuffle(deck, random);
        return deck;
    }
}
