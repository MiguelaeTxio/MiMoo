# MIMOO — ANEXO HITO 10
# Hash de Compartición de Contenido

*Vive en `DOCS/ANNEX_H10.md` — flujo NewFlow Android. Ver estado en
`DOCS/ANNEX_ROUTER.md`.*

---

## NOTA DE APERTURA (S011, 2026-07-15)

Hito nuevo, abierto a petición explícita de Miguel Ángel dentro de una
lista de varios frentes de MiMoo. Objetivo: generar un hash/enlace de
compartición que, abierto desde MiMoo por otra persona (o en otro
dispositivo propio) por cualquier medio (WhatsApp, etc.), importe una
réplica de lo compartido — reutilizando los enlaces de descarga ya
resueltos, sin que el receptor tenga que rebuscar nada.

---

## Planteamiento recibido de Miguel Ángel (S011)

Cita textual del encargo original: *"Generar un hash de compartición
que se habrá con la aplicación al enviarlo por cualquier medio.
Comparte los links de descarga favoritos del disco o lista etc.
Réplica total en el repositorio de quien lo abre."*

Al preguntarle qué alcance tiene exactamente ("¿lista de favoritos, un
álbum, un sencillo, una lista, o todo?"), su respuesta (textual,
S011): **"Lo que se comparte depende del que genera el hash, si es la
lista de favoritos, un álbum o un sencillo o una lista o todo. Depende
de desde donde se comparta, el nivel y el lugar."**

Interpretación de esa respuesta, a confirmar con él antes de cerrar el
diseño: **no hay un único modo de compartir fijo** — el hash debe
poder generarse desde varios puntos de la app (p.ej. desde una fila de
álbum, desde una playlist, desde la sección de Favoritos, desde algún
punto que represente "todo"), y lo que contiene cada hash depende de
**desde qué pantalla/nivel concreto se pulsó "compartir"**. Es decir,
el "nivel" y el "lugar" de origen determinan el contenido del hash, no
un selector aparte.

---

## Lo que queda por cerrar de diseño antes de poder escribir una hoja
## de ruta ejecutable

Nada de esto está resuelto todavía — ninguna de estas preguntas se le
ha hecho aún a Miguel Ángel:

1. **Puntos de origen exactos.** ¿Desde qué pantallas/filas concretas
   aparece la opción "Compartir"? (candidatos evidentes por el
   encargo: fila de álbum en Biblioteca/Búsqueda, una playlist
   completa, la sección de Favoritos, quizás una pista suelta) ¿Se
   añade en todas a la vez o se prioriza alguna primero?
2. **Formato del hash/enlace.** ¿Un deep link (`mimoo://share/...`) o
   una URL http normal que, si no se tiene la app, no hace nada útil?
   ¿Dónde se aloja el contenido del hash (Drive, como el resto de
   backups, o un formato autocontenido tipo Base64 en el propio
   enlace)?
3. **"Réplica total en el repositorio de quien lo abre"** — confirmar
   qué significa exactamente "réplica total": ¿sustituye por completo
   lo que el receptor ya tenía dentro de ese nivel (todo-o-nada, mismo
   criterio que `BackupImportRepository.importDestructively()` de H06)
   o se fusiona añadiendo sin borrar nada? H06/H07 ya tienen ambos
   patrones construidos (`importDestructively()` y
   `applyCloudWinsTargeted()`), pero aquí el contexto es distinto:
   quien abre el hash no es "otro dispositivo del mismo usuario", es
   potencialmente otra persona con su propia biblioteca.
4. **Vigencia y revocación.** ¿El hash es válido para siempre o
   caduca? ¿Se puede compartir el mismo hash con varias personas o es
   de un solo uso?
5. **Sin cuenta compartida real entre remitente y receptor** (a
   diferencia de Miguel Ángel/Silvia en H06/H07, que comparten el
   mismo proyecto de Drive): si el receptor no tiene acceso al mismo
   `mimoo-drive`, ¿de dónde descarga el contenido del hash? Puede
   necesitar un mecanismo de almacenamiento distinto al de H06/H07
   (p.ej. un endpoint público de solo lectura, o que el propio hash
   contenga ya todos los metadatos necesarios sin depender de Drive).

---

## Alcance

Sin cerrar todavía — depende de las respuestas de arriba.

- **Sí está claro:** "réplica total" reutiliza los enlaces de
  descarga (youtubeId) ya resueltos del contenido compartido, para
  que el receptor no tenga que buscar nada a mano — mismo principio
  que ya usa H06 (`applyCloudWinsTargeted()`/`importDestructively()`)
  y H07 (persistencia del `youtubeId`).
- **No está claro:** todo lo de la sección anterior.

---

## Hoja de ruta

**Sin hoja de ruta ejecutable todavía** — antes hace falta cerrar el
diseño con Miguel Ángel (sección "Lo que queda por cerrar" arriba).
No arrancar código de este hito sin esa conversación primero.

---
