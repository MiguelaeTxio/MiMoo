# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S025 (2026-07-29)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Decisión pendiente al abrir la siguiente sesión

`ANNEX_ROUTER.md` sigue marcando H13 como el hito activo desde el
cierre de S024, pero prácticamente todo el trabajo de S025 se hizo
sobre H08 sin que se ejecutara un PCH formal — la sesión derivó hacia
Radio por la propia conversación, no por una orden explícita de
cambio de hito. Miguel Ángel debe decidir cómo se resuelve esto: PCH
formal a H08, o retomar H13 tal cual estaba.

## Qué hacer en la siguiente sesión

La hoja de ruta técnica completa está en `DOCS/ANNEX_H08.md`, sección
"Hoja de ruta para la siguiente sesión" al final del archivo. En una
línea: **verificar en dispositivo real todo lo construido en S025**
antes de dar nada por bueno.

En orden de prioridad:

1. **Verificación en dispositivo real**, ninguna parte de S025 se ha
   probado contra el teléfono de Miguel Ángel. En concreto:
   - Que el ancla de un artista de rock (p. ej. Led Zeppelin) no derive
     hacia géneros sin relación.
   - Que ningún tema se repita en una sesión de Radio, bajo ninguna
     circunstancia.
   - Que el botón "Crear base de datos" de Ajustes complete un
     recorrido sin colgar la aplicación ni ralentizar el resto de la
     interfaz.
   - Que `suggestRelatedArtist()` esté sirviendo candidatos de la base
     de datos local antes que de MusicBrainz en vivo — verificable en
     el log de Radio por la línea "DE LA BASE DE DATOS, sin red".

2. **La semilla de 1.161 artistas** (`anchor_artists.json`) se escribió
   a mano, sin contraste con ninguna fuente externa. Puede tener país o
   género equivocado en artistas concretos — revisar si aparecen
   anclajes raros.

3. **Discogs y Wikidata no se han podido probar contra la red real**
   durante S025 por falta de acceso de red al entorno de trabajo del
   modelo. Confirmar en dispositivo que ambas fuentes responden.

4. Pendiente de sesiones anteriores, sin tocar en S025:
   - Verificación física de sincronización entre dos dispositivos (H07,
     PASO 5).
   - Resto de la hoja de ruta original de H13: auditoría completa de
     los botones de `PlayerBar.kt`, y cierre del alcance de "aspecto de
     algunos ítems".

## Incidencias de proceso a tener en cuenta

- El zip de logs de GitHub Actions no es accesible por red desde el
  entorno de trabajo del modelo. Se añadió un paso al workflow
  (`build-and-deploy.yml`) que publica los errores de compilación como
  anotaciones del check, legibles por API — mantenerlo.
- S025 tuvo varias iteraciones de fallos reales del propio modelo
  (documentados con detalle en `ANNEX_H08.md`): una regla de negocio
  inventada sin que Miguel Ángel la pidiera, un fallo de MIME en SAF
  que impidió que nada persistiera durante varias vueltas, un borrado
  automático que se ejecutó en bucle, y lentitud general de la app por
  no cachear una resolución de carpeta. Todo corregido dentro de la
  propia sesión, pero conviene leer esa sección antes de tocar
  `AnchorDictionary.kt` de nuevo.
