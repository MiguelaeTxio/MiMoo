# MIMOO — PUNTO DE REANUDACIÓN (NewFlow Android)

*Vive en `DOCS/RESUMPTION_POINT.md`. Sustituye a la bitácora de
sesión que antes era una skill (`doc-session-log-mimoo`) — con
NewFlow, cada paso ya queda registrado como un commit individual con
mensaje detallado en el propio `git log`, así que este archivo es
solo un resumen corto de "dónde estamos" y "qué sigue", no un diario
completo. Para el detalle exacto de qué cambió en cada paso, consultar
`git log` directamente sobre el repo clonado.*

---

## Última actualización: 2026-07-12 (cierre de sesión S009 NewFlow)

**Hito EN PROGRESO: H09 — SHOUTcast, Radios Online del Mundo por
Género/Tema/Década** (`DOCS/ANNEX_H09.md`, recién abierto, PCH durante
esta misma sesión). Ver `DOCS/MASTER_DOCUMENT.md` para la tabla
completa. **H08 queda pausado — Radio confirmada funcionando en
dispositivo real; búsqueda de listas/canales construida, pendiente
solo de confirmación final** (ver abajo).

**S009 en resumen — sesión larga, H08 cerrado de verdad (las dos
partes) y H09 abierto y documentado, sin código todavía:**

1. **Investigación previa (antes de tocar código):** verificado que
   con un token de fine-grained PAT con permiso `Actions: Read` se
   pueden leer los logs de GitHub Actions directamente vía API REST
   (`api.github.com` en dominios permitidos) — ya documentado en
   `android-build`/`newflow-android-token`, es el método por defecto
   desde esta sesión.
2. **H08 PARTE 1 — corrección de alcance real.** Primer intento
   (filtro sobre playlists *locales*) fue un malentendido — el
   encargo real era buscar listas y canales creados por **otros
   usuarios** en YouTube. El filtro local se conservó como mejora
   aparte (bienvenida, no se revirtió). Implementado el alcance
   correcto: búsqueda filtrada por Listas/Canales vía los filtros
   nativos y gratuitos de YouTube (`sp=`), verificados con captura
   real de la app de YouTube; podcasts/audiolibros descartados (sin
   filtro nativo). Tocar un resultado abre "Importar enlace" con la
   URL ya resuelta, reutilizando el 100% de ese flujo.
3. **H08 PARTE 2 — Radio, diseño cerrado y construido, con tres
   rondas de corrección real tras pruebas en dispositivo:**
   - Diseño: se dispara al terminar la última canción sin cíclico;
     fuente del "relacionado" MusicBrainz (géneros compartidos),
     descartado el Mix de YouTube por inestabilidad documentada de
     yt-dlp; solo streaming, nunca descarga.
   - 1ª corrección: el autoplay no arrancaba solo (hacía falta
     `prepare()`, no solo `play()`, para salir de `STATE_ENDED`); y
     una sola pista no era "radio" — rediseñado a mantener hasta 10
     pistas por delante, reponiendo una al terminar cada una.
   - 2ª corrección: el autoplay *seguía* sin funcionar de verdad —
     causa de fondo: reanudar desde `STATE_ENDED` es frágil. Fix
     real: la reposición se dispara *proactivamente* en cuanto
     empieza a sonar la última pista (no reactivamente al llegar al
     final), para que ExoPlayer nunca llegue a `STATE_ENDED` en el
     camino normal. De paso, detectado por el propio modelo (no
     reportado): varios "temas" añadidos eran vídeos de "Greatest
     Hits Full Album" de 1-2 horas — corregido con filtro de
     duración + palabras clave de compilación.
   - 3ª corrección: la Radio se paraba a los 3-4 temas y no
     reponía más. Causa raíz: "Various Artists" (entidad de
     MusicBrainz sin géneros propios) se coló como "relacionado" y
     mató la cadena. Corregido: excluida explícitamente, más un
     respaldo (reintento desde el artista que arrancó la Radio si
     algún otro eslabón futuro resulta ser un callejón sin salida).
   - **Confirmado funcionando por Miguel Ángel en dispositivo real**
     tras la tercera corrección.
4. **Dos arreglos del reproductor, sin hito concreto:** indicador
   "Streaming"/"Local" con icono en vez de frase larga (se recortaba
   en pantallas estrechas); notificación ahora abre la app al tocarla
   (le faltaba `MediaSession.setSessionActivity()`). De paso se
   añadieron tiempo transcurrido/restante + barra de progreso
   arrastrable al `PlayerBar`, y el botón "anterior" ahora siempre
   visible (reinicia la pista actual si no hay una anterior real).
5. **H09 abierto (PCH CASO D).** Investigado el "Shoutcast" real
   (requiere clave de desarrollador de iHeartMedia, con
   aprobación/condiciones de marca) vs **Radio-Browser.info**
   (directorio comunitario gratuito, sin clave, +25.000 emisoras) —
   elegido este último, decisión confirmada explícitamente por Miguel
   Ángel tras preguntar por el coste real del Shoutcast (aclarado que
   su cuenta de Google Play Console no tiene ninguna relación con
   ello). Ver `DOCS/ANNEX_H09.md` para los endpoints/campos reales
   verificados y la hoja de ruta en 5 pasos. **Sin código escrito
   todavía en este hito.**

**Siguiente sesión — orden sugerido:**
1. H09 PASO 1 en adelante (capa de red → repositorio → UI →
   navegación → verificación), ver `DOCS/ANNEX_H09.md`. Dos cosas a
   confirmar con Miguel Ángel antes de programar la UI (PASO 3): el
   nombre visible en la app (propuesto "Radios Online" en vez de
   "SHOUTcast", que es jerga técnica) y qué mostrar en la barra de
   progreso cuando suena una radio en directo sin duración real.
2. H08 PARTE 1 (búsqueda de listas/canales): pendiente de que Miguel
   Ángel la pruebe en dispositivo real y confirme si funciona bien —
   no se ha reportado ningún problema, pero tampoco confirmación
   explícita como sí la hubo para Radio.

**Pendientes antiguos, sin tocar en S009, no bloquean nada:**
- H03 PASO 8 y H04 PASO 6 (verificación funcional en dispositivo).
- H05 PASO 6c (Lou Reed, búsqueda por artista/título suelto, Importar
  enlace) — pausado.
- H06 (Importar desde Drive) — implementado, verificación pendiente.
- Decisión de producto pendiente: ¿menú de configuración para
  tema/color de la app? Sin decisión tomada.

