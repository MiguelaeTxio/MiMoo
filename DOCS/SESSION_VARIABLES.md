# MIMOO — VARIABLES DE SESIÓN (NewFlow Android)

*Vive en `DOCS/SESSION_VARIABLES.md` — editable directamente en
GitHub o vía `newflow-android-edit`. No es una skill: para cambiar
cualquier valor, se edita este archivo y se commitea, sin necesidad
de reinstalar nada en el cliente.*

| Variable | Valor |
|---|---|
| `ANDROID_APP_NAME` | MiMoo |
| `ANDROID_PACKAGE` | `com.miguelaetxio.mimoo` |
| `ANDROID_GITHUB_REPO` | `https://github.com/MiguelaeTxio/MiMoo.git` |
| `ANDROID_GITHUB_OWNER` | MiguelaeTxio |
| `ANDROID_GITHUB_BRANCH` | main |
| `LOCAL_CLONE_PATH` (workspace del modelo) | `/home/claude/repo/MiMoo` |
| `MASTER_DOCUMENT_PATH` | `DOCS/MASTER_DOCUMENT.md` |
| `ROUTER_PATH` (única fuente de verdad del hito EN PROGRESO) | `DOCS/ANNEX_ROUTER.md` |
| `ANNEX_PATH_PATTERN` | `DOCS/ANNEX_H{NN}.md` |
| `RESUMPTION_POINT_PATH` | `DOCS/RESUMPTION_POINT.md` |
| `APK_DEPLOY_PATH` (PythonAnywhere, gestionado por el workflow) | `/home/MiguelAeTxio/ANDROID/MiMoo/apk/MiMoo.apk` |

## Secrets de GitHub Actions (no vive el valor aquí, solo el nombre)

- `YOUTUBE_API_KEY`
- `PA_API_TOKEN`

## Notas

- El token de GitHub para `newflow-android-edit` **nunca** vive en
  este archivo ni en ningún otro archivo del repositorio — solo en
  memoria de comandos puntuales de la sesión activa, ver
  `newflow-android-token`.
- Este archivo sustituye a lo que antes eran variables embebidas
  directamente en el texto de las skills `android-edit`/`android-git`
  (`ANDROID_SERVER_ROOT`, etc., que asumían PythonAnywhere como
  almacén). Esas variables ya no aplican al flujo NewFlow.
