# MIMOO — ANEXO HITO 10
# Hash de Compartición de Contenido

*Vive en `DOCS/ANNEX_H10.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (S011, 2026-07-15)

Hito nuevo, abierto a petición explícita de Miguel Ángel dentro de una
lista de varios frentes de MiMoo. Objetivo: generar un código de
compartición que, abierto desde MiMoo por otra persona (o en otro
dispositivo propio) por cualquier medio (WhatsApp, etc.), importe una
réplica de lo compartido — reutilizando los enlaces de descarga ya
resueltos, sin que el receptor tenga que rebuscar nada.

---

## Diseño cerrado en S011 (mismo día de apertura)

Cita textual del encargo original: *"Generar un hash de compartición
que se habrá con la aplicación al enviarlo por cualquier medio.
Comparte los links de descarga favoritos del disco o lista etc.
Réplica total en el repositorio de quien lo abre."*

Sobre el alcance ("¿lista de favoritos, un álbum, un sencillo, una
lista, o todo?"), respuesta de Miguel Ángel: **"Lo que se comparte
depende del que genera el hash, si es la lista de favoritos, un álbum
o un sencillo o una lista o todo. Depende de desde donde se comparta,
el nivel y el lugar."** — confirmado: no hay un único modo fijo, el
código se genera desde varios puntos de la app y su contenido depende
de desde dónde se pulsó "compartir".

Formato pedido explícitamente: **`miMoo+hash`**.

Sobre "réplica total", aclaración de Miguel Ángel: *"si hay favoritos
en lo que se comparte se comparte también de igual forma los enlaces
originales y los cambios y ediciones de nombres. Orden de las
canciones etc. El contenido se debe descargar del mismo sitio de
donde lo descargó el original."* — es decir: favoritos, `sourceUrl`,
ediciones de artista/álbum, y orden real se replican fielmente; el
receptor redescarga cada pista desde YouTube (mismo `youtubeId`), no
desde Drive ni desde ningún servidor propio de MiMoo.

Decisión técnica tomada al construir esto (no preguntada
explícitamente, pero necesaria para no ser peligrosa): la importación
de un código recibido es **siempre ADITIVA, nunca destructiva** — a
diferencia de H06 (`importDestructively()`, pensado para "mi propio
backup") y H07 (`applyCloudWinsTargeted()`, sincronización entre MIS
propios dispositivos), aquí quien abre el código es potencialmente
otra persona con su propia biblioteca, y recibir un envío no debería
poder borrarle nada.

---

## Formato del código (S011)

`"miMoo+" + Base64URL(GZIP(JSON(ShareBundle)))` — autocontenido, sin
depender de Drive ni de ningún servidor propio: todo el contenido
compartido viaja dentro del propio texto. GZIP porque un bundle de
Biblioteca completa como JSON plano sería un texto larguísimo;
Base64 URL-safe sin relleno para que sea una única línea pegable en
cualquier sitio (WhatsApp, SMS...) sin que el medio la corte o la
reformatee.

MiMoo está registrado como destino de "Compartir" de texto plano en
el sistema (`AndroidManifest.xml`, `ACTION_SEND` + `text/plain`) — el
código "se abre con la aplicación al enviarlo por cualquier medio",
tal como se pidió. Comprueba el prefijo `miMoo+` antes de intentar
decodificar nada; cualquier otro texto compartido por error se
ignora en silencio.

Ver `data/share/ShareCodeRepository.kt` (generación/decodificación),
`data/share/ShareDto.kt` (`ShareBundle`, envuelve el mismo
`BackupBundle` ya usado por H06/H07), `data/backup/
BackupImportRepository.kt` → `importSharedBundle()` (importación
aditiva), `ui/share/ShareImportViewModel.kt` (confirmación antes de
tocar el repositorio del receptor).

---

## COMPLETADAS EN S011

**Niveles 1-8 de la lista de Miguel Ángel, construidos y con punto de
entrada real en la UI (salvo donde se indica):**

1. **Biblioteca completa** — Ajustes → sección "Compartir" →
   "Compartir biblioteca completa". Reutiliza
   `BackupRepository.buildCurrentBundle()` tal cual.
2. **Artista** — botón de compartir en `ArtistList` (Biblioteca,
   pestañas Álbumes y Sencillos, ambos modos de vista), junto a
   reproducir/aleatorio/borrar.
3. **Álbum** — "Compartir con réplica total" en el menú "⋮" de cada
   fila de álbum en Biblioteca, junto al "Compartir enlace" que ya
   existía (H03/H05, comparte solo la URL de origen, sin réplica).
4. **Tema de álbum** / 6. **Sencillo** — mismo mecanismo
   (`buildSingleTrackShareCode()`), "Compartir con réplica total" en
   el menú "⋮" de cada fila de pista (Biblioteca: pestañas Álbumes,
   Sencillos y Favoritos).
5. **Sencillos favoritos** — botón en la barra superior de
   Biblioteca, visible en el nivel raíz de la pestaña Sencillos.
   Comparte las pistas favoritas sin álbum asignado
   (`isFavorite && album == null`).
7. **Listas de reproducción** / 8. **Lista de reproducción** — icono
   de compartir en la barra superior de `PlaylistDetailScreen`, para
   la lista abierta.

**Sin implementar — niveles 9 y 10, Canales/Canal:** no existe ningún
concepto de "canal" como entidad en el modelo de datos de MiMoo hoy
(`channelTitle` es solo un campo de texto en cada pista, no una
agrupación con identidad propia). Antes de construir nada aquí hace
falta una conversación de diseño con Miguel Ángel: ¿"canal" significa
agrupar por `channelTitle` igual que se agrupa por `artist`? ¿Aplica
solo a canales encontrados vía búsqueda de H08 PARTE 1, o a cualquier
`channelTitle` presente en la biblioteca?

**Sin probar en dispositivo real todavía** — Miguel Ángel confirmó
que compila y que el resto de fixes de la sesión funcionan
(descargas), pero el flujo de compartición en sí (generar código →
enviarlo → abrirlo en otro dispositivo → confirmar import) queda
pendiente de una prueba real end-to-end.

---

## Hoja de ruta para la siguiente sesión

1. Verificación en dispositivo real del flujo completo: generar un
   código desde un móvil, enviarlo por WhatsApp, abrirlo en el otro
   dispositivo, confirmar que el diálogo de importación aparece y que
   el resultado (pistas añadidas, favoritos, orden, playlist nueva)
   es el esperado.
2. Decidir con Miguel Ángel qué significa "Canal"/"Canales" (niveles
   9-10) antes de construir nada ahí.
3. Si en el uso real se detecta que el código es demasiado largo para
   compartir cómodamente por algún medio (Biblioteca completa con
   muchas pistas), revisar el formato de compresión — no antes de
   que se confirme que es un problema real.

---
