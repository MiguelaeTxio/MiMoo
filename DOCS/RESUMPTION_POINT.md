# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S023 (2026-07-26)
**Hito EN PROGRESO:** H08 — Radio (`DOCS/ANNEX_H08.md`)

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Dónde se quedó S023

El bloque de géneros de H08 quedó **cerrado y verificado en
dispositivo**. Arrancó de una observación de Miguel Ángel: el cruce de
géneros metía en el mismo saco `new wave` y `dark wave` por parecerse
el nombre, y así se acabaría poniendo reguetón con reggae.

Lo que había de verdad era peor que una comparación por nombre: eran
22 sacos de géneros escritos por el modelo. Se sustituyeron por la
taxonomía real de MusicBrainz.

**Regla que gobierna ahora la pertenencia**, cerrada por Miguel Ángel
con su analogía de la taxonomía animal — oso hormiguero y oso polar
comparten ancestro y no son parientes:

1. Intersección directa sobre un género **concreto**.
2. Descenso desde el ancla, nunca ascenso, y solo desde carpetas
   contenidas.
3. Hermanos bajo un padre concreto, como último peldaño.

Nunca se sube al padre y nunca se recorren aristas de influencia.

**Segunda regla suya, igual de importante:** la década la marca el
TEMA, nunca el artista.

---

## Estado real del código y los datos

- `genre_tree.json` — 2176 géneros con parentesco real, dentro del APK.
- `known_hit_artists.json` — 621 de 777 entradas (79,9%) con conjunto
  real de géneros.
- `GENRE_FAMILIES` **eliminado**. `matchesGenre()` cruza contra
  `GenreTree`.
- La década del ancla sale de `resolveTrackDecade()`: diccionario →
  `first-release-date` de MusicBrainz → sin fijar.
- `resolveAnchor()` y `lookupArtistProfile()` comprueban que el
  artista devuelto sea el buscado antes de aceptarlo.
- El cupo DISCO ya no se agota por fallos de red.

Verificado en dispositivo: una radio de P!nk pasó de Cat Stevens y
Lynyrd Skynyrd (los 70, del nacimiento de la artista) a The Killers,
Keane, Kings Of Leon y Kaiser Chiefs.

---

## Próximos pasos

Ver **"Hoja de Ruta para la Siguiente Sesión que retome H08"** en
`DOCS/ANNEX_H08.md`. En orden: ampliar el diccionario (lo más antiguo
pendiente del hito), cerrar las fuentes de fecha por artista que
quedan, meter el árbol en `sharesGenreWith()`, comprobar si los cinco
artistas sin MBID ya se resuelven con el límite de búsqueda subido a
25, y la verificación en dispositivo del modo degradado.

---

## Pendiente de Miguel Ángel, no técnico

- **Umbral de descenso.** `MAX_DESCENDANTS_TO_DESCEND = 25` sale de
  medir el árbol, no de la intuición. Pero la radio de P!nk tira hacia
  el indie/rock de los 2000 más que hacia el pop, porque su género
  principal es `pop`, la carpeta raíz. Queda a su juicio, tras
  escuchar más radios, si eso se ajusta y dónde.

- **Pantalla en blanco al navegar.** Reportada al final de S023 y
  ajena a H08 — no se tocó interfaz. Al entrar en Ajustes el hueco del
  NavGraph queda vacío y se recupera solo abriendo el menú lateral.
  Decidió no perseguirla ("déjalo, se recupera, no hay problema").
  Documentada en `ANNEX_H08.md` con lo que la evidencia descarta.
  Encaja en H13.
