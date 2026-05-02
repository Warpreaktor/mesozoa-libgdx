# Мезозоя: LibGDX Visual Simulator

## Запуск

```bash
./gradlew lwjgl3:run
```

Windows:

```bat
gradlew.bat lwjgl3:run
```

С параметрами:

```bash
./gradlew lwjgl3:run --args="--seed 42 --speed 0.35"
```

## Управление

- `Пробел` — пауза / продолжить
- `N` — один шаг симуляции
- `R` — перезапустить текущую партию
- `+ / =` — ускорить
- `-` — замедлить
- `G` — скрыть / показать сетку
- `S` — скрыть / показать спаун-метки закрытых тайлов
- `D` — скрыть / показать отладочный слой
- `Esc` — выход

## Арт

Можно положить картинки в папки:

```text
assets/tiles/
assets/dinos/
```

Имена тайлов:
```text
broadleaf_forest.png
coniferous_forest.png
meadow.png
swamp.png
river.png
lake.png
floodplain.png
mountain.png
landing.png
closed.png
```

Имена динозавров:
```text
gallimimon.png
driornis.png
cryptognath.png
velocitaurus.png
monoceratus.png
vocarezauroloph.png
```

Если картинок нет, игра рисует цветные прямоугольники и буквенные маркеры. Потому что тестировать механику важнее, чем молиться PNG-шкам, как в Tabletop Simulator, этом храме загрузочного страдания.

## Что видно на экране

- закрытые тайлы серые;
- открытые тайлы цветные;
- спаун-тайлы помечены маленьким кодом вида;
- динозавры рисуются кругами/картинками поверх тайла;
- ловушки рисуются крестиками;
- рейнджеры рисуются квадратами;
- справа показан лог последних событий.
