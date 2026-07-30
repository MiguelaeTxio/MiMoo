# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S026 (2026-07-30)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

La hoja de ruta técnica completa está en `DOCS/ANNEX_H08.md`, sección
"Hoja de Ruta para la Siguiente Sesión que retome H08" al final del
archivo. En orden de prioridad:

1. **Modal "¿Quién es el artista?"** -- diseño CERRADO con Miguel
   Ángel en S026, sin construir todavía. Empezar la sesión leyendo ese
   diseño completo en el anexo (disparadores, qué pasa si no se
   responde, persistencia, "si no hay artista no hay ancla") y
   preguntando la única decisión que quedó sin cerrar: si la sección
   de Canales se mantiene o se retira también. Después, barrido de
   los 16 ficheros que hoy usan `track.artist ?: track.channelTitle`
   (lista completa en el anexo).

2. **Verificación en dispositivo real del tramo final de S026**: los
   cuatro grupos de origen (`OriginGroup`,
   Hispanoamérica/Anglosajona/Europea/Mundial) y la salvaguarda
   "conocido en España" para Exploración. Todo lo anterior de S026 sí
   se probó en dispositivo real durante la propia sesión (así se
   encontraron Supertramp, Queen/Pink Floyd, "Free" con Nacho, el
   fichero mal nombrado, el fallo de Discogs, y Portugal metiéndose
   donde no debía) -- solo falta confirmar este último tramo.

3. **La semilla de 1.161 artistas** (`anchor_artists.json`) se escribió
   a mano, sin contraste con ninguna fuente externa. S026 encontró y
   corrigió un caso real (`progressive rock` en Led Zeppelin, causa de
   tres colados falsos seguidos) -- sigue pendiente auditar el resto.
   No se puede verificar contra MusicBrainz en vivo desde este entorno
   de trabajo (sin acceso de red a `musicbrainz.org`).

4. Pendiente de sesiones anteriores, sin tocar en S026:
   - Verificación física de sincronización entre dos dispositivos (H07,
     PASO 5).
   - Resto de la hoja de ruta original de H13 (PAUSADO): auditoría
     completa de los botones de `PlayerBar.kt`, y cierre del alcance
     de "aspecto de algunos ítems".

## Incidencias de proceso a tener en cuenta

- El zip de logs de GitHub Actions no es accesible por red desde el
  entorno de trabajo del modelo. El paso del workflow que publica los
  errores de compilación como anotaciones del check (legibles por
  API) sigue siendo el método fiable -- mantenerlo.
- S026 tuvo un fallo de compilación real y corregido en la propia
  sesión (commit `76aec87` roto, corregido en `3d3a199`): una
  referencia a un parámetro (`country`) que se había retirado de una
  función, pero un `log()` dentro de un `catch` seguía usándolo.
  Revisar siempre con `grep` los nombres de parámetros retirados antes
  de dar un cambio de firma por completo, no solo los sitios donde se
  llama a la función.
- S026 corrigió dos veces el mismo diseño de origen (grupos, luego
  Portugal/Brasil) tras probarlo en dispositivo real. Patrón sano:
  cerrar el diseño con Miguel Ángel, implementarlo, y estar preparado
  para que el propio dispositivo real lo corrija -- no es un fallo de
  diseño previo, es cómo se afina algo que no se puede predecir del
  todo sin escucharlo.
