package com.miguelaetxio.mimoo.data.contacts

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de intentar poner una pista como tono de un contacto. */
sealed class SetContactRingtoneResult {
    object Success : SetContactRingtoneResult()
    object UnsupportedAndroidVersion : SetContactRingtoneResult()
    object ContactNotFound : SetContactRingtoneResult()
    data class Failed(val message: String) : SetContactRingtoneResult()
}

/**
 * "Elegir como tono para un contacto" -- petición explícita de
 * Miguel Ángel (2026-08-02) en el menú de tres puntos del
 * reproductor. Dos pasos separados, cada uno con su propia API de
 * Android:
 *
 * 1. **Instalar la pista como tono real del sistema**
 *    (`installAsRingtone`): copia los bytes del archivo local (URI
 *    SAF, ver SearchResultTrack.filePath) a una entrada nueva de
 *    MediaStore marcada `IS_RINGTONE=1` -- es el mismo mecanismo que
 *    usa cualquier gestor de archivos con "Usar como tono". Solo
 *    Android 10+ (API 29, `Build.VERSION_CODES.Q`): la vía anterior a
 *    scoped storage exige escribir una ruta de archivo real fuera del
 *    sandbox de la app y no se ha implementado -- decisión explícita
 *    de alcance para no dejar a medias una ruta legacy difícil de
 *    verificar sin un dispositivo con Android 9 o anterior a mano.
 * 2. **Asignar ese tono a UN contacto concreto**
 *    (`assignToContact`): actualiza la columna
 *    `ContactsContract.Contacts.CUSTOM_RINGTONE` del contacto elegido
 *    -- requiere permiso WRITE_CONTACTS (runtime, se solicita desde
 *    PlayerBar antes de llamar aquí).
 * ---
 * "Set as ringtone for a contact" -- explicit request from Miguel
 * Ángel (2026-08-02) in the player's three-dot menu. Two separate
 * steps, each with its own Android API:
 *
 * 1. **Install the track as a real system ringtone**
 *    (`installAsRingtone`): copies the local file's bytes (SAF URI,
 *    see SearchResultTrack.filePath) into a new MediaStore entry
 *    flagged `IS_RINGTONE=1` -- the same mechanism any file manager's
 *    "Use as ringtone" uses. Android 10+ only (API 29,
 *    `Build.VERSION_CODES.Q`): the pre-scoped-storage path requires
 *    writing a real file path outside the app's sandbox and hasn't
 *    been implemented -- explicit scope decision to avoid leaving a
 *    legacy path half-done that's hard to verify without an Android 9
 *    or older device on hand.
 * 2. **Assign that ringtone to ONE specific contact**
 *    (`assignToContact`): updates the chosen contact's
 *    `ContactsContract.Contacts.CUSTOM_RINGTONE` column -- requires
 *    WRITE_CONTACTS permission (runtime, requested from PlayerBar
 *    before calling here).
 */
@Singleton
class ContactRingtoneRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun setAsRingtoneForContact(
        sourceUri: Uri,
        displayName: String,
        contactPickedUri: Uri,
    ): SetContactRingtoneResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext SetContactRingtoneResult.UnsupportedAndroidVersion
        }
        val ringtoneUri = try {
            installAsRingtone(sourceUri, displayName)
        } catch (e: Exception) {
            return@withContext SetContactRingtoneResult.Failed(
                e.message ?: "No se pudo instalar la pista como tono."
            )
        } ?: return@withContext SetContactRingtoneResult.Failed("No se pudo instalar la pista como tono.")

        val contactId = resolveContactId(contactPickedUri)
            ?: return@withContext SetContactRingtoneResult.ContactNotFound

        return@withContext try {
            assignToContact(contactId, ringtoneUri)
            SetContactRingtoneResult.Success
        } catch (e: Exception) {
            SetContactRingtoneResult.Failed(e.message ?: "No se pudo asignar el tono al contacto.")
        }
    }

    /**
     * Inserta una entrada nueva en MediaStore marcada como tono
     * (`IS_RINGTONE=1`) y copia dentro los bytes del archivo local --
     * el propio archivo original (SAF, dentro de la carpeta de MiMoo)
     * NO se toca ni se mueve, esto es una COPIA nueva que gestiona
     * MediaStore, visible para el resto de apps del sistema.
     * ---
     * Inserts a new MediaStore entry flagged as a ringtone
     * (`IS_RINGTONE=1`) and copies the local file's bytes into it --
     * the original file itself (SAF, inside MiMoo's folder) is NOT
     * touched or moved, this is a new COPY managed by MediaStore,
     * visible to the rest of the system's apps.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun installAsRingtone(sourceUri: Uri, displayName: String): Uri? {
        val resolver = context.contentResolver
        val safeName = displayName.ifBlank { "MiMoo" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.opus")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/opus")
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
            put(MediaStore.Audio.Media.IS_ALARM, 0)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values) ?: return null

        val copiedOk = resolver.openOutputStream(itemUri)?.use { out ->
            resolver.openInputStream(sourceUri)?.use { input ->
                input.copyTo(out)
                true
            } ?: false
        } ?: false

        if (!copiedOk) {
            resolver.delete(itemUri, null, null)
            return null
        }

        val pendingDone = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        resolver.update(itemUri, pendingDone, null, null)
        return itemUri
    }

    /**
     * El Uri que devuelve el selector de contacto de Android
     * (`ActivityResultContracts.PickContact`) no es directamente el
     * `_ID` que exige `CUSTOM_RINGTONE` -- hay que resolverlo con una
     * consulta, igual que cualquier integración con la agenda.
     * ---
     * The Uri returned by Android's contact picker
     * (`ActivityResultContracts.PickContact`) isn't directly the
     * `_ID` that `CUSTOM_RINGTONE` needs -- it has to be resolved with
     * a query, same as any address-book integration.
     */
    private fun resolveContactId(contactPickedUri: Uri): Long? {
        context.contentResolver.query(
            contactPickedUri,
            arrayOf(ContactsContract.Contacts._ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            }
        }
        return null
    }

    private fun assignToContact(contactId: Long, ringtoneUri: Uri) {
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
        }
        context.contentResolver.update(
            ContactsContract.Contacts.CONTENT_URI,
            values,
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
        )
    }
}
