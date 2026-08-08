package com.miguelaetxio.mimoo.data.playback

import javax.inject.Inject
import javax.inject.Singleton

/**
 * H15 (miMooutCast), S032 -- señal compartida mínima para enrutar el
 * log de depuración de las funciones que Radio (H08) y miMooutCast
 * comparten (`RadioRepository.suggestRelatedArtist()`/
 * `verifyTrackExists()`/`ensureDiscographyCached()`, `AnchorDictionary`,
 * `PlayerManager.resolveYoutubeCandidate()`). Sin esto, esas clases no
 * tienen forma de saber si la llamada que están atendiendo viene de
 * una sesión de miMooutCast o de la Radio automática -- inyectar
 * `PlayerManager` directamente en ellas crearía una dependencia
 * circular (`PlayerManager` ya las inyecta a ellas).
 *
 * Orden explícita y repetida de Miguel Ángel, tras varias rondas de
 * confusión real por esto: el archivo de depuración de Radio
 * (`radio_relacionados_debug.txt`) NO debe tocarse en absoluto
 * mientras se usa miMooutCast, sea cual sea el motivo interno de la
 * llamada compartida -- toda esa actividad debe ir a
 * `mimooutcast_debug.txt`. Ver `ANNEX_H15.md`, "COMPLETADAS EN S032",
 * punto 15.
 *
 * `PlayerManager.manualAnchorActive` delega en `active` como única
 * fuente de verdad -- nunca dos banderas separadas que puedan
 * desincronizarse.
 * ---
 * H15 (miMooutCast), S032 -- minimal shared signal to route debug
 * logging for the functions Radio (H08) and miMooutCast share.
 * Without this, those classes have no way to know whether the call
 * they're handling comes from a miMooutCast session or from
 * automatic Radio -- injecting `PlayerManager` directly into them
 * would create a circular dependency.
 *
 * Explicit, repeated instruction from Miguel Ángel, after several
 * rounds of real confusion over this: the Radio debug file must never
 * be touched at all while miMooutCast is in use, whatever the
 * internal reason for the shared call -- all of that activity must
 * go to the miMooutCast debug file instead.
 *
 * `PlayerManager.manualAnchorActive` delegates to `active` as the
 * single source of truth -- never two separate flags that could
 * drift out of sync.
 */
@Singleton
class MiMooutcastSessionFlag @Inject constructor() {
    @Volatile
    var active: Boolean = false
}
