package ru.mesozoa.sim.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AssetCatalog {
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, Boolean> missingLogged = new HashMap<>();

    public Texture get(String path) {
        if (path == null || path.isBlank()) return null;

        if (textures.containsKey(path)) {
            return textures.get(path);
        }

        FileHandle file = resolve(path);
        if (file == null) {
            textures.put(path, null);
            logMissingOnce(path);
            return null;
        }

        Texture texture = new Texture(file);
        textures.put(path, texture);
        Gdx.app.log("AssetCatalog", "Loaded texture: " + file.path());
        return texture;
    }

    private FileHandle resolve(String path) {
        List<FileHandle> candidates = new ArrayList<>();

        candidates.add(Gdx.files.internal(path));
        candidates.add(Gdx.files.internal("assets/" + path));
        candidates.add(Gdx.files.internal("../assets/" + path));
        candidates.add(Gdx.files.internal("../../assets/" + path));

        candidates.add(Gdx.files.internal("core/src/main/resources/assets/" + path));
        candidates.add(Gdx.files.internal("../core/src/main/resources/assets/" + path));

        candidates.add(Gdx.files.classpath(path));
        candidates.add(Gdx.files.classpath("assets/" + path));

        candidates.add(Gdx.files.local(path));
        candidates.add(Gdx.files.local("assets/" + path));
        candidates.add(Gdx.files.local("../assets/" + path));
        candidates.add(Gdx.files.local("../../assets/" + path));

        for (FileHandle candidate : candidates) {
            if (candidate.exists()) {
                return candidate;
            }
        }

        return null;
    }

    private void logMissingOnce(String path) {
        if (missingLogged.putIfAbsent(path, true) != null) {
            return;
        }

        Gdx.app.error(
                "AssetCatalog",
                "Texture not found: " + path
                        + ". user.dir=" + System.getProperty("user.dir")
                        + ". Expected: assets/" + path
        );
    }

    public void dispose() {
        for (Texture texture : textures.values()) {
            if (texture != null) texture.dispose();
        }
        textures.clear();
        missingLogged.clear();
    }
}
