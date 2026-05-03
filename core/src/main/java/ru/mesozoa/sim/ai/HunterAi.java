package ru.mesozoa.sim.ai;

import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * AI-оценка полезности охотника для очередной активации игрока.
 *
 * Охотник занимается только теми динозаврами, которые ловятся через охоту
 * или выслеживание. Ловушечные цели относятся к инженеру и здесь не учитываются.
 */
public final class HunterAi {

    /**
     * Текущая симуляция, из которой AI читает состояние карты, динозавров,
     * игроков и конфигурации механик.
     */
    private final GameSimulation simulation;

    /**
     * Создаёт AI-оценщик охотника.
     *
     * @param simulation текущая симуляция игры
     */
    public HunterAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Рассчитывает вес активации охотника для текущего игрока.
     *
     * Чем выше вес, тем вероятнее планировщик выберет охотника
     * следующей активируемой ролью.
     *
     * @param player игрок, для которого оценивается полезность охотника
     * @return числовая оценка полезности охотника
     */
    public AiScore scoreHunter(PlayerState player) {
        Optional<Dinosaur> target = nearestNeededHunterTarget(player, player.hunter);

        if (target.isEmpty()) {
            return new AiScore(10.0, "нет нужной цели для охоты или выслеживания");
        }

        Dinosaur dinosaur = target.get();
        int distance = player.hunter.manhattan(dinosaur.position);

        double score = 75.0 - Math.min(45.0, distance * 5.0);
        String reason = "цель " + dinosaur.species.displayName
                + ", способ " + dinosaur.species.captureMethod
                + ", расстояние " + distance;

        if (dinosaur.species.captureMethod == CaptureMethod.TRACKING && distance <= 1) {
            score += 55.0;
            reason += ", цель рядом для выслеживания";
        }

        if (dinosaur.species.captureMethod == CaptureMethod.HUNT && distance <= 2 && player.hunterBait > 0) {
            score += 55.0;
            reason += ", цель в радиусе охоты и есть приманка";
        }

        if (dinosaur.species.captureMethod == CaptureMethod.HUNT && player.hunterBait <= 0) {
            score -= 40.0;
            reason += ", но нет приманки";
        }

        return new AiScore(score, reason);
    }

    /**
     * Ищет ближайшего нужного игроку динозавра, которого может ловить охотник.
     *
     * TRAP-цели здесь намеренно не учитываются, потому что такие динозавры
     * должны обрабатываться инженером через ловушки.
     *
     * @param player игрок, для которого ищется цель
     * @param from позиция охотника
     * @return ближайшая подходящая цель или Optional.empty(), если цели нет
     */
    private Optional<Dinosaur> nearestNeededHunterTarget(PlayerState player, Point from) {
        EnumSet<CaptureMethod> allowedMethods = EnumSet.of(
                CaptureMethod.TRACKING,
                CaptureMethod.HUNT
        );

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.manhattan(from)));
    }
}