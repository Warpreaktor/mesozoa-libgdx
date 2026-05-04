package ru.mesozoa.sim.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Активная засада охотника на M-хищника.
 *
 * Засада хранит клетку с приманкой, целевого динозавра, текущую сумму подготовки
 * и оставшуюся колоду «Охота». Пока засада активна, охотник обязан тратить свою
 * активацию на ожидание или добор карты, а не бегать по острову как человек,
 * внезапно забывший, что он лежит в кустах с транквилизатором.
 */
public final class HuntAmbush {

    /** Максимально допустимая сумма подготовки охотника. */
    public static final int MAX_PREPARATION_SCORE = 10;

    /** ID игрока, который начал засаду. */
    public final int playerId;

    /** ID динозавра, под маршрут которого положена приманка. */
    public final int dinosaurId;

    /** Вид целевого хищника. */
    public final Species species;

    /** Клетка, на которой лежит приманка и ждёт охотник. */
    public final Point baitPosition;

    /** Оставшаяся колода охоты. */
    private final ArrayDeque<HuntCard> deck;

    /** Уже вытянутые карты охоты. */
    private final ArrayList<HuntCard> drawnCards = new ArrayList<>();

    /** Текущая сумма подготовки охотника. */
    private int preparationScore;

    /**
     * Сколько активаций охотник уже провёл в этой засаде.
     *
     * Счётчик нужен, чтобы AI не лежал двадцать ходов на одной поляне,
     * если хищник явно выбрал другой маршрут. Подготовка может быть отличной,
     * но если динозавр не пришёл, это уже не охота, а пикник с транквилизатором.
     */
    private int ambushTurns;

    /**
     * Создаёт новую активную засаду.
     *
     * @param playerId владелец засады
     * @param dinosaurId целевой динозавр
     * @param species вид целевого динозавра
     * @param baitPosition клетка с приманкой
     * @param shuffledDeck перемешанная колода охоты
     */
    public HuntAmbush(int playerId, int dinosaurId, Species species, Point baitPosition, List<HuntCard> shuffledDeck) {
        this.playerId = playerId;
        this.dinosaurId = dinosaurId;
        this.species = species;
        this.baitPosition = baitPosition;
        this.deck = new ArrayDeque<>(shuffledDeck);
    }

    /**
     * Добирает следующую карту охоты и добавляет её стоимость к подготовке.
     *
     * @return вытянутая карта или Optional.empty(), если колода закончилась
     */
    public Optional<HuntCard> drawCard() {
        HuntCard card = deck.pollFirst();
        if (card == null) {
            return Optional.empty();
        }

        drawnCards.add(card);
        preparationScore += card.points;
        return Optional.of(card);
    }

    /** @return текущая сумма подготовки */
    public int preparationScore() {
        return preparationScore;
    }

    /** @return true, если подготовка превысила лимит и охотник сорвал засаду */
    public boolean isOverPrepared() {
        return preparationScore > MAX_PREPARATION_SCORE;
    }

    /**
     * Увеличивает количество активаций, проведённых в засаде.
     */
    public void advanceAmbushTurn() {
        ambushTurns++;
    }

    /** @return сколько активаций охотник уже провёл в этой засаде */
    public int ambushTurns() {
        return ambushTurns;
    }

    /** @return количество карт, оставшихся в колоде */
    public int remainingCards() {
        return deck.size();
    }

    /** @return неизменяемая копия вытянутых карт */
    public List<HuntCard> drawnCards() {
        return List.copyOf(drawnCards);
    }
}
