# PUNTO DE REANUDACIÓN — MiMoo

**Última sesión cerrada:** S027 (2026-08-02)
**Hito con la hoja de ruta activa:** ver `DOCS/ANNEX_ROUTER.md`

> El estado de los hitos vive **exclusivamente** en
> `DOCS/ANNEX_ROUTER.md`. Este archivo no lo declara ni lo duplica.

---

## Qué hacer en la siguiente sesión

La hoja de ruta técnica completa está en `DOCS/ANNEX_H08.md`, sección
"Hoja de Ruta para la Siguiente Sesión que retome H08" al final del
archivo. En orden de prioridad, por petición explícita de Miguel
Ángel al cerrar S027:

1. **Favoritos** -- funcionalidad ya existente pero dispersa y sin
   criterio uniforme. Funciona bien para emisoras de radio online
   (ShoutCast); para artistas, álbumes, sencillos y listas de
   reproducción falta estructurarlo: doble marcado streaming+local,
   vista de favoritos ausente en Explorador y en artistas de
   Biblioteca, sin forma de ir a la discografía de un artista favorito
   ni de armar una Radio con álbumes favoritos, y Biblioteca sin
   diferenciar tipo (álbum/artista/sencillo/lista) entre sus
   favoritos. Es sesión de DISEÑO primero, como H08 y H12 -- empezar
   preguntando el alcance exacto antes de tocar código. Detalle
   completo en el anexo.

2. **Auditoría pendiente de la semilla de 1.161 artistas**
   (`anchor_artists.json`), arrastrada de varias sesiones -- sigue sin
   tocar, no se puede verificar contra MusicBrainz en vivo desde este
   entorno de trabajo.

3. **Fallo de streaming ajeno a la Radio**, encontrado pero no
   resuelto del todo: `notification_debug.txt` mostraba 50 errores
   `ERROR_CODE_IO_BAD_HTTP_STATUS`/`NETWORK_CONNECTION_FAILED`
   repartidos en muchas sesiones. Se corrigió un caso concreto
   ("Sign in to confirm you're not a bot" por falta de
   User-Agent/cookies en `resolver.py`), sin confirmar si cubre el
   resto.

4. **Bug sin localizar**: el `.txt` de log que Miguel Ángel compartía
   desde el móvil llegaba a veces con contenido viejo, distinto del
   estado real del fichero (confirmado con capturas del propio gestor
   de archivos). Mitigado indirectamente bajando `MAX_LINES`, causa
   real sin diagnosticar.

## Incidencias de proceso a tener en cuenta

- El zip de logs de GitHub Actions no es accesible por red desde el
  entorno de trabajo del modelo. El paso del workflow que publica los
  errores de compilación como anotaciones del check (legibles por
  API) sigue siendo el método fiable -- mantenerlo.
- **S027 tuvo dos fallos de compilación reales, corregidos en la
  propia sesión**: cambio de tipo de retorno de
  `findAnchorArtistMbid()`/`pickAnchorArtist()` (de `String?` a
  `AnchorArtistPick?`) dejó dos usos antiguos sin actualizar
  (`501df04`, corrige la introducción del cambio); inferencia de tipos
  ambigua en un `if/else` con `emptyList()` seguido de
  `.mapNotNull{}` -- Kotlin no consigue unificar el tipo sin variables
  explícitamente tipadas (`4c342df`). Revisar siempre TODOS los
  call-sites con `grep` tras cambiar una firma o un tipo de retorno,
  no solo el punto donde se originó el cambio.
- **El token de GitHub caducó dos veces durante S027**, una vez con
  más de un commit local sin subir a la vez. Si vuelve a pasar con
  frecuencia, puede merecer la pena revisar la vida útil del token
  fine-grained que se genera para cada sesión.
- **Patrón muy repetido en S027, útil para sesiones futuras de
  depuración de Radio**: casi todos los bugs reales de esta sesión
  fueron variaciones del MISMO patrón -- una comparación EXACTA
  (nombre de artista, título de canción, clave de diccionario) fallaba
  ante una variación real y razonable del dato (nombre corto vs.
  completo, título con la letra en vez del título real, ruido de
  YouTube). La solución que funcionó cada vez fue la misma: fallback
  por CONJUNTO DE PALABRAS, aceptado solo si el candidato es ÚNICO
  entre las alternativas disponibles -- nunca adivinar con ambigüedad.
  Si aparece un bug nuevo con esta forma (algo que "debería" encontrar
  un dato real y no lo encuentra), sospechar primero de una
  comparación demasiado estricta antes que de falta de datos.
- **Miguel Ángel corrigió una decisión propia dentro de la misma
  sesión** (bajar la ventana de no-repetir-artista de 10 a 5 en el
  último recurso) tras ver que el síntoma seguía sin explicarse del
  todo -- el diagnóstico correcto (`radioUnusableArtistsThisRound` sin
  reiniciarse por ronda) apareció poco después. Patrón sano a repetir:
  no dar un arreglo por bueno solo porque "parece" resolver el
  síntoma -- seguir pidiendo pruebas hasta que la causa quede
  confirmada con log o captura real.
