package ru.mesozoa.sim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import ru.mesozoa.sim.view.RussianFontFactory;

/**
 * Создаёт минимальный Scene2D skin для HUD симулятора.
 */
public final class MesozoaSkinFactory {

    private MesozoaSkinFactory() {
    }

    /**
     * Создаёт skin с кириллическими шрифтами, цветами и простыми drawable.
     *
     * @return готовый Scene2D Skin для правой HUD-панели
     */
    public static Skin create() {
        Skin skin = new Skin();

        BitmapFont defaultFont = RussianFontFactory.create(15);
        BitmapFont smallFont = RussianFontFactory.create(13);
        BitmapFont titleFont = RussianFontFactory.create(18);

        skin.add("font-default", defaultFont, BitmapFont.class);
        skin.add("font-small", smallFont, BitmapFont.class);
        skin.add("font-title", titleFont, BitmapFont.class);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture white = new Texture(pixmap);
        pixmap.dispose();
        skin.add("white", white, Texture.class);

        skin.add("default", new Label.LabelStyle(defaultFont, new Color(0.88f, 0.90f, 0.86f, 1f)));
        skin.add("small", new Label.LabelStyle(smallFont, new Color(0.72f, 0.76f, 0.72f, 1f)));
        skin.add("title", new Label.LabelStyle(titleFont, new Color(0.98f, 0.86f, 0.50f, 1f)));
        skin.add("section", new Label.LabelStyle(defaultFont, new Color(0.60f, 0.84f, 0.60f, 1f)));
        skin.add("muted", new Label.LabelStyle(smallFont, new Color(0.52f, 0.57f, 0.55f, 1f)));
        skin.add("good", new Label.LabelStyle(defaultFont, new Color(0.52f, 0.90f, 0.55f, 1f)));
        skin.add("warning", new Label.LabelStyle(defaultFont, new Color(0.95f, 0.78f, 0.35f, 1f)));
        skin.add("danger", new Label.LabelStyle(defaultFont, new Color(0.95f, 0.46f, 0.38f, 1f)));

        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = skin.newDrawable("white", new Color(0.07f, 0.08f, 0.09f, 0.62f));
        skin.add("default", scrollPaneStyle);

        skin.add("hud-background", skin.newDrawable("white", new Color(0.075f, 0.085f, 0.095f, 0.98f)), Drawable.class);
        skin.add("card-background", skin.newDrawable("white", new Color(0.105f, 0.118f, 0.130f, 0.96f)), Drawable.class);
        skin.add("section-background", skin.newDrawable("white", new Color(0.135f, 0.152f, 0.160f, 0.96f)), Drawable.class);
        skin.add("line", skin.newDrawable("white", new Color(0.24f, 0.27f, 0.27f, 1f)), Drawable.class);

        return skin;
    }
}
