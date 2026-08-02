package com.miguelaetxio.mimoo.data.remote

import android.content.Context
import com.miguelaetxio.mimoo.BuildConfig
import com.miguelaetxio.mimoo.data.backup.NetworkConnectivityChecker
import com.miguelaetxio.mimoo.data.download.StorageManager
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzArtistSummary
import com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzGenre
import com.miguelaetxio.mimoo.util.SearchNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Género + país + década + origen fijados UNA SOLA VEZ al arrancar
 * una sesión de Radio (S010 género/país, S011 década, S013/S014
 * origen). Se calculan del PRIMER artista y no se vuelven a tocar
 * mientras dure la sesión.
 *
 * `originGroup` -- ABSOLUTO para el resto de la sesión, nunca se
 * relaja en ningún peldaño de ningún cupo. Pared total entre los
 * cuatro grupos (S026: Iberoamericana/Anglosajona/Europea/Mundial,
 * sustituyendo el binario España/resto de S020) -- ver `OriginGroup`
 * para la decisión completa y la lista de países por grupo.
 *
 * **S020 -- separación dura en los DOS sentidos, regla cerrada por
 * Miguel Ángel.** Un grupo -> solo artistas del MISMO grupo. Ya no
 * existe el "modo mixto": hasta S020, "no español" significaba "sin
 * restricción de origen", y eso metía el bloque español entero del
 * diccionario en cualquier sesión anclada en un artista extranjero --
 * medido sobre log real, con ancla Pixies (rock/US/1980) el 60% del
 * pool disponible era música española.
 * Lo extranjero sigue teniendo que ser CONOCIDO EN ESPAÑA cuando sale
 * del diccionario (ver KnownHitsRepository, bloque "intl"), nunca
 * cualquier tema del Billboard sin más.
 * ---
 * Genre + country + decade + origin fixed ONCE when a Radio session
 * starts. Computed from the FIRST artist and never recalculated for
 * the rest of the session.
 */
data class RadioAnchor(
    val genre: String,
    /**
     * TODOS los géneros que MusicBrainz atribuye al artista ancla, no
     * solo el más votado.
     *
     * S022 -- el fallo que Miguel Ángel calificó de aberración:
     * MusicBrainz describe a Dead Can Dance como
     * `dark wave(13), ethereal wave(9), gothic(5),
     * neoclassical dark wave(5), new age(4), ambient(1), post-punk(1)`,
     * y el código se quedaba con `dark wave` y tiraba los otros seis.
     * A partir de ahí había que reconstruir a mano, con familias de
     * géneros escritas a ojo, la información que se acababa de
     * descartar -- y así es como Pet Shop Boys acabó en una radio de
     * Dead Can Dance: agrupados por usar sintetizadores, que es como
     * juntar a Bob Marley con Bad Bunny porque reggae y reggaetón
     * suenan parecido.
     *
     * Conservando el conjunto, la pertenencia se decide por
     * INTERSECCIÓN con los géneros del candidato, que es un dato real
     * de la misma fuente y no una taxonomía inventada:
     *
     *   Joy Division   {post-punk, new wave, gothic rock} -> corta en
     *                  `post-punk` -> entra
     *   Pet Shop Boys  {synth-pop, dance-pop, house}      -> vacía -> fuera
     *   Guns N' Roses  {hard rock, glam metal}            -> vacía -> fuera
     *
     * `genre` se conserva porque las consultas a MusicBrainz necesitan
     * un único término de búsqueda.
     */
    val genres: Set<String> = setOf(genre),
    val country: String?,
    val decadeBegin: Int? = null,
    /**
     * S027 -- año EXACTO del tema ancla, cuando se conoce (no todos
     * los caminos lo dan -- ver `resolveOriginalDecade()`/
     * `TrackDecade`). Sustituye la comparación por década fija (bloque
     * de 10 años) en `resolveYoutubeCandidate()` por una ventana de
     * años alrededor de este valor -- ver su parámetro `yearWindow`.
     * `null` hace que se use `decadeBegin` como antes (frontera de
     * década fija), para los pocos casos sin año exacto disponible.
     */
    val anchorYear: Int? = null,
    /**
     * S027 -- ver el kdoc de `RadioRepository.TrackDecade.networkFailure`.
     * `true` cuando la década/año del ancla no se pudo determinar por
     * falta de red en ese momento exacto (no porque el tema
     * genuinamente no tenga año encontrable). `PlayerManager` lo trata
     * como el aviso de "sin conexión" ya existente para la Radio, en
     * vez de arrancar una sesión sin ningún filtro temporal.
     */
    val decadeUnknownDueToNetwork: Boolean = false,
    /**
     * S026 -- `null` cuando MusicBrainz no da país para el artista
     * ancla y tampoco está en el diccionario de éxitos (dato
     * desconocido, no un grupo real) -- ver `OriginGroup.of()`. El
     * código que consulta esto debe abrirse a todo en vez de amurallar
     * contra un grupo inventado, mismo principio de degradación
     * elegante que ya usa el resto de la Radio.
     */
    val originGroup: OriginGroup? = null,
    /**
     * Repertorio clásico (S024).
     *
     * **Qué implica: el país deja de filtrar POR COMPLETO.** Orden de
     * Miguel Ángel -- *"en la radio de la clásica no deberíamos
     * filtrar por origen, restringimos demasiado si filtramos por
     * origen"*, precisada después: *"no se trata de permitir
     * españoles, se trata de permitir cualquiera -- alemanes,
     * franceses, ingleses, italianos, españoles, rusos, checos, y de
     * cualquier país del mundo"*.
     *
     * No es una excepción para España: es que la dimensión país
     * desaparece. Ni `country:ES` ni `NOT country:ES` -- ninguna
     * cláusula de país.
     *
     * La separación dura España/extranjero que cerró S020 existe
     * porque en música popular el origen SE NOTA: un ancla de Mecano
     * no debe traer a Duran Duran. El repertorio clásico es
     * internacional por naturaleza y se escucha como un solo cuerpo:
     * Beethoven, Debussy, Elgar, Vivaldi, Chaikovski, Dvořák y Falla
     * son la misma radio. Filtrar por país ahí no ordena nada y
     * estrecha el pool justo donde ya iba corto.
     *
     * No deroga la regla de S020: la deja fuera de un repertorio donde
     * nunca tuvo sentido.
     */
    val isClassical: Boolean = false,
)

/**
 * H08 PARTE 2 -- "Radio": dado el artista que estaba sonando, sugiere
 * otro relacionado vía MusicBrainz, para continuar la reproducción en
 * streaming cuando la cola se queda sin nada más y el cíclico está
 * desactivado (PlayerManager).
 *
 * S013/S014 -- REDISEÑO DE ORIGEN (ver DOCS/ANNEX_H08.md, sección
 * "S013", motivación completa). `suggestRelatedArtist()` es AHORA
 * únicamente el mecanismo del cupo de "exploración" (10% de las
 * pistas que añade Radio, ver PlayerManager) -- ya NO decide por sí
 * solo si un candidato es aceptable (eso lo hace el cupo 80/10/10 en
 * PlayerManager, que combina esta clase con KnownHitsRepository y la
 * biblioteca local). Dentro de esta búsqueda, el origen (grupo de
 * `anchor.originGroup`, S026 -- ver `OriginGroup`) se
 * mantiene FIJO durante toda la cascada género/década -- nunca se
 * relaja aquí dentro (petición explícita de Miguel Ángel: "el origen
 * NO se relaja nunca" para este cupo). Cascada (prioridad género >
 * década, ver ANNEX_H08.md S013 punto 5):
 *   1. género + década exacta (+ origen)
 *   2. género, cualquier década (+ origen)
 *   3. década exacta, cualquier género (+ origen)
 *   4. sin candidatos -- null (el llamante decide el fallback final,
 *      que si acaso relaja el origen, ver PlayerManager).
 * ---
 * H08 PART 2 -- "Radio": given the artist that was playing, suggests
 * a related one via MusicBrainz.
 *
 * S013/S014 -- ORIGIN REDESIGN. `suggestRelatedArtist()` is now only
 * the "exploration" quota's mechanism (10% of the tracks Radio adds)
 * -- it no longer decides on its own whether a candidate is
 * acceptable. Origin stays FIXED through the whole genre/decade
 * cascade -- never relaxed inside this function.
 */
