package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/**
 * Базовая реализация видового профиля динозавра с циклической био-тропой.
 */
abstract class AbstractBioTrailDinosaurProfile implements DinosaurProfile {

    private final Species species;
    private final List<Biome> bioTrail;

    protected AbstractBioTrailDinosaurProfile(Species species, List<Biome> bioTrail) {
        this.species = species;
        this.bioTrail = List.copyOf(bioTrail);
    }

    @Override
    public Species species() {
        return species;
    }

    @Override
    public List<Biome> bioTrail() {
        return bioTrail;
    }

    @Override
    public int agility() {
        return species.agility;
    }

    @Override
    public boolean canEnter(Biome biome) {
        return biome != null && bioTrail.contains(biome);
    }

    @Override
    public Biome nextBiomeAfter(Biome currentBiome, int fallbackTrailIndex) {
        int currentIndex = trailIndexOf(currentBiome);
        if (currentIndex < 0) {
            currentIndex = Math.floorMod(fallbackTrailIndex, bioTrail.size());
        }

        return bioTrail.get((currentIndex + 1) % bioTrail.size());
    }

    @Override
    public int trailIndexOf(Biome biome) {
        for (int i = 0; i < bioTrail.size(); i++) {
            if (bioTrail.get(i) == biome) {
                return i;
            }
        }
        return -1;
    }
}
