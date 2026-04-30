package ru.mesozoa.sim.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.Map;

public final class AssetCatalog {
    private final Map<String, Texture> textures = new HashMap<>();

    public Texture get(String path) {
        if (path == null) return null;
        if (textures.containsKey(path)) return textures.get(path);
        if (!Gdx.files.internal(path).exists()) {
            textures.put(path, null);
            return null;
        }
        Texture texture = new Texture(Gdx.files.internal(path));
        textures.put(path, texture);
        return texture;
    }

    public void dispose() {
        for (Texture texture : textures.values()) {
            if (texture != null) texture.dispose();
        }
        textures.clear();
    }
}
