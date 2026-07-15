# MIMOO — ANEXO HITO 11
# Canales — Suscripciones y Descarga Automática

*Vive en `DOCS/ANNEX_H11.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (S011, 2026-07-15)

Hito nuevo, abierto a petición explícita de Miguel Ángel al aclarar
qué significaba "Canal"/"Canales" en los niveles 9-10 de H10 (compartir).
Cita textual: *"canal en Mimo significa lo mismo que canal en YouTube,
exactamente igual. Son suscripciones y búsqueda de canales para
suscribirse y descargar contenido de esos canales para verlo cuando se
quiera. Es como un guardado de podcast."*

Es decir: **no es un filtro nuevo sobre datos ya existentes** (como sí
lo eran los niveles 2-8 de H10) — es una funcionalidad completamente
nueva que hoy no existe en MiMoo: suscribirse a un canal de YouTube y
que la app vaya descargando en segundo plano el contenido nuevo que
publique, sin que Miguel Ángel tenga que ir a buscarlo cada vez.

Una vez construido este hito, H10 niveles 9-10 (compartir un canal /
varios canales) pasan a tener sentido: compartir la suscripción para
que el receptor la reciba y empiece a recibir el mismo contenido.

---

## Punto de partida real ya construido (H08 PARTE 1)

**No arrancar desde cero** — ya existe búsqueda de canales de YouTube
por texto libre, vía el filtro nativo de YouTube (`sp=EgIQAg%3D%3D`),
en `link_resolver.search_by_type()` (Python) /
`ExternalLinkResolver.searchYoutube()` con el DTO `SearchTypeResult`
(`id`, `title`, `url`, `subtitle`, `thumbnailUrl`). Hoy, tocar un
resultado de canal abre "Importar enlace" con la URL resuelta -- un
uso puntual, manual, sin persistencia. Este hito reutiliza esa misma
búsqueda como punto de entrada para "Suscribirse", en vez de
construir una búsqueda de canales nueva.

---

## Diseño (asunciones tomadas al escribir esto, a confirmar/corregir con Miguel Ángel antes de darlas por buenas)

1. **Contenido descargado en audio, igual que el resto de MiMoo.**
   Miguel Ángel dijo "verlo", pero MiMoo es una app de audio de
   principio a fin (yt-dlp `bestaudio`, Opus) -- se asume que los
   canales se tratan igual, como "guardado de podcast" (que es
   literalmente audio). Si en realidad quiere vídeo, es un cambio de
   alcance mucho mayor (reproductor de vídeo, otro formato de
   descarga) que habría que hablar aparte.
2. **Descarga automática = de verdad automática, no una lista de
   "pendientes por confirmar".** Mismo comportamiento que un cliente
   de podcasts: al detectar contenido nuevo de un canal suscrito, se
   encola la descarga sola (`DownloadQueueManager.enqueue()`, ya
   construido), sin pedir confirmación por vídeo.
3. **Comprobación periódica en segundo plano vía WorkManager**, mismo
   mecanismo que ya usa el proyecto para descargas (`DownloadWorker`)
   -- un `PeriodicWorkRequest` que recorre los canales suscritos,
   consulta sus últimos vídeos (yt-dlp con `extract_flat` sobre la URL
   del canal, ligero, sin descargar nada todavía) y encola solo los
   `youtubeId` que no existan ya en `search_result_tracks`.
   Periodicidad de partida: una vez al día (ajustable si Miguel Ángel
   quiere otra cosa).
4. **Sin límite de "cuántos vídeos atrás" al suscribirse** -- asunción
   más simple y más parecida a un podcast real: al suscribirse, se
   descarga el contenido más reciente (p.ej. los últimos N, número a
   decidir) y a partir de ahí solo lo nuevo. Suscribirse a un canal
   con miles de vídeos y descargarlos todos de golpe sería
   probablemente indeseado, pero esto es una asunción a confirmar.
5. **Nueva entidad Room** `ChannelSubscription` (channelId de
   YouTube como clave real, title, thumbnailUrl, subscribedAt) --
   igual patrón que `FavoriteRadioStation`/`FavoriteAlbum`: concepto
   propio, no forzado dentro de `SearchResultTrack`.

---

## Lo que queda por confirmar con Miguel Ángel antes de cerrar del todo

1. ¿Audio (como el resto de MiMoo) o de verdad quiere vídeo? (asunción 1 arriba)
2. ¿Cuántos vídeos atrás descargar al suscribirse por primera vez a un canal? ¿O ninguno, solo lo que se publique a partir de ahí?
3. ¿Notificación cuando llega contenido nuevo de un canal suscrito, o simplemente aparece descargado sin avisar?
4. ¿Los "Shorts" de un canal cuentan como contenido a descargar, o se filtran?

---

## COMPLETADAS EN S011

PASOS 1-4 construidos en la misma sesión que se abrió el hito:
entidad/persistencia, suscribirse desde la búsqueda de canales ya
existente (H08 PARTE 1), pantalla "Canales" (ver/reproducir/dar de
baja), y comprobación periódica en segundo plano con descarga
automática de contenido nuevo (sin descargar el catálogo histórico al
suscribirse). Ver el detalle real en cada PASO más abajo.

Solo queda **PASO 5, verificación en dispositivo real** -- nada de
esto se ha probado fuera de que compile.

---

## Hoja de ruta

**PASO 1 — Entidad y persistencia.** `ChannelSubscription` (Room),
`ChannelSubscriptionDao`, `ChannelSubscriptionRepository` -- mismo
patrón que `FavoriteRadioStation`/`FavoriteAlbum`. Migración de Room
nueva.

**PASO 2 — Suscribirse desde la búsqueda ya existente.** Añadir botón
"Suscribirse" a los resultados de tipo Canal de H08 PARTE 1
(`SearchTypeResult`), junto al "Importar enlace" que ya existe --
inserta una fila en `ChannelSubscription`.

**PASO 3 — Pantalla "Canales".** Nueva entrada de navegación: lista de
canales suscritos, cada uno con su contenido descargado (reutilizando
la agrupación por `channelTitle`, similar a como Biblioteca agrupa
por `artist`), opción de darse de baja.

**PASO 4 — Comprobación periódica en segundo plano.**
`ChannelCheckWorker` (WorkManager, `PeriodicWorkRequest`): para cada
canal suscrito, consulta sus últimos vídeos vía yt-dlp
(`extract_flat`, sin descargar audio), filtra los `youtubeId` que ya
existan en `search_result_tracks`, y encola el resto vía
`DownloadQueueManager.enqueue()`. Releer `DownloadWorker.kt` (§4.1)
antes de replicar su patrón de registro/gestión de WorkManager.
**Completado en S011:** reutiliza `ExternalLinkResolver.resolveLink()`
(H08 PARTE 1) tal cual contra `https://www.youtube.com/channel/{id}/videos`
-- sin Python nuevo. Primera comprobación de un canal: guarda sus
vídeos actuales como línea base (`PENDING`, sin encolar) en vez de
descargar el catálogo entero de golpe. Periodicidad: una vez al día,
registrado en `MiMooApp.onCreate()` con `ExistingPeriodicWorkPolicy.KEEP`.

**PASO 5 — Verificación en dispositivo real.** Confirmar que
suscribirse funciona, que el contenido inicial se descarga según lo
que se decida en el punto 2 de "lo que queda por confirmar", y que la
comprobación periódica detecta contenido nuevo de verdad sin
duplicar descargas.

---
