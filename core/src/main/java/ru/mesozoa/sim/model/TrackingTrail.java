package ru.mesozoa.sim.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;


/**
 * Активное выслеживание М-травоядного охотником.
 *
 * Состояние хранит целевого динозавра, вытянутые карты «Охота», сумму подготовки
 * и жетоны следов текущей цепочки. Первая попытка начинается с двух карт
 * «Охота», последующие попытки добавляют по одной карте. Если охотник не может
 * снова выйти на одну клетку с динозавром, след обрывается, а не превращается в
 * сериал на 280 раундов.
 */
public final class TrackingTrail {

    /** Максимальная сумма карт, после которой зверь уходит от охотника. */
    public static final int MAX_PREPARATION_SCORE = 10;

    /** Максимальное число попыток и жетонов следа в одной цепочке. */
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
    private final ArrayList<TrailToken> trailTokens = new ArrayList<>();

    /** Сколько попыток поимки уже сделано в текущей цепочке. */
    private int attempts;

    /** Текущая сумма очков по вытянутым картам охоты. */
    private int preparationScore;

    /** Сколько активаций подряд охотник не смог сделать новую попытку. */
    private int activationsWithoutAttempt;

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
     * Проверяет, можно ли начать ещё одну попытку выслеживания.
     *
     * @return true, если лимит попыток не исчерпан и в колоде хватает карт
     */
    public boolean canStartAttempt() {
        return attempts < MAX_ATTEMPTS && deck.size() >= cardsNeededForNextAttempt();
    }

    /**
     * Начинает очередную попытку поимки.
     *
     * @return номер попытки в текущей цепочке
     */
    public int startAttempt() {
        if (!canStartAttempt()) {
            throw new IllegalStateException("Лимит попыток исчерпан или в колоде Охота не хватает карт");
        }

        attempts++;
        activationsWithoutAttempt = 0;
        return attempts;
    }

    /**
     * Добирает карты для текущей попытки выслеживания.
     *
     * Первая попытка берёт две стартовые карты, а каждая следующая добавляет
     * одну карту к уже набранной сумме. Так выслеживание совпадает с физической
     * моделью настолки: охотник копит подготовку, но рискует перевалить за 10.
     *
     * @return список реально вытянутых карт
     */
    public List<HuntCard> drawCardsForCurrentAttempt() {
        int cardsToDraw = attempts == 1 ? 2 : 1;
        ArrayList<HuntCard> result = new ArrayList<>(cardsToDraw);
        for (int i = 0; i < cardsToDraw; i++) {
            HuntCard card = drawOneCard();
            if (card == null) {
                break;
            }
            result.add(card);
        }
        return List.copyOf(result);
    }

    /**
     * Создаёт и запоминает жетон следа текущей попытки.
     *
     * @param position клетка, где лежит жетон
     * @param direction направление следа
     * @param captureMarker true, если жетон отмечает обездвиженного динозавра
     * @return созданный жетон следа
     */
    public TrailToken createTrailToken(Point position, Direction direction, boolean captureMarker) {
        TrailToken token = new TrailToken(
                playerId,
                dinosaurId,
                position,
                direction,
                Math.max(1, attempts),
                captureMarker
        );
        trailTokens.add(token);
        return token;
    }

    /** @return true, если сумма карт уже превысила допустимый лимит */
    public boolean isOverPrepared() {
        return preparationScore > MAX_PREPARATION_SCORE;
    }

    /** @return сколько попыток уже сделано */
    public int attempts() {
        return attempts;
    }

    /** @return сколько попыток ещё доступно в текущей цепочке */
    public int remainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }

    /**
     * Отмечает активацию, в которой охотник шёл по следу, но не смог сделать попытку.
     *
     * @return число подряд идущих активаций без новой попытки
     */
    public int registerActivationWithoutAttempt() {
        activationsWithoutAttempt++;
        return activationsWithoutAttempt;
    }

    /** @return сколько активаций подряд прошло без новой попытки */
    public int activationsWithoutAttempt() {
        return activationsWithoutAttempt;
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
    public List<TrailToken> trailTokens() {
        return List.copyOf(trailTokens);
    }

    /** @return количество оставшихся карт в колоде */
    public int remainingCards() {
        return deck.size();
    }

    private int cardsNeededForNextAttempt() {
        return attempts == 0 ? 2 : 1;
    }

    private HuntCard drawOneCard() {
        HuntCard card = deck.pollFirst();
        if (card == null) {
            return null;
        }

        drawnCards.add(card);
        preparationScore += card.points;
        return card;
    }
}
