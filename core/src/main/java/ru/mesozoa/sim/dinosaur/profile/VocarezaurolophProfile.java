package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

import java.util.List;

/** Видовой профиль Вокарезауролофа*/
public final class VocarezaurolophProfile extends AbstractBioTrailDinosaurProfile {
    public VocarezaurolophProfile() {
        super(Species.VOCAREZAUROLOPH, List.of(Biome.RIVER, Biome.MEADOW, Biome.BROADLEAF_FOREST, Biome.FLOODPLAIN));
    }
}
