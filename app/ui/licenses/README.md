# Bundled typefaces

Three faces ship inside the app, in `src/commonMain/composeResources/font/`.
All are under the SIL Open Font License 1.1, whose text is beside this file, one
copy per family. The OFL permits bundling and redistribution inside an
application; it requires the licence to travel with the fonts, which is what
these files are for.

The licences live here rather than next to the `.ttf` files because everything
in a `composeResources/font/` directory is generated into the `Res.font`
accessor class, and a `.txt` in there is not a font.

| Family | Used for |
|---|---|
| **Instrument Sans** | Everything you read. A grotesque, and a tight one — Yu-Gi-Oh! card names are long and deck panes are narrow, so width is a feature. |
| **JetBrains Mono** | Counts, ATK/DEF, levels, odds. The tool this replaces used it the same way, tracked in tight, and called it `font-mono-tactical`. |
| **Tektur** | The wordmark, and nothing else. |

The original asked for `'Helvetica Neue Haas Grotesk'` in its stylesheet and
never loaded it, so every install fell back to Inter — which the project's own
guidelines rule out. Instrument Sans is that intent, actually shipped.
