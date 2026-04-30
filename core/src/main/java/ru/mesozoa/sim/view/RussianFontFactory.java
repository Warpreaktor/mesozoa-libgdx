package ru.mesozoa.sim.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Генератор BitmapFont с поддержкой кириллицы.
 *
 * Ожидаемое имя файла:
 * NotoSans-Regular.ttf
 *
 * Поддерживаемые варианты расположения:
 * 1) <project-root>/assets/fonts/NotoSans-Regular.ttf
 * 2) <project-root>/core/src/main/resources/assets/fonts/NotoSans-Regular.ttf
 * 3) <working-dir>/fonts/NotoSans-Regular.ttf
 * 4) <working-dir>/assets/fonts/NotoSans-Regular.ttf
 *
 * Сделано специально терпимым к Gradle workingDir, потому что Gradle,
 * как водится, любит делать вид, что текущая папка — это философский вопрос.
 */
public final class RussianFontFactory {
    private static final String DEFAULT_FONT_PATH = "fonts/NotoSans-Regular.ttf";

    private static final String CYRILLIC_CHARS =
            FreeTypeFontGenerator.DEFAULT_CHARS
                    + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
                    + "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
                    + "ІіЇїЄєҐґ"
                    + "«»—–…№"
                    + "₽"
                    + "→←↑↓"
                    + "×"
                    + "·";

    private RussianFontFactory() {
    }

    public static BitmapFont create(int size) {
        return create(DEFAULT_FONT_PATH, size);
    }

    public static BitmapFont create(String fontPath, int size) {
        FileHandle fontFile = resolveFontFile(fontPath);
        if (fontFile == null) {
            Gdx.app.error(
                    "RussianFontFactory",
                    "Font file not found. user.dir=" + System.getProperty("user.dir")
                            + ". Expected NotoSans-Regular.ttf in assets/fonts/ or core/src/main/resources/assets/fonts/."
            );
            return new BitmapFont();
        }

        Gdx.app.log("RussianFontFactory", "Using font: " + fontFile.path());

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameter =
                    new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = size;
            parameter.characters = CYRILLIC_CHARS;
            parameter.color = Color.WHITE;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.incremental = false;

            BitmapFont font = generator.generateFont(parameter);
            font.getData().markupEnabled = true;
            return font;
        } finally {
            generator.dispose();
        }
    }

    private static FileHandle resolveFontFile(String fontPath) {
        List<FileHandle> candidates = new ArrayList<>();

        // Если workingDir = root/assets
        candidates.add(Gdx.files.internal(fontPath));

        // Если workingDir = root
        candidates.add(Gdx.files.internal("assets/" + fontPath));

        // Если workingDir = lwjgl3
        candidates.add(Gdx.files.internal("../assets/" + fontPath));

        // Если workingDir = lwjgl3/build/something или IDE чудит
        candidates.add(Gdx.files.internal("../../assets/" + fontPath));

        // Если шрифт лежит в core/src/main/resources/assets/fonts
        candidates.add(Gdx.files.internal("core/src/main/resources/assets/" + fontPath));
        candidates.add(Gdx.files.internal("../core/src/main/resources/assets/" + fontPath));

        // Если Gradle/IDE положил resources в classpath
        candidates.add(Gdx.files.classpath(fontPath));
        candidates.add(Gdx.files.classpath("assets/" + fontPath));

        // На всякий случай local тоже проверим.
        candidates.add(Gdx.files.local(fontPath));
        candidates.add(Gdx.files.local("assets/" + fontPath));
        candidates.add(Gdx.files.local("../assets/" + fontPath));
        candidates.add(Gdx.files.local("core/src/main/resources/assets/" + fontPath));

        StringBuilder checked = new StringBuilder();
        for (FileHandle candidate : candidates) {
            checked.append(candidate.path()).append("\n");
            if (candidate.exists()) {
                Gdx.app.log("RussianFontFactory", "Font candidate found: " + candidate.path());
                return candidate;
            }
        }

        Gdx.app.error("RussianFontFactory", "Checked font paths:\n" + checked);
        return null;
    }
}
