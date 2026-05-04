package ru.mesozoa.sim.model;

/**
 * Стабильный идентификатор вида динозавра.
 *
 * Видовые игровые параметры живут в {@link ru.mesozoa.sim.dinosaur.Dinosaur}:
 * так конкретная фишка хранит ловкость, размер, рацион, метод поимки и био-тропу
 * как часть своего состояния. Enum остаётся компактным ID для заданий, спаунов,
 * UI и сохранения ссылок на вид. Потому что если enum начнёт снова хранить всё,
 * мы опять получим два источника правды и маленький архитектурный палеонтологический ад.
 */
public enum Species {
    GALLIMIMON("Галлимимон", "G", "gallimimon.png"),
    DRIORNIS("Дриорнис", "D", "driornis.png"),
    CRYPTOGNATH("Криптогнат", "K", "cryptognath.png"),
    VELOCITAURUS("Велоцитаурус", "V", "velocitaurus.png"),
    MONOCERATUS("Моноцератус", "M", "monoceratus.png"),
    VOCAREZAUROLOPH("Вокарезауролоф", "W", "vocarezauroloph.png");

    /** Отображаемое имя вида. */
    public final String displayName;

    /** Короткий код вида для компактных подписей. */
    public final String shortCode;

    /** Имя файла изображения фишки динозавра. */
    public final String imagePath;

    Species(String displayName, String shortCode, String imagePath) {
        this.displayName = displayName;
        this.shortCode = shortCode;
        this.imagePath = imagePath;
    }
}