@Singleton
class RadioRepository @Inject constructor(
    private val musicBrainzApiService: MusicBrainzApiService,
    private val knownHitsRepository: KnownHitsRepository,
    // S024 -- para marcar el ancla como de repertorio clasico en el
    // momento en que se fija, y que todo lo demas lo herede de ahi.
    private val genreTree: GenreTree,
    @ApplicationContext private val appContext: Context,
    private val storageManager: StorageManager,
    // S025 -- diccionario del ancla persistido en la tarjeta. Se
    // consulta ANTES que la red y se alimenta con todo lo que la red
    // resuelva, de modo que cada sesión depende menos de MusicBrainz
    // que la anterior. Ver AnchorDictionary.
    private val anchorDictionary: AnchorDictionary,
    // S025 -- último peldaño de la fecha de primera edición. Ver
    // firstReleaseYearFromWikidata().
    private val wikidataApiService: WikidataApiService,
    private val discogsApiService: DiscogsApiService,
    // Bug real reportado por Miguel Ángel (2026-08-02), con captura y
    // log de depuración: el cartel "Radio detenida: se ha perdido la
    // conexión" salía con Wi-Fi perfectamente conectado -- porque
    // "SIN RED" solo comprobaba si MusicBrainz/Discogs habían fallado
    // (isTransient(), timeouts/503/429 incluidos), NUNCA si el
    // teléfono tenía conexión real. Ver verifyTrackExists() y
    // resolveOriginalDecade() más abajo.
    private val networkConnectivityChecker: NetworkConnectivityChecker,
) {
    /**
     * Perfil de un artista para la fuente de "disco" (10% de la
     * biblioteca local, S013/S014, ver PlayerManager.pickDiscoCandidate()).
     * A diferencia de RadioAnchor (un único género elegido al azar),
     * aquí se devuelve el conjunto completo de géneros del artista,
     * para poder comprobar si contiene el género del ancla sin perder
     * información por el camino.
     */
    data class ArtistProfile(val genres: Set<String>, val country: String?, val decadeBegin: Int?)

    /**
     * SOLO se llama una vez, al arrancar una sesión de Radio -- ver
     * comentario de clase y PlayerManager.radioAnchor.
     * ---
     * ONLY called once, when a Radio session starts.
     */
    /**
     * Fallos transitorios de MusicBrainz seguidos (503, 429, timeout,
     * corte de red). Se pone a cero en cuanto una llamada responde.
     *
     * S022 -- MusicBrainz es gratuito y notoriamente inestable, y sus
     * caídas envenenaban la Radio entera: `lookupArtistProfile()` y
     * `findCandidates()` devuelven `null`/vacío tanto cuando NO HAY
     * candidatos como cuando NO SE HA PODIDO PREGUNTAR, y el motor
     * trataba ambos como "porción agotada", que es irreversible. Ocho
     * timeouts seguidos bastaron para dejar una sesión sirviendo doce
     * temas del mismo artista.
     */
    @Volatile
    var consecutiveTransientFailures: Int = 0
        private set

    /** ¿Está MusicBrainz dando problemas ahora mismo? */
    val isServiceDegraded: Boolean
        get() = consecutiveTransientFailures >= DEGRADED_THRESHOLD

    /**
     * True si el último fallo fue de red y no una respuesta legítima.
     * Lo consulta el motor para no derivar un ancla de la biblioteca
     * local cuando lo único que ha pasado es que MusicBrainz no
     * contesta.
     */
    @Volatile
    var lastFailureWasTransient: Boolean = false
        private set

    private fun isTransient(e: Exception): Boolean = when (e) {
        is retrofit2.HttpException -> e.code() == 429 || e.code() >= 500
        is java.io.IOException -> true
        else -> false
    }

    private fun noteFailure(e: Exception) {
        val transient = isTransient(e)
        lastFailureWasTransient = transient
        if (transient) consecutiveTransientFailures++
    }

    private fun noteSuccess() {
        consecutiveTransientFailures = 0
        lastFailureWasTransient = false
    }

    /**
     * S025 -- ANCLAJE DESDE EL DICCIONARIO, EN EL ORDEN DICTADO POR
     * MIGUEL ÁNGEL.
     *
     * Sus palabras, que son la especificación entera:
     *
     *   *"Se determina el nombre del artista, se determina el nombre de
     *   la canción. Se mira el artista de dónde es: origen. Se mira el
     *   género. ¿Es clásica? Sí: ya da igual el origen, anclamos con
     *   género, y ya da igual la década. Que no es clásica: anclamos
     *   con origen y género del artista. Y ahora la década, la del tema
     *   original, no la del remaster."*
     *
     * Devuelve `null` solo si el artista no está en el diccionario, que
     * es la señal para que `resolveAnchor()` siga con la red.
     */
    private suspend fun anchorFromDictionary(
        sourceArtist: String,
        sourceTrackTitle: String?,
    ): RadioAnchor? {
        val facts = anchorDictionary.artist(sourceArtist) ?: return null
        val genres = facts.genres.map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (genres.isEmpty()) return null

        val isClassical = genres.any {
            it == "classical" || genreTree.isDescendantOf(it, "classical")
        }
        // El género que manda es el más CONCRETO, no el primero de la
        // lista: `hard rock` describe una radio, `rock` no describe
        // nada. Desempate alfabético para que el mismo artista ancle
        // siempre igual (regla determinista de S020).
        val chosenGenre = genres.filter { genreTree.isSpecific(it) }.minOrNull()
            ?: genres.minOrNull()
            ?: return null

        // PASO 3 -- CLÁSICA: se acabó. Ni origen ni década.
        // *"Si el género es clásica, anclamos por clásica, da igual, y
        // buscamos artistas de clásica. Se acabó."*
        //
        // Esto es además lo que arregla el caso del log: en clásica el
        // vídeo se titula con el INTÉRPRETE, no con el compositor
        // ('Valentina Lisitsa' tocando a Beethoven). Por eso el índice
        // incluye directores, orquestas, solistas y voces, no solo
        // compositores: preguntando por el intérprete se llega igual a
        // `classical` y la Radio arranca.
        if (isClassical) {
            log(
                "resolveAnchor('$sourceArtist') -> ancla del DICCIONARIO (sin red): " +
                    "CLÁSICA -- género='$chosenGenre', sin origen y sin década, " +
                    "géneros=[${genres.joinToString()}]"
            )
            return RadioAnchor(
                genre = chosenGenre,
                genres = genres,
                country = null,
                decadeBegin = null,
                originGroup = null,
                isClassical = true,
            )
        }

        // PASO 4 -- no es clásica: ancla = ORIGEN + GÉNERO del artista.
        val country = facts.country?.trim()?.ifBlank { null }
        // S026 -- OriginGroup.of() ya resuelve el país; si MusicBrainz
        // no da país para este artista, se cae al diccionario de
        // éxitos (mismo papel que hacía antes isKnownSpanishArtist(),
        // ahora generalizado a los cuatro grupos).
        val originGroup = OriginGroup.of(country)
            ?: knownHitsRepository.originGroupOfKnownArtist(sourceArtist)

        // PASO 5 -- la década, del TEMA ORIGINAL.
        val trackDecade = resolveOriginalDecade(sourceArtist, sourceTrackTitle)
        val decadeBegin = trackDecade.decadeBegin

        log(
            "resolveAnchor('$sourceArtist') -> ancla del DICCIONARIO (sin red): " +
                "género='$chosenGenre', país=${country ?: "?"}, grupo=${originGroup ?: "?"}, " +
                "década=${decadeBegin ?: "?"}, año=${trackDecade.exactYear ?: "?"}, géneros=[${genres.joinToString()}]"
        )
        return RadioAnchor(
            genre = chosenGenre,
            genres = genres,
            country = country,
            decadeBegin = decadeBegin,
            anchorYear = trackDecade.exactYear,
            decadeUnknownDueToNetwork = trackDecade.networkFailure,
            originGroup = originGroup,
            isClassical = false,
        )
    }

    /**
     * S025 -- DÉCADA DE LA PRIMERA EDICIÓN DEL TEMA.
     *
     * Orden de Miguel Ángel, y no hay nada más: *"para anclar la década
     * miramos el tema, edición original del tema, la que sea, pues
     * anclamos en esa década. Se acabó. No hay más."*
     *
     * El fallo que lo motiva, de su log:
     *
     *   resolveTrackDecade('Led Zeppelin' -- 'Black Dog')
     *     -> década 1980 (primera publicación 1983)
     *
     * "Black Dog" es de noviembre de 1971. El 1983 salía de preguntar a
     * nivel de GRABACIÓN, donde una remasterización o un directo valen
     * tanto como el original. Lo que se busca es la PRIMERA edición.
     *
     * Cascada, y se para en la primera que conteste: diccionario en
     * tarjeta -> diccionario de éxitos -> MusicBrainz. Si ninguna
     * contesta, el tema se apunta en la cola de pendientes y se ancla
     * igual por origen y género: no saber el año no puede parar la
     * Radio.
     */
    /**
     * S026 -- ¿ES ESTE VÍDEO DE VERDAD UN TEMA DE ESTE ARTISTA?
     *
     * Orden de Miguel Ángel, literal, tras ver un corto de animación de
     * Sony colarse en una radio de Led Zeppelin buscando solo el
     * nombre del artista ('Free'): *"el título del vídeo tiene que ser
     * de un artista y de un tema de ese artista... si no coincide con
     * ningún título de ese artista, se desecha. Y si no hay red no hay
     * radio... antes parar que meter un vídeo que no viene a cuento."*
     * También, explícito: *"me da igual validar contra MusicBrainz,
     * Discogs, local, Wikidata o la fuente que sea."*
     *
     * Solo hace falta para las búsquedas "solo artista" (sin canción
     * conocida, ver `resolveYoutubeCandidate()` en `PlayerManager`):
     * cuando SÍ hay canción conocida, viene del diccionario propio, ya
     * es un dato curado y real por construcción, verificarlo sería
     * redundante.
     *
     * Reutiliza exactamente la misma cascada que ya usa
     * `resolveOriginalDecade()` (diccionario en tarjeta -> diccionario
     * de éxitos -> MusicBrainz -> Discogs -> Wikidata) pero con un
     * matiz importante: aquí SÍ importa distinguir "comprobado, no
     * existe" de "no se ha podido comprobar por falta de red" --
     * `resolveOriginalDecade()` trata ambos casos igual (no para la
     * Radio), pero aquí el segundo caso debe PARARLA, según pide Miguel
     * Ángel. La distinción se apoya en `lastFailureWasTransient`, que
     * ya usa el resto de la clase para lo mismo (ver `reconcilePending()`).
     */
    sealed class TrackExistence {
        /**
         * S027 -- `exactYear` es el año real del tema encontrado,
         * cuando se conoce -- permite comparar por VENTANA en
         * `resolveYoutubeCandidate()` en vez de por década fija. `null`
         * solo en el camino del diccionario de éxitos (catálogo
         * organizado por década, sin año exacto por canción).
         */
        data class Confirmed(val decadeBegin: Int?, val exactYear: Int? = null) : TrackExistence()
        object NotFound : TrackExistence()
        object NetworkUnavailable : TrackExistence()
    }

    /**
     * S027 -- reintento de UNA sola vez ante un fallo transitorio.
     * Bug real reportado por Miguel Ángel: "Radio detenida" saltando
     * varias veces en 24 minutos con wifi estable en casa. Causa: un
     * único 503/429/timeout puntual de MusicBrainz -- un servicio
     * pequeño y comunitario, le pasa de vez en cuando aunque la
     * conexión del usuario esté perfecta -- paraba la Radio entera de
     * inmediato, sin intentarlo una segunda vez. Se reintenta UNA vez
     * tras una espera breve; si el segundo intento también falla por
     * red, entonces sí se da por auténtico y se avisa al usuario.
     */
    private suspend fun <T> retryOnceIfTransient(fetch: suspend () -> T?): T? {
        val first = fetch()
        if (first != null || !lastFailureWasTransient) return first
        log("retryOnceIfTransient() -- fallo transitorio, se reintenta una vez tras una breve espera")
        delay(1500)
        return fetch()
    }

    /**
     * S027 -- artistas cuya discografía completa ya se ha pedido esta
     * sesión (con éxito o sin él), para no repetir el browse una y
     * otra vez sobre el mismo artista.
     */
    private val discographyCachedArtists = mutableSetOf<String>()

    /**
     * S027 -- pregunta de Miguel Ángel: *"¿se puede recibir todo el
     * paquete de búsquedas en una sola llamada?"* Sí -- MusicBrainz
     * tiene BROWSE, no solo búsqueda: la discografía entera de un
     * artista (por MBID) en una o dos llamadas paginadas, en vez de
     * preguntar título por título "¿existe esto?" una y otra vez cada
     * vez que sale como candidato. Orden textual: *"exprimir al
     * máximo, creando listas válidas de temas por artista para ir
     * sirviendo temas de dicho artista cuando acabe su restricción de
     * diez temas"*.
     *
     * Se llama UNA vez por artista y sesión, la primera vez que sale
     * como candidato real (ver `PlayerManager.fetchRoundCandidate()`).
     * Guarda título+año de cada obra en el diccionario en tarjeta
     * (`AnchorDictionary.learnTrackYearsBulk()`, una sola escritura) --
     * a partir de ahí, CUALQUIER canción de ese artista que
     * `verifyTrackExists()`/`resolveOriginalDecade()` necesite fechar
     * es una consulta local instantánea, sin gastar ni una llamada de
     * red más para ese artista en el resto de la sesión.
     *
     * Tope de 2 páginas (200 obras) por artista -- de sobra para casi
     * cualquier discografía real, y acota el coste para las pocas que
     * no lo son.
     */
    suspend fun ensureDiscographyCached(artist: String) {
        val key = SearchNormalizer.normalizeArtistName(artist)
        if (key.isBlank() || key in discographyCachedArtists) return
        discographyCachedArtists.add(key)

        val pick = try {
            findAnchorArtistMbid(artist)
        } catch (e: Exception) {
            noteFailure(e)
            null
        } ?: run {
            log("ensureDiscographyCached('$artist') -- no se pudo resolver MBID, se sigue con búsqueda tema a tema")
            return
        }

        val entries = mutableListOf<Pair<String, Int>>()
        var offset = 0
        val pageSize = 100
        var page = 0
        while (page < 2) {
            page++
            val response = try {
                musicBrainzApiService.browseReleaseGroupsByArtist(pick.mbid, limit = pageSize, offset = offset)
            } catch (e: Exception) {
                noteFailure(e)
                break
            }
            noteSuccess()
            if (response.releaseGroups.isEmpty()) break
            for (rg in response.releaseGroups) {
                val year = rg.firstReleaseDate?.take(4)?.toIntOrNull() ?: continue
                if (year in 1850..2100) entries += rg.title to year
            }
            if (response.releaseGroups.size < pageSize) break
            offset += pageSize
        }

        if (entries.isNotEmpty()) {
            anchorDictionary.learnTrackYearsBulk(artist, entries, "musicbrainz-browse")
            log("ensureDiscographyCached('$artist') -> ${entries.size} temas guardados de golpe (discografía completa)")
        }
    }

    suspend fun verifyTrackExists(artist: String, rawVideoTitle: String): TrackExistence {
        val cleanTitle = stripTitleNoise(rawVideoTitle)
        if (cleanTitle.isBlank()) return TrackExistence.NotFound

        anchorDictionary.trackYear(artist, cleanTitle)?.let { year ->
            log("verifyTrackExists('$artist' -- '$cleanTitle') -> CONFIRMADO, del diccionario en tarjeta")
            return TrackExistence.Confirmed((year / 10) * 10, year)
        }
        knownHitsRepository.decadeOfTrack(artist, cleanTitle)?.let { decade ->
            log("verifyTrackExists('$artist' -- '$cleanTitle') -> CONFIRMADO, del diccionario de éxitos")
            return TrackExistence.Confirmed(decade, null)
        }

        val mbYear = retryOnceIfTransient { firstReleaseYearFromMusicBrainz(artist, cleanTitle) }
        if (mbYear != null) {
            anchorDictionary.learnTrackYear(artist, cleanTitle, mbYear, "musicbrainz")
            log("verifyTrackExists('$artist' -- '$cleanTitle') -> CONFIRMADO, de MusicBrainz ($mbYear)")
            return TrackExistence.Confirmed((mbYear / 10) * 10, mbYear)
        }
        if (lastFailureWasTransient && !networkConnectivityChecker.isConnected()) {
            log("verifyTrackExists('$artist' -- '$cleanTitle') -- SIN RED (MusicBrainz no responde, ni siquiera al reintentar, y el teléfono no tiene conexión)")
            return TrackExistence.NetworkUnavailable
        }

        val discogsYear = retryOnceIfTransient { firstReleaseYearFromDiscogs(artist, cleanTitle) }
        if (discogsYear != null) {
            anchorDictionary.learnTrackYear(artist, cleanTitle, discogsYear, "discogs")
            log("verifyTrackExists('$artist' -- '$cleanTitle') -> CONFIRMADO, de Discogs ($discogsYear)")
            return TrackExistence.Confirmed((discogsYear / 10) * 10, discogsYear)
        }
        if (lastFailureWasTransient && !networkConnectivityChecker.isConnected()) {
            log("verifyTrackExists('$artist' -- '$cleanTitle') -- SIN RED (Discogs no responde, ni siquiera al reintentar, y el teléfono no tiene conexión)")
            return TrackExistence.NetworkUnavailable
        }

        val wikidataYear = retryOnceIfTransient { firstReleaseYearFromWikidata(artist, cleanTitle) }
        if (wikidataYear != null) {
            anchorDictionary.learnTrackYear(artist, cleanTitle, wikidataYear, "wikidata")
            log("verifyTrackExists('$artist' -- '$cleanTitle') -> CONFIRMADO, de Wikidata ($wikidataYear)")
            return TrackExistence.Confirmed((wikidataYear / 10) * 10, wikidataYear)
        }

        log(
            "verifyTrackExists('$artist' -- '$cleanTitle') -- NO ENCONTRADO en ninguna fuente " +
                "(diccionarios, MusicBrainz, Discogs, Wikidata) -- se descarta el vídeo",
        )
        return TrackExistence.NotFound
    }

    /**
     * S027 -- año exacto y década (año/10*10) del tema original.
     * Antes solo se devolvía la década, lo que creaba una frontera
     * artificial: una canción de 1979 y una de 1980 quedan a un año de
     * distancia mundo real, pero en décadas distintas -- exactamente
     * el caso real que motivó esto (ancla New Wave de 1979, década
     * "1970", con casi ningún tema porque el New Wave es
     * fundamentalmente un movimiento de los 80). Orden textual de
     * Miguel Ángel: *"cinco años atrás, cinco años adelante... si
     * vemos que es corto, no pasa nada, diez años hacia adelante, diez
     * años hacia atrás"*. Con el año exacto disponible,
     * `resolveYoutubeCandidate()` compara por VENTANA en vez de por
     * década fija -- ver su parámetro `yearWindow`.
     *
     * `networkFailure` -- bug real reportado por Miguel Ángel con
     * captura de pantalla: sesión anclada en 'Radio Futura' (España,
     * años 80) sirvió a Dani Martín, Sôber, Arde Bogotá, Mónica
     * Naranjo... TODOS artistas de 2000-2020, ni uno solo de los años
     * 80. Causa: en el momento exacto de fijar el ancla no había red,
     * `resolveOriginalDecade()` devolvía año Y década nulos, y el
     * ancla quedaba SIN NINGÚN FILTRO TEMPORAL para el resto de la
     * sesión entera -- "se ancla por origen y género" trataba igual
     * "no hay dato" que "no se pudo preguntar ahora mismo", cuando son
     * cosas completamente distintas. Con `networkFailure=true` el
     * llamante (`PlayerManager.fetchOneRadioTrack()`) trata esto como
     * el aviso de "sin conexión" que ya existe para la Radio -- deja
     * sonar lo que hay en cola, nunca interrumpe antes de tiempo --
     * en vez de arrancar una sesión sin ningún control de época.
     *
     * SEGUNDO bug real, mismo campo (2026-08-02): el cartel de "sin
     * conexión" salía con Wi-Fi perfectamente conectado -- porque
     * `networkFailure` se ponía a `true` solo con que MusicBrainz/
     * Discogs/Wikidata hubieran fallado (`isTransient()`: timeouts,
     * 503, 429...), sin comprobar NUNCA si el teléfono tenía conexión
     * real. "Eso no es no hay red. No hay red es cuando no hay red"
     * -- orden textual de Miguel Ángel. Ahora `networkFailure` exige
     * ADEMÁS `!networkConnectivityChecker.isConnected()`: si el
     * teléfono tiene conexión real pero los tres servicios están
     * fallando (MusicBrainz es "gratuito y notoriamente inestable",
     * ver `consecutiveTransientFailures` más abajo), esto se trata
     * como "sin dato" -- no ancla sin filtro temporal por eso, pero
     * tampoco enseña al usuario un aviso que dice algo falso.
     */
    data class TrackDecade(val decadeBegin: Int?, val exactYear: Int?, val networkFailure: Boolean = false)

    private suspend fun resolveOriginalDecade(
        artist: String,
        trackTitle: String?,
    ): TrackDecade {
        val cleanTitle = trackTitle?.let { stripTitleNoise(it) }

        anchorDictionary.trackYear(artist, cleanTitle)?.let { year ->
            log("resolveOriginalDecade('$artist' -- '$cleanTitle') -> $year, del diccionario en tarjeta")
            return TrackDecade((year / 10) * 10, year)
        }
        knownHitsRepository.decadeOfTrack(artist, cleanTitle)?.let { decade ->
            log("resolveOriginalDecade('$artist' -- '$cleanTitle') -> década $decade, del diccionario de éxitos")
            // S027 -- el diccionario de éxitos solo guarda década (es
            // un catálogo organizado por década, no por año), sin año
            // exacto disponible. `exactYear = null` hace que
            // `resolveYoutubeCandidate()` caiga a comparar por década
            // como antes, solo para este camino concreto.
            return TrackDecade(decade, null)
        }
        if (cleanTitle.isNullOrBlank()) return TrackDecade(null, null)

        val year = retryOnceIfTransient { firstReleaseYearFromMusicBrainz(artist, cleanTitle) }
            ?: retryOnceIfTransient { firstReleaseYearFromDiscogs(artist, cleanTitle) }
            ?: retryOnceIfTransient { firstReleaseYearFromWikidata(artist, cleanTitle) }
        if (year == null) {
            // Sin red o sin dato: se apunta para resolverlo más tarde y
            // se sigue. Sin año se ancla igual por origen y género --
            // "no lo sé" no es "no hay", y no puede parar la Radio.
            anchorDictionary.rememberPending(artist, cleanTitle)
            // Bug real reportado por Miguel Ángel (2026-08-02): el
            // cartel de "sin conexión" salía con Wi-Fi conectado,
            // porque esto solo miraba si MusicBrainz/Discogs/Wikidata
            // habían fallado (isTransient()), nunca si el teléfono
            // tenía conexión real -- ver el comentario del parámetro
            // networkConnectivityChecker del constructor.
            // `reallyNoNetwork` exige AMBAS cosas: que el último fallo
            // fuera de red Y que el teléfono esté realmente
            // desconectado. Si hay conexión real, esto se trata como
            // "sin dato" (no ancla sin filtro temporal por eso, pero
            // tampoco detiene la Radio con un aviso engañoso) en vez
            // de "sin conexión".
            val reallyNoNetwork = lastFailureWasTransient && !networkConnectivityChecker.isConnected()
            log(
                "resolveOriginalDecade('$artist' -- '$cleanTitle') -- sin año; " +
                    when {
                        reallyNoNetwork -> "SIN RED (no se pudo preguntar)"
                        lastFailureWasTransient -> "servicios de música con problemas ahora mismo, pero el teléfono SÍ tiene conexión -- se trata como sin dato"
                        else -> "apuntado en pendientes para cuando haya red"
                    } +
                    ". Se ancla por origen y género"
            )
            return TrackDecade(null, null, networkFailure = reallyNoNetwork)
        }
        anchorDictionary.learnTrackYear(artist, cleanTitle, year, "musicbrainz")
        log("resolveOriginalDecade('$artist' -- '$cleanTitle') -> $year, de MusicBrainz; aprendido en la tarjeta")
        return TrackDecade((year / 10) * 10, year)
    }


    /**
     * S025 -- artistas de un género, de cien en cien, para el recorrido
     * masivo del botón de Ajustes.
     *
     * Cada resultado trae ya nombre y país, y el género lo da la propia
     * consulta, así que una sola petición deja hasta cien artistas
     * completos para el ancla. Es lo que convierte el botón en una
     * base de datos de verdad en vez de un repaso de lo que hay en la
     * tarjeta.
     */
    suspend fun browseArtistsByGenre(
        genre: String,
        offset: Int,
    ): List<MusicBrainzArtistSummary> {
        val safe = genre.replace("\"", "")
        return musicBrainzApiService
            .searchArtists(query = "tag:\"$safe\"", limit = 100, offset = offset)
            .artists
            .also { noteSuccess() }
    }

    /**
     * S025 -- resuelve UN artista y lo guarda en el diccionario de la
     * tarjeta. Es lo que usa `AnchorDictionaryBuilder` desde el botón
     * de Ajustes, y distingue los tres desenlaces que le importan al
     * recorrido: resuelto, no existe, o no hay red.
     */
    enum class DictionaryOutcome { RESOLVED, NOT_FOUND, NETWORK_DOWN }

    suspend fun resolveArtistFactsForDictionary(name: String): DictionaryOutcome {
        val mbid = try {
            findAnchorArtistMbid(name)?.mbid
        } catch (e: Exception) {
            noteFailure(e)
            return DictionaryOutcome.NETWORK_DOWN
        }
        if (mbid == null) {
            anchorDictionary.dropPendingArtist(name)
            return DictionaryOutcome.NOT_FOUND
        }
        val detail = try {
            musicBrainzApiService.lookupArtist(mbid).also { noteSuccess() }
        } catch (e: Exception) {
            noteFailure(e)
            return DictionaryOutcome.NETWORK_DOWN
        }
        val genres = detail.genres.map { it.name.lowercase().trim() }
            .filter { it.isNotBlank() }
            .sorted()
        if (detail.country == null && genres.isEmpty()) {
            anchorDictionary.dropPendingArtist(name)
            return DictionaryOutcome.NOT_FOUND
        }
        anchorDictionary.learnArtist(
            AnchorDictionary.ArtistFacts(
                artist = name,
                country = detail.country?.trim()?.ifBlank { null },
                genres = genres,
                source = "musicbrainz",
            ),
        )
        return DictionaryOutcome.RESOLVED
    }

    /**
     * S025 -- RECONCILIACIÓN DEL CAJÓN DE SIN RED.
     *
     * Orden de Miguel Ángel: *"cuando tengamos red y estemos realizando
     * otra búsqueda, reconciliar ese artista, ese y todos los que haya
     * en el cajón de sin red. Llegar y decir: vale, tengo que buscar
     * este, tengo red, voy a ver en la cola de los que fallamos porque
     * no teníamos red. Está este, pues vamos a darle ya, aunque no sea
     * para ponerlo, pero por lo menos para tenerlo guardado en el
     * diccionario."*
     *
     * Se llama en cada vuelta de la Radio, aprovechando que ya hay red
     * probada. Resuelve un puñado por vuelta, no el cajón entero:
     * MusicBrainz admite una petición por segundo y vaciar de golpe una
     * cola de dos mil dejaría la Radio esperando. A razón de unos pocos
     * por tema escuchado, el cajón se drena solo mientras se escucha
     * música.
     *
     * No devuelve nada ni influye en lo que suena: su único efecto es
     * engordar el diccionario de la tarjeta.
     */
    suspend fun reconcilePending() {
        if (isServiceDegraded) return

        val artists = anchorDictionary.takePendingArtists(RECONCILE_PER_ROUND)
        for (name in artists) {
            val mbid = try {
                findAnchorArtistMbid(name)?.mbid
            } catch (e: Exception) {
                noteFailure(e)
                return
            }
            if (mbid == null) {
                // La red funcionó y el artista no existe en MusicBrainz.
                // Sacarlo del cajón: reintentarlo cada vuelta para
                // siempre no lo va a hacer aparecer.
                anchorDictionary.dropPendingArtist(name)
                log("reconcilePending() -- '$name' no existe en MusicBrainz; fuera del cajón")
                continue
            }
            val detail = try {
                musicBrainzApiService.lookupArtist(mbid).also { noteSuccess() }
            } catch (e: Exception) {
                noteFailure(e)
                return
            }
            val genres = detail.genres.map { it.name.lowercase().trim() }
                .filter { it.isNotBlank() }
                .sorted()
            anchorDictionary.learnArtist(
                AnchorDictionary.ArtistFacts(
                    artist = name,
                    country = detail.country?.trim()?.ifBlank { null },
                    genres = genres,
                    source = "musicbrainz",
                ),
            )
            log(
                "reconcilePending() -- '$name' resuelto y guardado en el diccionario: " +
                    "país=${detail.country ?: "?"}, géneros=[${genres.joinToString()}]"
            )
        }

        val tracks = anchorDictionary.takePending(RECONCILE_PER_ROUND)
        for (item in tracks) {
            val year = firstReleaseYearFromMusicBrainz(item.artist, item.title)
            if (year == null) {
                if (lastFailureWasTransient) return
                anchorDictionary.dropPending(item.artist, item.title)
                log(
                    "reconcilePending() -- sin fecha para '${item.artist}' - '${item.title}'; " +
                        "fuera del cajón"
                )
                continue
            }
            anchorDictionary.learnTrackYear(item.artist, item.title, year, "musicbrainz")
            log(
                "reconcilePending() -- '${item.artist}' - '${item.title}' -> $year, " +
                    "guardado en el diccionario"
            )
        }
    }

    /**
     * S025 -- año de la PRIMERA edición de la obra. Se pregunta por
     * release-group además de por grabación, y se toma el más antiguo
     * de todo lo que venga: la primera edición del tema, no la
     * recopilación que se esté escuchando.
     */
    private suspend fun firstReleaseYearFromMusicBrainz(artist: String, title: String): Int? = try {
        val safeTitle = title.replace("\"", "")
        val safeArtist = artist.replace("\"", "")
        val wantedTitle = SearchNormalizer.normalize(title)
        val wantedWords = wordsOf(wantedTitle)
        val years = mutableListOf<Int>()

        // S027 -- FALLBACK POR PALABRAS cuando la igualdad exacta no
        // encuentra nada. Caso real reportado por Miguel Ángel: el
        // vídeo traía "Divina estás" (el primer verso de la letra,
        // puesto por quien subió el vídeo a YouTube), pero la canción
        // real de Radio Futura se titula "Divina" a secas -- "estás"
        // nunca va a coincidir con el título real por muy exacta que
        // sea la comparación, aunque la canción esté perfectamente
        // documentada (álbum "Música Moderna", 1980, versión de
        // "Ballrooms of Mars" de T. Rex). Mismo mecanismo ya usado
        // para nombres de artista cortos (Beethoven -> Ludwig van
        // Beethoven): si el título real está CONTENIDO en el título
        // buscado (o al revés), se acepta -- solo si es el ÚNICO
        // candidato que cumple eso entre lo que ha devuelto
        // MusicBrainz para ese artista+consulta, para no adivinar con
        // ambigüedad.
        fun titleMatches(candidateTitle: String): Boolean {
            val got = SearchNormalizer.normalize(candidateTitle)
            if (got == wantedTitle) return true
            val gotWords = wordsOf(got)
            if (gotWords.isEmpty() || wantedWords.isEmpty()) return false
            return wantedWords.all { it in gotWords } || gotWords.all { it in wantedWords }
        }

        // S025 -- PRIMERO POR RELEASE-GROUP, que es la OBRA. Su
        // `first-release-date` es la fecha de la primera edición y no se
        // mueve porque salga una remasterización. Preguntar por
        // grabación, que era lo único que se hacía, fechó "Black Dog" de
        // Led Zeppelin en 1983.
        val releaseGroupMatches = musicBrainzApiService
            .searchReleaseGroups(query = "releasegroup:\"$safeTitle\" AND artist:\"$safeArtist\"")
            .releaseGroups
            .filter { titleMatches(it.title) }
        val releaseGroupExact = releaseGroupMatches.filter { SearchNormalizer.normalize(it.title) == wantedTitle }
        val releaseGroupChosen: List<com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzReleaseGroup> =
            if (releaseGroupExact.isNotEmpty()) releaseGroupExact
            else if (releaseGroupMatches.size == 1) releaseGroupMatches
            else emptyList()
        years += releaseGroupChosen.mapNotNull { it.firstReleaseDate?.take(4)?.toIntOrNull() }

        // Y después por grabación, que sigue valiendo para los temas que
        // nunca dieron nombre a un disco -- la mayoría de las caras B.
        val recordingMatches = musicBrainzApiService
            .searchRecordings(query = "recording:\"$safeTitle\" AND artist:\"$safeArtist\"")
            .recordings
            .filter { titleMatches(it.title) }
        val recordingExact = recordingMatches.filter { SearchNormalizer.normalize(it.title) == wantedTitle }
        val recordingChosen: List<com.miguelaetxio.mimoo.data.remote.dto.MusicBrainzRecording> =
            if (recordingExact.isNotEmpty()) recordingExact
            else if (recordingMatches.size == 1) recordingMatches
            else emptyList()
        years += recordingChosen.mapNotNull { it.firstReleaseDate?.take(4)?.toIntOrNull() }

        noteSuccess()
        years.filter { it in 1850..2100 }.minOrNull()
    } catch (e: Exception) {
        noteFailure(e)
        null
    }

    /**
     * S025 -- DISCOGS, peldaño intermedio de la fecha.
     *
     * Discogs cataloga EDICIONES FÍSICAS, no obras abstractas, y eso es
     * justo lo que hace falta: cada ficha lleva su año, así que pidiendo
     * las de un tema y quedándose con la más antigua sale la primera vez
     * que se publicó. Cubre bien lo que a MusicBrainz se le escapa,
     * sobre todo en repertorio español antiguo.
     *
     * El token viaja como secreto de repositorio `DISCOGS_TOKEN` y llega
     * al APK por `BuildConfig`. Si está vacío -- compilación local sin
     * secreto -- este peldaño se salta sin romper la cascada.
     */
    private suspend fun firstReleaseYearFromDiscogs(artist: String, title: String): Int? {
        val token = BuildConfig.DISCOGS_TOKEN
        if (token.isBlank()) return null
        return try {
            // S026 -- FALLO REAL, con captura de pantalla de Miguel
            // Ángel delante: `verifyTrackExists('Free', 'Nacho')`
            // devolvió CONFIRMADO desde este peldaño, y lo que sonó fue
            // '[Free Cover] Nacho (Número Uno)' -- un cover de un tema
            // de Nacho (artista dominicano), nada que ver con el grupo
            // británico Free. La API de Discogs es una búsqueda de
            // texto libre, no una coincidencia exacta como
            // MusicBrainz -- devolvía CUALQUIER resultado "relevante"
            // para "Free"+"Nacho" y se aceptaba el año sin comprobar
            // que el resultado tuviera algo que ver con lo pedido.
            //
            // El campo `title` que da Discogs para resultados de tipo
            // "release" viene como "Artista - Edición" -- se exige que,
            // normalizado, contenga de verdad tanto el artista como el
            // tema buscados antes de aceptar su año. Más flojo que la
            // igualdad exacta de MusicBrainz (Discogs no da por
            // separado el título del tema dentro de una edición sin
            // otra llamada), pero muy por encima de "cualquier cosa
            // relevante", que es lo que colaba antes.
            val wantedArtist = SearchNormalizer.normalize(artist)
            val wantedTitle = SearchNormalizer.normalize(title)
            val years = discogsApiService
                .search(track = title, artist = artist, token = token)
                .results
                .filter { result ->
                    val resultTitle = SearchNormalizer.normalize(result.title)
                    wantedArtist.isNotBlank() && wantedTitle.isNotBlank() &&
                        resultTitle.contains(wantedArtist) && resultTitle.contains(wantedTitle)
                }
                .mapNotNull { it.year?.take(4)?.toIntOrNull() }
                .filter { it in 1850..2100 }
            val year = years.minOrNull()
            if (year != null) {
                log("firstReleaseYearFromDiscogs('$artist' -- '$title') -> $year")
            }
            year
        } catch (e: Exception) {
            // Discogs no es MusicBrainz: que falle no debe marcar el
            // servicio como degradado ni afectar a las porciones.
            null
        }
    }

    /**
     * S025 -- WIKIDATA, último peldaño de la fecha.
     *
     * Orden de Miguel Ángel: *"si hay que ir a Wikipedia, se va."*
     *
     * Wikidata y no Wikipedia a secas porque es la parte estructurada
     * del mismo proyecto: la fecha de publicación es la propiedad
     * `P577` sobre la entidad de la OBRA, con identificador fijo, así
     * que no hay que interpretar prosa. Se pide el año más antiguo, que
     * es el de la edición original.
     *
     * La consulta cruza título del tema con nombre del artista por
     * cualquiera de las dos vías que usa Wikidata para relacionarlos:
     * intérprete (`P175`) o autor/compositor (`P86`). Con eso entra
     * tanto un tema de un grupo como una obra clásica.
     */
    private suspend fun firstReleaseYearFromWikidata(artist: String, title: String): Int? = try {
        val safeTitle = title.replace("\"", "").replace("\\", "")
        val safeArtist = artist.replace("\"", "").replace("\\", "")
        val sparql = """
            SELECT ?date WHERE {
              ?work rdfs:label ?label .
              FILTER(LCASE(STR(?label)) = LCASE("$safeTitle"))
              { ?work wdt:P175 ?who } UNION { ?work wdt:P86 ?who }
              ?who rdfs:label ?whoLabel .
              FILTER(LCASE(STR(?whoLabel)) = LCASE("$safeArtist"))
              ?work wdt:P577 ?date .
            } LIMIT 20
        """.trimIndent()
        val years = wikidataApiService.query(sparql)
            .results
            .bindings
            .mapNotNull { it["date"]?.value?.take(4)?.toIntOrNull() }
            .filter { it in 1850..2100 }
        val year = years.minOrNull()
        if (year != null) {
            log("firstReleaseYearFromWikidata('$artist' -- '$title') -> $year")
        }
        year
    } catch (e: Exception) {
        // Wikidata es el último recurso: si falla, no hay año y ya
        // está. No cuenta como fallo de servicio de MusicBrainz.
        null
    }

    suspend fun resolveAnchor(
        sourceArtist: String,
        sourceTrackTitle: String? = null,
    ): RadioAnchor? {
        if (sourceArtist.isBlank() || isPlaceholderArtist(sourceArtist)) {
            log("resolveAnchor('$sourceArtist') -- origen vacío o placeholder, se descarta sin buscar")
            return null
        }

        // S025 -- PASO 1: EL DICCIONARIO ANTES QUE LA RED.
        //
        // Orden de Miguel Ángel: *"que pongamos los Beatles, que
        // pongamos Led Zeppelin, o que pongamos Beethoven, y no
        // tengamos ni idea de lo que poner después, es para nota. Lo
        // que hay que tener es un buen diccionario para todos los
        // grandes artistas más conocidos. Y luego ya, la morralla con
        // la red."*
        //
        // Hasta S025 el orden era el contrario: se preguntaba SIEMPRE a
        // MusicBrainz y el diccionario solo entraba de rescate. Con
        // MusicBrainz caído -- trece llamadas seguidas fallidas en su
        // log -- eso dejaba sin ancla a Led Zeppelin y a Beethoven, que
        // son justo los que nunca deberían fallar.
        //
        // Ahora, si el artista está en el diccionario (semilla de 1.161
        // más todo lo aprendido en la tarjeta), el ancla se fija SIN
        // TOCAR LA RED. La red queda para lo que no está.
        // ---
        // S025 -- dictionary first, network second. If the artist is
        // known, the anchor is fixed with no network call at all.
        anchorFromDictionary(sourceArtist, sourceTrackTitle)?.let { return it }

        return try {
            // S023 -- antes esto era `.artists.firstOrNull()?.id`: se
            // aceptaba el PRIMER resultado sin comprobar que el nombre
            // devuelto se pareciera al buscado. Con nombres cortos o
            // ambiguos eso fijaba el ancla equivocada, y como el ancla
            // congela género y país desde el primer tema, el error
            // contaminaba la cadena entera y no un tema suelto:
            //
            //   Pink        -> Pink Floyd                (progressive rock)
            //   Los Ángeles -> Los Angeles Philharmonic  (classical)
            //   Burning     -> Burning Spear             (reggae)
            //
            // Nótese que 'classical' reentraba por aquí pese a haberse
            // ordenado sacarlo del todo en S016: no llegaba como género
            // de un tema, sino de un ancla mal resuelta.
            //
            // S024 -- la búsqueda ya no es un único intento con el
            // nombre entero: si ese falla, se prueba palabra por
            // palabra (ver `findAnchorArtistMbid()`).
            val sourcePick = findAnchorArtistMbid(sourceArtist)
            if (sourcePick == null) {
                // S024 -- la red FUNCIONÓ; lo que no hay es coincidencia.
                // Hay que dejarlo dicho, porque la cascada de
                // `resolveAnchorWithFallbacks()` decide si baja de
                // peldaño mirando `lastFailureWasTransient`, y si se
                // quedara sucio de un fallo anterior abortaría un
                // anclaje perfectamente legítimo.
                noteSuccess()
                return null
            }
            val sourceMbid = sourcePick.mbid

            val sourceDetail = musicBrainzApiService.lookupArtist(sourceMbid)
            noteSuccess()
            val genres = sourceDetail.genres
                .filter { it.name.isNotBlank() }
            val fromMusicBrainz = genres.map { it.name.lowercase().trim() }
                .filter { it.isNotBlank() }
                .toSet()
            // S024 -- el ancla se enriquece con lo que el DICCIONARIO
            // sabe de este artista, no solo con lo que da MusicBrainz.
            //
            // Para el bloque español MusicBrainz es pobre: de Radio
            // Futura devuelve únicamente `rock`, carpeta raíz de 129
            // descendientes. Con un ancla así `matchesGenre()` cae al
            // último peldaño y solo acepta entradas que lleven
            // literalmente `rock` -- diez artistas de los 226 que tiene
            // ya el bloque ES de los 80. El diccionario, tras el
            // enriquecimiento con Discogs, tiene de ese mismo grupo
            // `[rock, pop rock, new wave, alternative rock, synth-pop]`.
            //
            // Sin esto las 675 entradas nuevas no se aprovechan cuando
            // el ancla es justo uno de esos artistas, que es el caso
            // más frecuente: la Radio suele arrancar sobre un tema
            // conocido.
            //
            // S025 -- EL DICCIONARIO SE CONSULTA AHORA, ANTES DE
            // DECIDIR SI HAY ANCLA O NO.
            //
            // Hasta aquí el diccionario solo AMPLIABA un conjunto de
            // géneros que MusicBrainz ya hubiera dado. Si MusicBrainz
            // devolvía cero géneros se abortaba con `return null`
            // cuarenta líneas antes de llegar a preguntarle -- aunque
            // el diccionario supiera perfectamente de qué va el
            // artista. Caso real del log de S024:
            //
            //   resolveAnchor('Pistones', mbid=378eb0e2-...) -- encontrado
            //   en MusicBrainz pero SIN géneros propios -- no se puede
            //   fijar ancla
            //   ...
            //   fetchOneRadioTrack() -- ancla sin resultado en NINGUNO
            //   de los intentos -- la Radio no arranca sobre este tema
            //
            // Y en ese mismo log, 41 líneas antes, el diccionario había
            // servido `'Pistones' - 'El pistolero' (género='new wave')`.
            // El dato estaba en casa. Solo que se preguntaba tarde.
            //
            // Ahora se pregunta primero y solo se abandona el ancla si
            // NINGUNA de las dos fuentes sabe nada.
            // ---
            // S025 -- the local dictionary is now consulted BEFORE
            // deciding whether there's an anchor at all. It used to only
            // widen a genre set MusicBrainz had already provided; if
            // MusicBrainz returned none, we bailed out before ever
            // asking. We now give up only if neither source knows
            // anything about the artist.
            val fromDictionary = knownHitsRepository.genresOfArtist(sourceArtist)
                .map { it.lowercase().trim() }
                .filter { it.isNotBlank() }
                .toSet()
            if (fromMusicBrainz.isEmpty() && fromDictionary.isEmpty()) {
                log(
                    "resolveAnchor('$sourceArtist', mbid=$sourceMbid) -- sin géneros ni en " +
                        "MusicBrainz (inc=genres vacío) ni en el diccionario local -- no se puede fijar ancla"
                )
                return null
            }
            // S020 -- ancla DETERMINISTA. Antes era `genres.random()`:
            // de todos los géneros del artista se echaba a suertes uno
            // y ese decidía la sesión entera. Ahora manda el más
            // votado por la comunidad de MusicBrainz, con desempate
            // alfabético para que el mismo artista dé SIEMPRE el mismo
            // ancla (dos sesiones de Pixies deben anclarse igual).
            val chosenGenre = if (genres.isNotEmpty()) {
                val byVotes = genres
                    .sortedWith(compareByDescending<MusicBrainzGenre> { it.count }.thenBy { it.name.lowercase() })
                    .first()
                    .name
                log(
                    "resolveAnchor('$sourceArtist') -- géneros de MusicBrainz por votos: " +
                        genres.sortedByDescending { it.count }.joinToString { "${it.name}(${it.count})" } +
                        " -> elegido '$byVotes'"
                )
                byVotes
            } else {
                // S025 -- el diccionario no lleva votos de comunidad, así
                // que el desempate es alfabético a secas. Sigue siendo
                // determinista, que es lo que exige S020: el mismo
                // artista debe anclar siempre igual.
                val fromDict = fromDictionary.sorted().first()
                log(
                    "resolveAnchor('$sourceArtist') -- MusicBrainz no da géneros para este artista; " +
                        "ancla tomada del DICCIONARIO local: [${fromDictionary.joinToString()}] " +
                        "-> elegido '$fromDict'"
                )
                fromDict
            }
            val sourceCountry = sourceDetail.country?.trim()?.ifBlank { null }
            // S025 -- misma resolución de década que el camino del
            // diccionario, con la regla de coherencia incluida. Antes
            // llamaba a `resolveTrackDecade()`, que aceptaba sin
            // comprobar el primer año que devolviera MusicBrainz y por
            // eso fechó "Black Dog" en 1983.
            val trackDecade = resolveOriginalDecade(sourceArtist, sourceTrackTitle)
            val decadeBegin = trackDecade.decadeBegin
            // S013/S014, punto 4, generalizado en S026 a los cuatro
            // grupos: el grupo se decide primero por el diccionario de
            // éxitos (barato, sin ambigüedad de MusicBrainz) y, si el
            // artista no está en él, por el país que da MusicBrainz.
            val originGroup = knownHitsRepository.originGroupOfKnownArtist(sourceArtist)
                ?: OriginGroup.of(sourceCountry)
            // S025 -- lo que acaba de costar una llamada de red se
            // guarda en la tarjeta, para que la próxima vez salga del
            // diccionario y no haga falta preguntar. Es el mecanismo
            // por el que "la morralla" se va aprendiendo, en palabras
            // de Miguel Ángel.
            //
            // S027 -- SALVO si `sourcePick.isNameOverride` es true: el
            // MBID no vino del resultado más relevante de MusicBrainz,
            // sino de forzar una coincidencia exacta de nombre contra
            // uno peor situado -- necesario para casos como "Pink"
            // (S023), pero de menor confianza. Caso real que motiva
            // esto: 'Fritz' resolvió a 'Fritz Kalkbrenner' (Alemania)
            // por esta vía, y al aprenderse quedó fijado para siempre
            // en el diccionario con país alemán bajo la clave corta
            // 'fritz' -- coló un artista alemán en TODAS las sesiones
            // futuras ancladas en España/Hispanoamérica, rompiendo la
            // pared de origen. Sin aprender aquí, la próxima vez que
            // aparezca 'Fritz' se vuelve a preguntar a la red en vez
            // de arrastrar un acierto de una sola vez como si fuera
            // dato fijo.
            if (!sourcePick.isNameOverride) {
                anchorDictionary.learnArtist(
                    AnchorDictionary.ArtistFacts(
                        artist = sourceArtist,
                        country = sourceCountry,
                        genres = (fromMusicBrainz + fromDictionary).sorted(),
                        source = "musicbrainz",
                    ),
                )
            } else {
                log(
                    "resolveAnchor('$sourceArtist') -- NO se aprende en el diccionario: el mbid vino " +
                        "de una coincidencia de nombre forzada, no del resultado más relevante " +
                        "(riesgo de homónimo, caso real: 'Fritz' -> 'Fritz Kalkbrenner')"
                )
            }
            val allGenres = fromMusicBrainz + fromDictionary
            if (fromDictionary.isNotEmpty() && fromMusicBrainz.isNotEmpty()) {
                log(
                    "resolveAnchor('$sourceArtist') -- géneros del diccionario añadidos al ancla: " +
                        "[${(fromDictionary - fromMusicBrainz).joinToString()}]"
                )
            }
            val isClassical = allGenres.any {
                it == "classical" || genreTree.isDescendantOf(it, "classical")
            }
            // S025 -- en clásica NI origen NI década. Orden de Miguel
            // Ángel: *"en clásica es clásica. No tenemos ni origen ni
            // década, solo género"*. S024 ya había sacado el país; la
            // década seguía filtrando, y no debe: el repertorio clásico
            // se escucha como un solo cuerpo, y fechar a Beethoven en
            // una década deja fuera a Vivaldi y a Debussy sin ninguna
            // razón musical.
            // ---
            // S025 -- classical takes neither origin nor decade, only
            // genre. S024 removed the country; the decade was still
            // filtering and shouldn't.
            val effectiveDecade = if (isClassical) null else decadeBegin
            val effectiveYear = if (isClassical) null else trackDecade.exactYear
            log(
                "resolveAnchor('$sourceArtist') -> ancla fijada para toda la sesión: " +
                    "género='$chosenGenre', país=$sourceCountry, grupo=${originGroup ?: "?"}, " +
                    "década=$effectiveDecade, año=${effectiveYear ?: "?"}, clásica=$isClassical" +
                    (if (isClassical) " (ni el origen ni la década filtran)" else "") +
                    ", géneros=[${allGenres.joinToString()}]"
            )
            RadioAnchor(
                genre = chosenGenre,
                genres = allGenres.ifEmpty { setOf(chosenGenre.lowercase()) },
                country = sourceCountry,
                decadeBegin = effectiveDecade,
                anchorYear = effectiveYear,
                decadeUnknownDueToNetwork = trackDecade.networkFailure,
                originGroup = originGroup,
                isClassical = isClassical,
            )
        } catch (e: Exception) {
            noteFailure(e)
            // S025 -- el artista no está en el diccionario Y no se ha
            // podido preguntar. Al cajón, para resolverlo en cuanto
            // vuelva la red aunque sea en otra sesión.
            if (lastFailureWasTransient) anchorDictionary.rememberPendingArtist(sourceArtist)
            log("resolveAnchor('$sourceArtist') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Cupo de "exploración" (10%, S013/S014) -- ver comentario de
     * clase para la cascada exacta. El origen (`anchor.originGroup`,
     * S026 -- pared del grupo, dentro del grupo cualquier país vale)
     * se mantiene FIJO en las tres vueltas de la cascada, nunca se
     * relaja aquí. `excludeArtists` son los nombres ya usados en esta
     * sesión.
     * `avoidArtists` (S016, `RadioSessionHistoryManager`): preferencia
     * SUAVE entre sesiones -- si evitarlos deja una vuelta de la
     * cascada sin candidatos, se ignora para esa vuelta y se elige
     * igual de ella, nunca se salta una vuelta entera por esto.
     */
    suspend fun suggestRelatedArtist(
        anchor: RadioAnchor,
        excludeArtists: Set<String>,
        avoidArtists: Set<String> = emptySet(),
        /**
         * S025 -- desplazamiento dentro del catálogo. La exploración no
         * se agota: cuando una página no da nada nuevo, la Radio avanza
         * y sigue pidiendo. MusicBrainz tiene dos millones de artistas.
         */
        offset: Int = 0,
        /** S026 -- ver KnownHitsRepository.randomHit(). */
        genreMatchThresholdPercent: Int = 40,
    ): String? {
        val excludeLower = excludeArtists.map { it.lowercase() }.toSet()
        val avoidLower = avoidArtists.map { it.lowercase() }.toSet()

        // S025 -- LA BASE DE DATOS CONSTRUIDA SE CONSULTA PRIMERO.
        //
        // Orden de Miguel Ángel, y llevaba razón en el fondo del asunto:
        // *"quiero que la base de datos que he construido se use de una
        // vez."* Hasta aquí `suggestRelatedArtist()` iba siempre en vivo
        // a MusicBrainz, ignorando por completo los miles de artistas
        // -- con país y géneros ya resueltos -- que el botón de Ajustes
        // guardó en la tarjeta. Media hora de recorrido para nada.
        //
        // Ahora se pregunta primero a `AnchorDictionary`, que es
        // instantáneo: sin red, sin el límite de una petición por
        // segundo de MusicBrainz. Solo si de ahí no sale nada -- la
        // base todavía no cubre ese género, o aún no se ha construido --
        // se cae a la búsqueda en vivo, exactamente como antes.
        val localGenres = (listOf(anchor.genre) + anchor.genres).toSet()
        val fromDictionary = anchorDictionary
            .artistsMatching(localGenres, anchor.originGroup)
            .filter { it.name.lowercase() !in excludeLower }
            // S026 -- SALVAGUARDA "CONOCIDO EN ESPAÑA" dentro de
            // Hispanoamérica. La semilla de Exploración no tiene
            // curación de fama -- es solo género+país de MusicBrainz,
            // a diferencia del diccionario de éxitos (`es`+`intl`), que
            // SÍ está curado para "conocido en España" (ver
            // `KnownHitsRepository.isKnownArtistAnywhere()`). Orden de
            // Miguel Ángel, con ejemplo: *"si en España Shakira es
            // número 1... Shakira debe ser preferente junto a los
            // demás éxitos en español... y que no me salga Karumanta,
            // que es un éxito en Perú."* Con ancla española, un
            // candidato hispanoamericano que NO sea de España tiene
            // que estar además en el diccionario de éxitos -- si no,
            // fuera. No aplica a candidatos españoles (siempre
            // admitidos, es su propio país) ni a otros grupos
            // (Anglosajona no tiene esta curación de fama por país, y
            // Miguel Ángel pidió explícitamente que ahí el país no
            // pesara nada).
            .filter { candidate ->
                if (anchor.country == "ES" && anchor.originGroup == OriginGroup.HISPANOAMERICA &&
                    candidate.country != "ES"
                ) {
                    knownHitsRepository.isKnownArtistAnywhere(candidate.name)
                } else {
                    true
                }
            }
        // S026 -- entre los candidatos de la base de datos, filtro
        // único por PORCENTAJE de intersección/unión de géneros
        // específicos (ver GenreMatchQuality), configurable en Ajustes.
        // Motivo real, con log delante: el ancla de Led Zeppelin trae
        // varios géneros, y Emerson, Lake & Palmer solo comparte
        // 'progressive rock' -- una intersección demasiado pequeña
        // frente al total de géneros de ambos.
        if (fromDictionary.isNotEmpty()) {
            val scored = fromDictionary.map { candidate ->
                Triple(
                    candidate.name,
                    candidate.genres,
                    GenreMatchQuality.of(candidate.genres, anchor.genres, genreTree, genreMatchThresholdPercent),
                )
            }
            val matching = scored.filter { it.third.matches }
            val names = matching.map { it.first }
            if (names.isNotEmpty()) {
                val preferredLocal = names.filter { it.lowercase() !in avoidLower }
                val chosenLocal = preferredLocal.ifEmpty { names }.random()
                log(
                    "suggestRelatedArtist(género='${anchor.genre}', país=${anchor.country ?: "?"}) -> " +
                        "'$chosenLocal' (${names.size}/${fromDictionary.size} candidatos DE LA BASE DE DATOS, " +
                        "sin red, umbral=$genreMatchThresholdPercent%)"
                )
                return chosenLocal
            }
            log(
                "suggestRelatedArtist(género='${anchor.genre}', país=${anchor.country ?: "?"}) -- " +
                    "${fromDictionary.size} candidatos de la base de datos, ninguno llega al " +
                    "$genreMatchThresholdPercent% de coincidencia -- se cae a la búsqueda en vivo"
            )
        }

        // S020 -- cascada de DOS peldaños, nunca tres. El tercero
        // (`findCandidatesAnyGenre`: mantener década, soltar el género)
        // se elimina por la regla suprema de Miguel Ángel: "el género no
        // se abandona nunca".
        //
        // S021 -- y ahora tampoco quedan DOS: vuelta ÚNICA. El segundo
        // peldaño mantenía el género pero soltaba la década
        // (`decadeBegin = null`), lo que contradecía la otra mitad de la
        // misma regla: *"siempre se respeta género y década, siempre"*.
        // `findCandidates()` ya omite el rango de fechas en la consulta
        // a MusicBrainz cuando `decadeBegin` es null, así que pasarle
        // directamente `anchor.decadeBegin` cubre los dos casos: ancla
        // con década (se respeta) y ancla sin ella (no hay nada que
        // respetar). Mismo cambio y misma razón que en
        // KnownHitsRepository.randomHit() y en
        // PlayerManager.pickDiscoCandidate().
        // S025 -- se pregunta por TODOS los géneros del ancla, no por
        // uno suelto.
        //
        // Miguel Ángel: *"me he hinchado a escuchar rock sinfónico
        // después de poner un tema de Led Zeppelin, que no me apetecía
        // en absoluto."* En su log el ancla salió `arena rock`, que es
        // el primero por orden alfabético entre los géneros concretos
        // de Led Zeppelin -- un desempate arbitrario mío. Preguntar por
        // `arena rock` en Reino Unido en los 70 devolvía UN candidato,
        // 34 rondas seguidas, así que la Radio tiraba de lo poco que
        // encontrase, y ahí entraban Yes, ELO y Pink Floyd.
        //
        // Con el conjunto completo -- hard rock, blues rock, heavy
        // metal, classic rock... -- la consulta trae decenas de
        // británicos de los 70 y deja de depender de una etiqueta
        // suelta elegida por azar alfabético.
        val candidates = findCandidatesForGenres(
            listOf(anchor.genre) + anchor.genres.filter { it != anchor.genre },
            anchor.originGroup,
            anchor.decadeBegin,
            excludeLower,
            anchor.isClassical,
            offset,
        )
        val preferred = candidates.filter { it.lowercase() !in avoidLower }
        val chosen = preferred.ifEmpty { candidates }.randomOrNull()
        if (chosen == null) {
            log(
                "suggestRelatedArtist(género='${anchor.genre}', país=${anchor.country ?: "?"}, " +
                    "década=${anchor.decadeBegin}) -- 0 candidatos en la vuelta única género+país+década " +
                    "(tras excluir ${excludeArtists.size} ya usados) -- eslabón roto para este cupo"
            )
        } else {
            log(
                "suggestRelatedArtist(género='${anchor.genre}', país=${anchor.country ?: "?"}, " +
                    "década=${anchor.decadeBegin}) -> '$chosen' (${candidates.size} candidatos)"
            )
        }
        return chosen
    }

    /**
     * S026 -- expone `GenreMatchQuality` a `PlayerManager` (porción
     * DISCO), que no tiene acceso directo a `GenreTree`. Mismo umbral
     * por porcentaje que en Conocidos y Exploración -- ver
     * `GenreMatchQuality`.
     */
    fun genreMatchQuality(
        anchor: RadioAnchor,
        candidateGenres: Set<String>,
        thresholdPercent: Int = 40,
    ): GenreMatchQuality.Result =
        GenreMatchQuality.of(candidateGenres, anchor.genres, genreTree, thresholdPercent)

    /**
     * S013/S014, punto 8 -- fuente de "disco" (10%, biblioteca local
     * sin género/país/década guardados): resuelve el perfil completo
     * de un artista bajo demanda, para que PlayerManager pueda
     * comprobar si contiene el género del ancla sin descartar
     * artistas por elegir un único género al azar (a diferencia de
     * resolveAnchor(), que sí necesita reducir a uno solo).
     */
    suspend fun lookupArtistProfile(artistName: String): ArtistProfile? {
        if (artistName.isBlank() || isPlaceholderArtist(artistName)) return null
        return try {
            // S023 -- mismo arreglo que en resolveAnchor(): se
            // comprueba que el candidato devuelto SEA el artista
            // buscado, en vez de aceptar el primero que llegue.
            val mbid = pickAnchorArtist(
                artistName,
                musicBrainzApiService
                    .searchArtists(
                        query = buildArtistQuery(artistName),
                        limit = ANCHOR_SEARCH_LIMIT,
                    )
                    .artists,
            )?.mbid ?: return null
            val detail = musicBrainzApiService.lookupArtist(mbid)
            noteSuccess()
            val genres = detail.genres.map { it.name }.filter { it.isNotBlank() }.toSet()
            ArtistProfile(
                genres = genres,
                country = detail.country?.trim()?.ifBlank { null },
                decadeBegin = parseDecadeBegin(detail.lifeSpan?.begin),
            )
        } catch (e: Exception) {
            noteFailure(e)
            log("lookupArtistProfile('$artistName') -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * S025 -- une los candidatos de TODOS los géneros del ancla,
     * parando en cuanto hay material de sobra. Ver el comentario de
     * `suggestRelatedArtist()` para por qué un solo género no basta.
     */
    private suspend fun findCandidatesForGenres(
        genres: List<String>,
        originGroup: OriginGroup?,
        decadeBegin: Int?,
        excludeLower: Set<String>,
        isClassical: Boolean,
        offset: Int = 0,
    ): List<String> {
        val all = linkedSetOf<String>()
        for (genre in genres) {
            all += findCandidates(genre, originGroup, decadeBegin, excludeLower, isClassical, offset)
            if (all.size >= ENOUGH_CANDIDATES) break
        }
        return all.toList()
    }

    private suspend fun findCandidates(
        genre: String,
        originGroup: OriginGroup?,
        decadeBegin: Int?,
        excludeLower: Set<String>,
        isClassical: Boolean,
        offset: Int = 0,
    ): List<String> = try {
        val query = buildGenreQuery(genre, originGroup, decadeBegin, isClassical)
        // S010 -- offset aleatorio, no siempre 0, para variar entre
        // sesiones de Radio con el mismo ancla.
        //
        // S025 -- y sumándole el AVANCE de la exploración, que crece
        // cada vez que una página no da nada nuevo. Así la porción de
        // desconocidos recorre el catálogo entero en vez de dar vueltas
        // sobre los mismos cien primeros. Es lo que la hace inagotable,
        // que es su papel: sostener la Radio cuando el diccionario ya
        // no tiene temas sin estrenar.
        val randomOffset = offset + (0..90 step 10).toList().random()

        suspend fun fetch(offset: Int) = musicBrainzApiService
            .searchArtists(query = query, limit = 25, offset = offset)
            .artists
            .map { it.name }
            .filter { it.lowercase() !in excludeLower && !isPlaceholderArtist(it) }

        var found = fetch(randomOffset)
        // S024 -- el offset aleatorio se pasa de largo cuando el
        // conjunto es pequeño, y ahí devuelve vacío. Verificado en log
        // real con ancla 'Radio Futura' (rock/ES/1980): la misma
        // consulta dio 10 y 9 candidatos, y a la vuelta siguiente 0
        // "tras excluir 3 ya usados" -- de 10 a 0 quitando 3 no cuadra.
        // No era que MusicBrainz se hubiera quedado sin artistas: era
        // que el offset había caído más allá del final. El código lo
        // leía como "eslabón roto para este cupo" y mataba la porción.
        if (found.isEmpty() && randomOffset > 0) {
            log(
                "findCandidates('$genre') -- vacío con offset $randomOffset; " +
                    "se reintenta desde el principio por si el desplazamiento se pasó del final"
            )
            found = fetch(0)
        }
        // El servicio ha respondido. Que la lista venga vacía es una
        // respuesta legítima, no un fallo: el contador se reinicia
        // igual.
        noteSuccess()
        found
    } catch (e: Exception) {
        noteFailure(e)
        log("findCandidates(género='$genre', grupo=${originGroup ?: "?"}, década=$decadeBegin) -- EXCEPCIÓN: ${e::class.java.simpleName}: ${e.message}")
        emptyList()
    }

    /**
     * S025 -- EL ORIGEN LO MARCA EL PAÍS DEL PRIMER TEMA, SEA EL QUE
     * SEA.
     *
     * Orden de Miguel Ángel, repetida hasta el hartazgo y con razón:
     * *"la canción inicial marca el ancla para toda la sesión, pero no
     * solamente la música española, que ya lo tenemos bien, sino en
     * todo: origen, género y década, en música española Y en música
     * extranjera"*.
     *
     * Hasta aquí el origen no era un país sino un BOOLEANO. S020
     * separó España y extranjero en los dos sentidos: ancla española
     * -> `country:ES`; ancla no española -> `NOT country:ES`. Para una
     * sesión española funciona; para cualquier otra, "no español" es
     * el mundo entero menos un país, o sea no filtrar. Verificado en
     * el log de S025, ancla Led Zeppelin (GB), hard rock, 1980:
     *
     *   suggestRelatedArtist(género='hard rock', origen_es=false, década=1980)
     *     -> '人間椅子'      (Japón)
     *     -> 'B’z'           (Japón)
     *     -> 'Ария'          (Rusia)
     *     -> 'Böhse Onkelz'  (Alemania)
     *     -> 'The 69 Eyes'   (Finlandia)
     *
     * Ahora la cláusula es `country:<país del ancla>`, igual de dura
     * para GB que para ES. Si MusicBrainz no da país del artista ancla
     * se mantiene el comportamiento antiguo como respaldo: es lo único
     * honesto cuando el dato no existe, y así una sesión española no
     * pierde el filtro que ya tenía.
     *
     * S024/S025 -- salvo en repertorio clásico, donde NO se pone
     * cláusula de país NI de década. Orden de Miguel Ángel: *"en
     * clásica es clásica; no tenemos ni origen ni década, solo
     * género"*. Ver `RadioAnchor.isClassical`.
     * ---
     * S026 -- REDISEÑO POR GRUPO (Iberoamericana/Anglosajona/Europea/
     * Mundial, ver `OriginGroup`), sustituyendo el país exacto de S025.
     * Orden explícita de Miguel Ángel: *"prefiero que Led Zeppelin me
     * traiga a Van Halen o AC/DC antes que rebuscar en GB"* -- dentro
     * de un grupo, el país exacto del ancla NO manda nada; se abre a
     * TODO el grupo desde el principio (Anglosajona: GB, US, AU, IE,
     * NZ, CA), no como sustituto de última hora. La pared sigue siendo
     * absoluta ENTRE grupos -- lo único que cambia es que ya no hay
     * preferencia por el país exacto dentro del propio grupo.
     *
     * Mundial no tiene lista cerrada de países (es el cajón de lo que
     * no encaja en los otros tres) -- para ese grupo se construye la
     * cláusula por EXCLUSIÓN de los otros tres, igual que antes se
     * excluía solo España.
     */
    private fun buildGenreQuery(
        genre: String,
        originGroup: OriginGroup?,
        decadeBegin: Int?,
        isClassical: Boolean,
    ): String {
        fun escape(value: String) = value.replace("\"", "")
        var query = "tag:\"${escape(genre)}\""
        if (!isClassical) {
            query += when (originGroup) {
                null -> ""
                OriginGroup.MUNDIAL -> {
                    val otherCountries = OriginGroup.entries
                        .filter { it != OriginGroup.MUNDIAL }
                        .flatMap { OriginGroup.countriesOf(it) }
                    " AND NOT (" + otherCountries.joinToString(" OR ") { "country:$it" } + ")"
                }
                else -> {
                    val countries = OriginGroup.countriesOf(originGroup)
                    " AND (" + countries.joinToString(" OR ") { "country:$it" } + ")"
                }
            }
            if (decadeBegin != null) {
                // S027 -- QUITADO. Esta cláusula filtraba por
                // `begin:[decadeBegin TO decadeBegin+9]`, el campo de
                // MusicBrainz para el ARTISTA: fecha de nacimiento si
                // es solista, de formación si es grupo. Exactamente
                // el mismo fallo que P!nk/life-span, ya identificado y
                // ya arreglado para `resolveAnchor()` (la década del
                // propio ancla) -- pero nunca se tocó aquí, en la
                // búsqueda de candidatos en vivo. Caso real: Namika,
                // nacida en 1991, entraba como candidata para una
                // sesión de "boom bap 1990" por su fecha de
                // nacimiento, no porque hiciera nada en los 90 --
                // empezó a publicar en 2015. La directriz de Miguel
                // Ángel es una sola, sin excepciones: la década sale
                // SIEMPRE del tema, nunca del nacimiento o formación
                // del artista. `verifyTrackExists()` /
                // `resolveOriginalDecade()` ya fechan el TEMA concreto
                // más abajo, en `resolveYoutubeCandidate()` -- ahí es
                // donde debe decidirse la década, y solo ahí.
            }
        }
        return query
    }

    /**
     * S025 -- restaurada. Se perdió al retirar `resolveTrackDecade()`
     * en este mismo hito: las dos vivían pegadas y el borrado se llevó
     * la de al lado. `lookupArtistProfile()` la sigue necesitando.
     *
     * Deriva la década del `life-span.begin` del ARTISTA, que es el año
     * de formación de un grupo o el de NACIMIENTO de un solista. Para
     * el ancla eso está mal y por eso el ancla ya no la usa -- usa
     * `resolveOriginalDecade()`, que fecha el TEMA. Aquí sobrevive
     * porque la porción de disco solo necesita situar a grandes rasgos
     * a un artista de la biblioteca local, no anclar una sesión.
     * ---
     * S025 -- restored; it was collateral damage from removing
     * `resolveTrackDecade()`. Derives a decade from the ARTIST's
     * life-span, which is wrong for anchoring (hence
     * `resolveOriginalDecade()`) but adequate for roughly placing a
     * local-library artist in the disco quota.
     */
    private fun parseDecadeBegin(begin: String?): Int? {
        val year = begin?.take(4)?.toIntOrNull() ?: return null
        return (year / 10) * 10
    }

    /**
     * Quita del título el ruido que trae YouTube y que impediría casar
     * el tema con MusicBrainz o con el diccionario: "(Official Video)",
     * "[Lyric Video]", "(Remastered 2011)" y compañía. También corta un
     * prefijo "Artista - " si viene pegado delante, que es la forma
     * habitual en que YouTube titula los vídeos musicales.
     */
    /**
     * Qué artista y qué canción hay dentro de un título, cuando el
     * título NO trae el patrón "Artista - Tema" (S023).
     *
     * **Por qué existe.** Miguel Ángel puso un vídeo titulado
     * `Led Zeppelin Immigrant song`, subido por un canal llamado
     * `oldschoolrockerkid`. El artista y la canción estaban los dos ahí
     * delante, y la Radio no los vio: `parseArtistFromTitle()` solo
     * parte por `" - "`, y ese título no lleva guion. Al no encontrar
     * nada, la sesión acabó anclándose en un artista sorteado al azar
     * de la biblioteca local.
     *
     * **La idea es suya**, y es la correcta: si el título no viene
     * partido, hay que partirlo nosotros por palabras y preguntar. De
     * `Led Zeppelin Immigrant song` salen cuatro; `Led Zeppelin` casa
     * con un artista real y lo que sobra, `Immigrant song`, es la
     * canción.
     *
     * **Se prueban prefijos, del más largo al más corto.** El orden
     * importa: buscar primero lo corto encontraría `Led`, que también
     * existe, y perderíamos `Led Zeppelin`. El primero que case gana, y
     * casar significa que MusicBrainz devuelve un artista con ESE
     * nombre -- misma comprobación que [pickAnchorArtist], no el primer
     * resultado que llegue.
     *
     * Coste acotado: como mucho [MAX_TITLE_WORDS_FOR_ARTIST]
     * peticiones, y solo al arrancar una sesión de Radio.
     */
    data class TitleIdentification(val artist: String, val song: String?)

    suspend fun identifyFromTitleWords(rawTitle: String?): TitleIdentification? {
        if (rawTitle.isNullOrBlank()) return null
        // Se quitan paréntesis y corchetes ("(Official Video)",
        // "[HD]"), pero NO se corta por " - ": aquí el título entero es
        // el material de trabajo.
        val cleaned = rawTitle
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("[-–—_|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val words = cleaned.split(" ").filter { it.isNotBlank() }
        if (words.size < 2) return null

        val longest = minOf(MAX_TITLE_WORDS_FOR_ARTIST, words.size - 1)
        for (take in longest downTo 1) {
            val candidate = words.take(take).joinToString(" ")
            if (candidate.length <= 2) continue
            val matched = try {
                val hits = musicBrainzApiService
                    .searchArtists(
                        query = buildArtistQuery(candidate),
                        limit = ANCHOR_SEARCH_LIMIT,
                    )
                    .artists
                pickAnchorArtist(candidate, hits)?.let { candidate }
            } catch (e: Exception) {
                // Un 503 aquí no significa "estas palabras no son un
                // artista". Se corta la búsqueda entera en vez de
                // seguir probando prefijos más cortos y quedarse con
                // uno peor por casualidad.
                noteFailure(e)
                log("identifyFromTitleWords('$rawTitle') -- ${e::class.java.simpleName} probando '$candidate', se abandona")
                return null
            }
            if (matched != null) {
                val song = words.drop(take).joinToString(" ").ifBlank { null }
                log("identifyFromTitleWords('$rawTitle') -> artista='$candidate', canción='$song' (probados ${longest - take + 1} prefijos)")
                return TitleIdentification(candidate, song)
            }
        }
        log("identifyFromTitleWords('$rawTitle') -- ningún prefijo de hasta $longest palabras casa con un artista de MusicBrainz")
        return null
    }

    private fun stripTitleNoise(rawTitle: String): String {
        val withoutBrackets = rawTitle
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
        val withoutArtistPrefix = withoutBrackets.substringAfter(" - ", withoutBrackets)
        // S027 -- orden textual de Miguel Ángel: "no me vale Remaster
        // 1970, no me vale Remaster 2025... es el título de la canción
        // y el nombre del grupo". Ya se quitaba "(2015 Remastered
        // Version)" por ir entre paréntesis -- esto cubre la misma
        // coletilla SIN paréntesis, al final del título ("Nombre del
        // tema Remaster 1970", "... Remasterizado 2025"), que no es
        // parte del título real de la obra y solo ensucia la búsqueda
        // de fecha y la comparación exacta.
        val withoutRemaster = withoutArtistPrefix
            .replace(Regex("(?i)\\s+remaster(ed|izado)?\\s*\\d{0,4}\\s*$"), "")
        // S027 -- bug real reportado por Miguel Ángel: "Divina estás."
        // (con punto final, tal cual venía del título del vídeo) nunca
        // encontraba año en ninguna fuente, mientras que la misma
        // canción sin el punto sí debería. La comparación de Wikidata
        // es una igualdad EXACTA de SPARQL
        // (`FILTER(LCASE(STR(?label)) = LCASE("..."))`) -- un punto,
        // coma o puntos suspensivos sueltos al final, que son
        // normalísimos en títulos de vídeo pero casi nunca forman
        // parte del título real de la obra, bastan para que esa
        // igualdad falle siempre, aunque el tema exista y esté bien
        // documentado. Se quita aquí, en el punto único donde se
        // limpia el título antes de cualquier búsqueda.
        return withoutRemaster
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ',', ';', ':', '!', '¡', '?', '¿', '…', '-')
            .trim()
    }

    private fun log(line: String) = RadioDebugLogger.log(appContext, storageManager, line)

    private fun isPlaceholderArtist(name: String): Boolean =
        name.equals("Various Artists", ignoreCase = true) ||
            name.equals("[unknown]", ignoreCase = true) ||
            name.equals("[anonymous]", ignoreCase = true) ||
            name.equals("[traditional]", ignoreCase = true)

    private fun buildArtistQuery(artist: String): String {
        fun escape(value: String) = value.replace("\"", "")
        return "artist:\"${escape(artist)}\""
    }

    /**
     * Lista de desambiguación cargada de `artist_disambiguation.json`
     * (S023). Se lee una sola vez y se conserva; es un asset del APK,
     * no cambia en ejecución.
     */
    private data class Disambiguation(
        /** Nombre normalizado -> MBID fijado a mano. */
        val forced: Map<String, String>,
        /** Normalizados sin MBID: MusicBrainz no tiene al artista. */
        val blocked: Set<String>,
        /** Normalizado -> nombre canónico que MusicBrainz devuelve. */
        val confirmed: Map<String, String>,
    )

    private val disambiguation: Disambiguation by lazy { loadDisambiguation() }

    private fun loadDisambiguation(): Disambiguation {
        return try {
            val json = appContext.assets.open("artist_disambiguation.json")
                .bufferedReader()
                .use { it.readText() }
            val root = org.json.JSONObject(json)

            val forced = mutableMapOf<String, String>()
            val blocked = mutableSetOf<String>()
            val wrong = root.optJSONObject("incorrectos")
            wrong?.keys()?.forEach { name ->
                val key = SearchNormalizer.normalizeArtistName(name)
                val mbid = wrong.optJSONObject(name)?.optString("mbid").orEmpty()
                if (mbid.isNotBlank() && mbid != "null") forced[key] = mbid else blocked += key
            }

            val confirmed = mutableMapOf<String, String>()
            val ok = root.optJSONObject("confirmados")
            ok?.keys()?.forEach { name ->
                confirmed[SearchNormalizer.normalizeArtistName(name)] =
                    SearchNormalizer.normalizeArtistName(ok.optString(name))
            }

            log("desambiguación cargada -- ${forced.size} con MBID fijado, ${blocked.size} sin resolver, ${confirmed.size} confirmados")
            Disambiguation(forced, blocked, confirmed)
        } catch (e: Exception) {
            // Sin la lista se sigue funcionando: lo que se pierde son
            // las correcciones manuales, no la comprobación de nombre.
            log("desambiguación NO disponible (${e.javaClass.simpleName}) -- se sigue con verificación de nombre")
            Disambiguation(emptyMap(), emptySet(), emptyMap())
        }
    }

    /**
     * S027 -- resultado de [pickAnchorArtist]: el MBID elegido, y si
     * hubo que DESCARTAR el resultado más relevante de MusicBrainz
     * para forzar una coincidencia exacta de nombre. Ese descarte es
     * necesario para casos como "Pink" (S023, no aceptar por defecto
     * "Pink Floyd" solo por ser más conocido), pero es también la
     * misma vía por la que se coló 'Fritz' -> 'Fritz Kalkbrenner'
     * (Alemania) en una sesión anclada en España/Hispanoamérica: el
     * nombre corto encontró coincidencia exacta con un artista que NO
     * era el más relevante, y punto de menor confianza. Ver el uso en
     * `resolveAnchor()`: cuando `isNameOverride` es true no se aprende
     * en el diccionario -- se usa solo para esta resolución.
     */
    private data class AnchorArtistPick(val mbid: String, val isNameOverride: Boolean)

    /**
     * Elige el artista del que se va a fijar el ancla, en vez de
     * aceptar el primer resultado (S023).
     *
     * Orden: primero la corrección manual, si la hay; después el
     * primer candidato cuyo nombre coincida de verdad con el buscado,
     * ya plegados acentos y tipografía por `normalizeArtistName()`
     * -- que es lo que hace que 'Guns N'Roses' case con
     * 'Guns N' Roses' y 'a‐ha' con 'a-ha'.
     *
     * Si ningún candidato coincide se devuelve null y NO se ancla.
     * Preferimos quedarnos sin radio a construir una cadena entera
     * sobre un artista que no es.
     */
    private fun pickAnchorArtist(
        sourceArtist: String,
        candidates: List<MusicBrainzArtistSummary>,
        quiet: Boolean = false,
    ): AnchorArtistPick? {
        val wanted = SearchNormalizer.normalizeArtistName(sourceArtist)

        disambiguation.forced[wanted]?.let { mbid ->
            log("resolveAnchor('$sourceArtist') -- MBID fijado a mano ($mbid), no se usa la búsqueda")
            return AnchorArtistPick(mbid, isNameOverride = false)
        }
        if (wanted in disambiguation.blocked) {
            log("resolveAnchor('$sourceArtist') -- artista marcado como no resoluble en MusicBrainz, no se fija ancla")
            return null
        }

        if (candidates.isEmpty()) {
            if (!quiet) {
                log("resolveAnchor('$sourceArtist') -- MusicBrainz no encontró NINGÚN artista con ese nombre (searchArtists vacío)")
            }
            return null
        }

        val canonical = disambiguation.confirmed[wanted]
        // `normalize()` BORRA la puntuación en vez de sustituirla por
        // espacio, así que 'M-Clan' queda como "mclan" y 'M Clan' como
        // "m clan": el mismo grupo, y no casaban. Verificado en log
        // real de S023 -- se rechazó a M-Clan, que era el correcto.
        // Comparar además sin espacios cierra ese hueco sin tocar
        // `normalize()`, que lo usan también H12 y favoritos.
        fun tight(value: String) = value.replace(" ", "")
        val match = candidates.firstOrNull { candidate ->
            val got = SearchNormalizer.normalizeArtistName(candidate.name)
            got == wanted ||
                tight(got) == tight(wanted) ||
                sameWords(got, wanted) ||
                (canonical != null && (got == canonical || tight(got) == tight(canonical)))
        }

        if (match == null) {
            if (!quiet) {
                log(
                    "resolveAnchor('$sourceArtist') -- ningún candidato coincide con el nombre buscado; " +
                        "descartados: ${candidates.joinToString(", ") { it.name }}. No se fija ancla."
                )
            }
            return null
        }
        // S027 -- cuando el elegido NO es el primero (el más relevante
        // para MusicBrainz), es una coincidencia de nombre forzada:
        // necesaria para casos como "Pink" (S023), pero de MENOR
        // confianza -- puede ser un artista homónimo sin relación real
        // con lo que se buscaba (caso real: 'Fritz' -> 'Fritz
        // Kalkbrenner', Alemania, colado en una sesión de España). El
        // llamante decide con `isNameOverride` si esto es lo bastante
        // fiable para APRENDERSE en el diccionario o solo vale para
        // esta resolución puntual.
        val isNameOverride = match !== candidates.first()
        if (isNameOverride) {
            log("resolveAnchor('$sourceArtist') -- se descarta '${candidates.first().name}' y se toma '${match.name}' por coincidencia de nombre")
        }
        return AnchorArtistPick(match.id, isNameOverride)
    }

    /**
     * ¿Son los dos nombres las MISMAS palabras, en cualquier orden?
     *
     * S024, regla de Miguel Ángel: *"las búsquedas no se deben ni de
     * invertir, ni de esto ni de lo otro -- se debe buscar por
     * palabras"*.
     *
     * La etiqueta de un archivo trae a menudo el nombre en formato de
     * catálogo, `Apellido, Nombre`, que MusicBrainz no conoce:
     * `Beethoven, Ludwig van` no existe, `Ludwig van Beethoven` sí. En
     * el log de S023 ese nombre se buscó doce veces y volvió vacío las
     * doce.
     *
     * Comparar CONJUNTOS de palabras lo resuelve sin ninguna regla
     * especial para las comas, y sin invertir nada:
     *
     *     'Beethoven, Ludwig van'    -> {beethoven, ludwig, van}
     *     'Ludwig van Beethoven'     -> {ludwig, van, beethoven}   IGUAL
     *
     * Y no reabre lo que cerró S023, porque exige igualdad y no
     * inclusión: sobra una palabra y ya no cuela.
     *
     *     'Los Ángeles'              -> {los, angeles}
     *     'Los Angeles Philharmonic' -> {los, angeles, philharmonic}  NO
     *     'Pink' -> {pink}   vs   'Pink Floyd' -> {pink, floyd}       NO
     *
     * Generaliza estrictamente la comparación exacta que ya había: dos
     * cadenas iguales tienen siempre el mismo conjunto de palabras.
     */
    private fun sameWords(a: String, b: String): Boolean {
        val wordsA = wordsOf(a)
        if (wordsA.isEmpty()) return false
        return wordsA == wordsOf(b)
    }

    private fun wordsOf(value: String): Set<String> =
        value.split(" ").filter { it.isNotBlank() }.toSet()

    /**
     * Busca el artista del ancla en MusicBrainz: primero el nombre
     * entero, y si no aparece, palabra por palabra (S024).
     *
     * Es la misma mecánica que [identifyFromTitleWords] aplicada al
     * NOMBRE en vez de al título -- que es donde faltaba. En el log de
     * S023, la 9ª de Beethoven llegó a probar cinco prefijos del
     * título (`Symphony No. 9 in D`... hasta `Symphony`) y ni una sola
     * vez las palabras del nombre del artista, que era donde estaba la
     * respuesta desde el principio.
     *
     * La palabra solo sirve para ALCANZAR la ficha; quien decide si
     * vale es [pickAnchorArtist] contra el nombre completo. Buscando
     * `Beethoven` llega `Ludwig van Beethoven`, cuyo conjunto de
     * palabras es el del nombre original, y se acepta. Buscando `van`
     * llegarían cien artistas y ninguno pasaría el filtro.
     */
    private suspend fun findAnchorArtistMbid(sourceArtist: String): AnchorArtistPick? {
        // S025 -- MusicBrainz guarda a las personas al derecho ('Ludwig
        // van Beethoven'), y el catálogo de H05 al revés ('Beethoven,
        // Ludwig van'). Preguntar tal cual devolvía vacío siempre: en
        // el log de S025 aparece cuatro veces
        // "MusicBrainz no encontró NINGÚN artista con ese nombre".
        // `reorderCommaName()` deja intactos los nombres de grupo con
        // coma ('Earth, Wind & Fire'), así que aplicarlo aquí no tiene
        // contraindicación.
        // ---
        // S025 -- MusicBrainz stores people forename-first while H05's
        // catalogue stores them surname-first, so the query used to
        // come back empty every time for composers.
        val queryName = SearchNormalizer.reorderCommaName(sourceArtist)
        val direct = musicBrainzApiService
            .searchArtists(
                query = buildArtistQuery(queryName),
                limit = ANCHOR_SEARCH_LIMIT,
            )
            .artists
        pickAnchorArtist(sourceArtist, direct)?.let { return it }

        val words = wordsOf(SearchNormalizer.normalizeArtistName(sourceArtist))
            .filter { it.length > 2 }
            .take(MAX_TITLE_WORDS_FOR_ARTIST)
        if (words.size < 2) return null

        for (word in words) {
            val hits = musicBrainzApiService
                .searchArtists(
                    query = buildArtistQuery(word),
                    limit = ANCHOR_SEARCH_LIMIT,
                )
                .artists
            pickAnchorArtist(sourceArtist, hits, quiet = true)?.let { pick ->
                log("resolveAnchor('$sourceArtist') -- no aparecía con el nombre entero; resuelto buscando por la palabra '$word'")
                return pick
            }
        }
        log("resolveAnchor('$sourceArtist') -- tampoco aparece buscando palabra por palabra (${words.joinToString()}). No se fija ancla.")
        return null
    }

    private companion object {
        /**
         * Fallos transitorios seguidos a partir de los cuales se
         * considera que MusicBrainz no está disponible y la Radio pasa
         * a modo degradado.
         *
         * S022 -- subido de 2 a 4 tras verlo en dispositivo: dos 503
         * sueltos de `lookupArtistProfile()` bastaron para declarar
         * caído un servicio que en esa misma sesión estaba devolviendo
         * diez candidatos sin problema. Con `noteSuccess()` ya
         * presente en todos los caminos de éxito, llegar a cuatro
         * significa cuatro fallos SEGUIDOS de verdad.
         */
        const val DEGRADED_THRESHOLD = 4

        /**
         * S025 -- cuántos pendientes se reconcilian por vuelta de la
         * Radio. MusicBrainz admite una petición por segundo, así que
         * vaciar de golpe una cola larga dejaría la Radio esperando. A
         * este ritmo el cajón se drena solo mientras se escucha música,
         * que es exactamente lo que pidió Miguel Ángel: aprovechar que
         * ya hay red por otra búsqueda.
         *
         * Bajado de 3 a 1 tras el reporte de lentitud: eran hasta SEIS
         * peticiones extra por tema -- tres artistas y tres temas --, y
         * a una por segundo eso son seis segundos añadidos a cada
         * reposición de la cola. Con el botón de Ajustes construyendo
         * ya la base de datos en masa, el goteo de la Radio no tiene
         * que ir deprisa.
         */
        const val RECONCILE_PER_ROUND = 1

        /**
         * S025 -- con estos candidatos ya no hace falta seguir
         * preguntando por más géneros del ancla. Suficiente para que la
         * Radio no se repita y para no gastar una petición por género
         * en cada ronda.
         */
        const val ENOUGH_CANDIDATES = 40

        /**
         * Candidatos que se piden al buscar el artista del ancla.
         *
         * S023 -- subido del 5 por defecto. La búsqueda por
         * `artist:"NOMBRE"` devuelve coincidencias PARCIALES antes que
         * la exacta: pidiendo cinco, de 'Kanye West' salían una banda
         * tributo y una colaboración, y el artista real no aparecía en
         * la ventana. Con `pickAnchorArtist()` descartando por nombre,
         * pedir de más no cuesta precisión -- cuesta no encontrarlo.
         */
        const val ANCHOR_SEARCH_LIMIT = 25

        /**
         * Palabras iniciales del título que se prueban como nombre de
         * artista, de más a menos (ver `identifyFromTitleWords()`).
         *
         * Cinco cubre de sobra los nombres reales -- 'Creedence
         * Clearwater Revival' son tres, 'Emerson, Lake and Palmer'
         * cuatro -- y acota el coste a cinco peticiones como mucho,
         * solo al arrancar la sesión.
         */
        const val MAX_TITLE_WORDS_FOR_ARTIST = 5
    }
}
