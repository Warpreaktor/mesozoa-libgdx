package ru.mesozoa.sim.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Активное выслеживание М-травоядного охотником.
 *
 * Состояние хранит целевого динозавра, вытянутые карты «Охота», сумму подготовки
 * и жетоны следов текущей цепочки. Одна попытка выслеживания тратит один жетон
 * следа и даёт право взять одну карту «Охота». Нет жетона — нет карты и новой
 * попытки, иначе цепочка превращается в бесконечный бумажный змей из логов.
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

    /** Сколько жетонов следа уже потрачено на попытки. */
    private int spentTrailTokens;

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
     * Проверяет, можно ли начать ещё одну попытку выслеживания.
     *
     * @return true, если остался хотя бы один жетон следа и одна карта «Охота»
     */
    public boolean canStartAttempt() {
        return spentTrailTokens < MAX_ATTEMPTS && !deck.isEmpty();
    }

    /**
     * Начинает очередную попытку поимки и тратит один жетон следа.
     *
     * @return номер попытки в текущей цепочке
     */
    public int startAttempt() {
        if (!canStartAttempt()) {
            throw new IllegalStateException("Нет жетона следа или карты Охота для новой попытки");
        }

        attempts++;
        spentTrailTokens++;
        return attempts;
    }

    /**
     * Добирает одну карту для текущей попытки выслеживания.
     *
     * @return вытянутая карта или пустой результат, если колода закончилась
     */
    public Optional<HuntCard> drawAttemptCard() {
        return drawOneCard();
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

    /** @return сколько жетонов следа ещё доступно в текущей цепочке */
    public int remainingTrailTokens() {
        return Math.max(0, MAX_ATTEMPTS - spentTrailTokens);
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
