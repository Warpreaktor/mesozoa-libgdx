package ru.mesozoa.sim.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Активное выслеживание М-травоядного охотником.
 *
 * Состояние хранит целевого динозавра, вытянутые карты «Охота», сумму подготовки
 * и цепочку следов. Пока оно активно, охотник должен продолжать идти за этим
 * зверем, а не внезапно переключиться на другую цель до успеха или провала.
 */
public final class TrackingTrail {

    /** Максимальная сумма карт, после которой зверь уходит от охотника. */
    public static final int MAX_PREPARATION_SCORE = 10;

    /** Максимальное число попыток в одной цепочке следов. */
    public static final int MAX_ATTEMPTS = 3;

    /** ID игрока, который ведёт выслеживание. */
    public final int playerId;

    /** ID целевого М-травоядного. */
    public final int dinosaurId;

    /** Вид целевого М-травоядного. */
    public final Species species;

    /** Оставшаяся колода «Охота» для текущей цепочки следов. */
    private final ArrayDeque<HuntCard> deck;

    /** Карты, уже вытянутые охотником в этой цепочке следов. */
    private final ArrayList<HuntCard> drawnCards = new ArrayList<>();

    /** Жетоны следов, лежащие на карте для текущей цепочки. */
    private final ArrayList<TrackingTrailToken> trailTokens = new ArrayList<>();

    /** Сколько попыток поимки уже сделано в текущей цепочке. */
    private int attempts;

    /** Текущая сумма очков по вытянутым картам охоты. */
    private int preparationScore;

    /**
     * Создаёт новую цепочку выслеживания.
     *
     * @param playerId владелец выслеживания
     * @param dinosaurId целевой динозавр
     * @param species вид целевого динозавра
     * @param shuffledDeck перемешанная колода «Охота»
     */
    public TrackingTrail(int playerId, int dinosaurId, Species species, List<HuntCard> shuffledDeck) {
        this.playerId = playerId;
        this.dinosaurId = dinosaurId;
        this.species = species;
        this.deck = new ArrayDeque<>(shuffledDeck);
    }

    /**
     * Начинает очередную попытку поимки.
     *
     * @return номер попытки в текущей цепочке
     */
    public int startAttempt() {
        attempts++;
        return attempts;
    }

    /**
     * Добирает две стартовые карты для первой попытки выслеживания.
     *
     * @return фактически вытянутые карты
     */
    public List<HuntCard> drawInitialCards() {
        ArrayList<HuntCard> result = new ArrayList<>(2);
        drawOneCard().ifPresent(result::add);
        drawOneCard().ifPresent(result::add);
        return result;
    }

    /**
     * Добирает одну карту для продолжения выслеживания по уже выложенным следам.
     *
     * @return вытянутая карта или пустой результат, если колода закончилась
     */
    public Optional<HuntCard> drawFollowUpCard() {
        return drawOneCard();
    }

    /**
     * Запоминает жетон следа, который зверь оставил после неудачной попытки.
     *
     * @param from клетка, откуда ушёл динозавр
     * @param to клетка, куда он ушёл
     * @param direction направление жетона следа
     */
    public void addTrailToken(Point from, Point to, Direction direction) {
        if (from == null || to == null || direction == null) {
            return;
        }
        trailTokens.add(new TrackingTrailToken(from, to, direction, trailTokens.size() + 1));
    }

    /** @return true, если сумма карт уже превысила допустимый лимит */
    public boolean isOverPrepared() {
        return preparationScore > MAX_PREPARATION_SCORE;
    }

    /** @return true, если это первая попытка в цепочке следов */
    public boolean isFirstAttempt() {
        return attempts == 1;
    }

    /** @return сколько попыток уже сделано */
    public int attempts() {
        return attempts;
    }

    /** @return текущая сумма очков карт охоты */
    public int preparationScore() {
        return preparationScore;
    }

    /** @return неизменяемая копия вытянутых карт */
    public List<HuntCard> drawnCards() {
        return List.copyOf(drawnCards);
    }

    /** @return неизменяемая копия жетонов следов на карте */
    public List<TrackingTrailToken> trailTokens() {
        return List.copyOf(trailTokens);
    }

    /** @return количество оставшихся карт в колоде */
    public int remainingCards() {
        return deck.size();
    }

    private Optional<HuntCard> drawOneCard() {
        HuntCard card = deck.pollFirst();
        if (card == null) {
            return Optional.empty();
        }

        drawnCards.add(card);
        preparationScore += card.points;
        return Optional.of(card);
    }
}
