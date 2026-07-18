# MIMOO — ENRUTADOR DE ANEXOS

*Vive en `DOCS/ANNEX_ROUTER.md`. **Único archivo del repositorio con
potestad para decir qué hito está EN PROGRESO** (instrucción explícita
y repetida de Miguel Ángel, 2026-07-15). Ningún otro archivo —
`MASTER_DOCUMENT.md`, ningún `ANNEX_H0X.md`, `RESUMPTION_POINT.md` —
contiene esa información. Editar y commitear como cualquier otro
archivo del proyecto, vía `newflow-android-edit`.*

---

## PRINCIPIO FUNDAMENTAL

Solo existen **dos** estados posibles para un hito: **EN PROGRESO** y
**PAUSADO**. Nunca "completado" — ningún hito lo está jamás.

Debe haber **siempre exactamente un** hito EN PROGRESO — nunca cero,
nunca dos. Todos los demás están PAUSADOS.

`MASTER_DOCUMENT.md` y los anexos (`DOCS/ANNEX_H0X.md`) son
puramente descriptivos: qué está construido/verificado y qué queda
abierto, sin mencionar jamás su propio estado.

---

## TABLA DE ESTADO

| Hito | Anexo | Estado |
|---|---|---|
| H01 | `DOCS/ANNEX_H01.md` | PAUSADO |
| H02 | `DOCS/ANNEX_H02.md` | PAUSADO |
| H03 | `DOCS/ANNEX_H03.md` | PAUSADO |
| H04 | `DOCS/ANNEX_H04.md` | PAUSADO |
| H05 | `DOCS/ANNEX_H05.md` | PAUSADO |
| H06 | `DOCS/ANNEX_H06.md` | PAUSADO |
| H07 | `DOCS/ANNEX_H07.md` | ← **EN PROGRESO** |
| H08 | `DOCS/ANNEX_H08.md` | PAUSADO |
| H09 | `DOCS/ANNEX_H09.md` | PAUSADO |
| H10 | `DOCS/ANNEX_H10.md` | PAUSADO |
| H11 | `DOCS/ANNEX_H11.md` | PAUSADO |

---

## HISTORIAL DE CAMBIOS DE HITO

- **2026-07-15** — Enrutador creado (migración desde el marcador que
  antes vivía en la tabla de `MASTER_DOCUMENT.md`, instrucción de
  Miguel Ángel). H09 se mantiene EN PROGRESO sin interrupción; H10 se
  abre PAUSADO en la misma sesión (planteamiento inicial recibido,
  sin hoja de ruta ejecutable todavía).
- **2026-07-15 (misma sesión S011)** — H11 abierto PAUSADO: Canales
  (suscripciones + descarga automática tipo podcast), a petición
  explícita de Miguel Ángel al aclarar qué significaba "Canal" en los
  niveles 9-10 de H10. H09 se mantiene EN PROGRESO.
- **2026-07-18** — PCH explícito de Miguel Ángel al arranque de
  sesión: H09 pasa a PAUSADO (sin incidencia nueva, simplemente se
  cierra el foco de esta sesión), H08 pasa a EN PROGRESO (fallo real
  reportado en dispositivo sobre la Ampliación S011: filtro
  país+género+década no está evitando que temas en español deriven a
  sugerencias en inglés — ver `DOCS/ANNEX_H08.md`).
- **2026-07-18 (cierre de S014, misma fecha)** — PCH explícito de
  Miguel Ángel al cierre de sesión: H08 pasa a PAUSADO (el rediseño
  cerrado en S013 quedó construido y compilando en S014, pendiente
  solo de verificación en dispositivo real -- ver `DOCS/ANNEX_H08.md`
  sección "COMPLETADAS EN S014"), H07 pasa a EN PROGRESO --
  divergencia real de favoritos/ajustes entre dispositivos tras
  sincronizar (reportada por Miguel Ángel en S013, ver
  `DOCS/ANNEX_H07.md`).
