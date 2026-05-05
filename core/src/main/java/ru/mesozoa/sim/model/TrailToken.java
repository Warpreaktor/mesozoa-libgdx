package ru.mesozoa.sim.model;

/**
 * Жетон следа, лежащий на карте.
 *
 * Технически это такой же физический маркер игрока, как ловушка: у него есть
 * владелец, клетка на поле и состояние активности. Дополнительно след хранит
 * направление, чтобы на карте было видно, куда ушёл М-травоядный после
 * неудачной попытки выслеживания.
 */
public final class TrailToken {

    /** ID игрока, который выложил жетон следа. */
    public final int playerId;

    /** ID динозавра, к цепочке которого относится жетон. */
    public final int dinosaurId;

    /** Номер попытки в текущей цепочке выслеживания. */
    public final int stepNumber;

    /** Клетка, на которой лежит жетон следа. */
    public Point position;

    /** Направление стрелки следа. */
    public Direction direction;

    /** true, если жетон сейчас лежит на карте. */
    public boolean active = true;

    /** true, если жетон отмечает уже обездвиженного динозавра до вывоза водителем. */
    public boolean captureMarker;

    /**
     * Создаёт жетон следа на карте.
     *
     * @param playerId владелец жетона
     * @param dinosaurId динозавр, которого выслеживают
     * @param position клетка жетона
     * @param direction направление следа
     * @param stepNumber номер попытки
     * @param captureMarker является ли жетон маркером пойманного динозавра
     */
    public TrailToken(
            int playerId,
            int dinosaurId,
            Point position,
            Direction direction,
            int stepNumber,
            boolean captureMarker
    ) {
        this.playerId = playerId;
        this.dinosaurId = dinosaurId;
        this.position = position;
        this.direction = direction == null ? Direction.NORTH : direction;
        this.stepNumber = stepNumber;
        this.captureMarker = captureMarker;
    }
}
