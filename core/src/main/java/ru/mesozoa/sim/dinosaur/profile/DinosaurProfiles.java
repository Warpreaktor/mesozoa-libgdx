package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Species;

import java.util.EnumMap;
import java.util.Map;

/**
 * Реестр видовых профилей динозавров.
 */
public final class DinosaurProfiles {

    private static final Map<Species, DinosaurProfile> PROFILES = createProfiles();

    private DinosaurProfiles() {
    }

    /**
     * Возвращает видовой профиль динозавра.
     *
     * @param species вид динозавра
     * @return профиль вида
     */
    public static DinosaurProfile profile(Species species) {
        DinosaurProfile profile = PROFILES.get(species);
        if (profile == null) {
            throw new IllegalArgumentException("No dinosaur profile for species: " + species);
        }
        return profile;
    }

    private static Map<Species, DinosaurProfile> createProfiles() {
        EnumMap<Species, DinosaurProfile> profiles = new EnumMap<>(Species.class);
        register(profiles, new GallimimonProfile());
        register(profiles, new DriornisProfile());
        register(profiles, new CryptognathProfile());
        register(profiles, new VelocitaurusProfile());
        register(profiles, new MonoceratusProfile());
        register(profiles, new VocarezaurolophProfile());
        return Map.copyOf(profiles);
    }

    private static void register(EnumMap<Species, DinosaurProfile> profiles, DinosaurProfile profile) {
        profiles.put(profile.species(), profile);
    }
}
