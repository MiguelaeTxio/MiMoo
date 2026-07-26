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

## La sesión se reabrió tras el primer cierre

Al probar en dispositivo aparecieron tres fallos más, todos sobre **de
dónde sale el artista que ancla la Radio**:

- El ancla salía del **nombre del canal de YouTube**. "Radio Futura -
  Divina" se ancló en *Kurt Cobain*, el canal que subió el vídeo. La
  cascada pasa a ordenarse por fiabilidad de la FUENTE: artista
  estructurado → título → canal.
- Un ancla cuyo conjunto entero fuese una carpeta raíz (`rock`)
  **agotaba el diccionario entero**. Cuarto peldaño añadido.
- Un título sin guion (`Led Zeppelin Immigrant song`) no se sabía
  leer, y la sesión acababa anclada en un artista sorteado al azar.
  Idea de Miguel Ángel, implementada: partir el título por palabras y
  probar prefijos del más largo al más corto.

Verificado en dispositivo: una radio desde `Led Zeppelin Immigrant
song` ancla en Led Zeppelin, hard rock, GB, 1970, y sirve Black
Sabbath, Genesis y Lou Reed.

---

## Próximos pasos — OBJETIVO ÚNICO: que la Radio no se agote

Acordado con Miguel Ángel al cierre. Ver la hoja de ruta completa en
`DOCS/ANNEX_H08.md`.

**El diagnóstico ya está hecho y medido, la próxima sesión no tiene que
volver a investigarlo.** El diccionario NO está corto de volumen (777
entradas, 104-116 por década, por encima del objetivo). Lo que falta es
dato de género en el lado español:

    entradas SIN conjunto de géneros    media de géneros
    español    151 de 352  (43%)              2,7
    intl         5 de 425  ( 1%)              7,8

MusicBrainz apenas cataloga a los artistas españoles, y 81 de esas 151
llevan `pop` o `rock` a secas. Por eso una radio anclada en un español
agota: no faltan entradas, falta el dato justo donde más se necesita.

Es una sesión de DATOS, no de motor. Y la primera decisión es de Miguel
Ángel: de qué fuente sale ese género, ya que MusicBrainz no lo tiene.
El modelo no lo inventa -- fue el origen de `GENRE_FAMILIES` y de todo
el trabajo de S023.

---

## Pendiente de Miguel Ángel, no técnico

- **Umbral de descenso.** `MAX_DESCENDANTS_TO_DESCEND = 25` sale de
  medir el árbol, no de la intuición. Pero la radio de P!nk tira hacia
  el indie/rock de los 2000 más que hacia el pop, porque su género
  principal es `pop`, la carpeta raíz. Queda a su juicio, tras
  escuchar más radios, si eso se ajusta y dónde.

- **Fuente del género español.** Primera decisión de la próxima
  sesión, y no la puede tomar el modelo: MusicBrainz no cataloga a
  esos 151 artistas. Opciones a plantearle -- otra fuente pública con
  mejor cobertura española, o por lotes revisados por él, como se hizo
  con la desambiguación de S023.

- **Pantalla en blanco al navegar.** Reportada al final de S023 y
  ajena a H08 — no se tocó interfaz. Al entrar en Ajustes el hueco del
  NavGraph queda vacío y se recupera solo abriendo el menú lateral.
  Decidió no perseguirla ("déjalo, se recupera, no hay problema").
  Documentada en `ANNEX_H08.md` con lo que la evidencia descarta.
  Encaja en H13.
