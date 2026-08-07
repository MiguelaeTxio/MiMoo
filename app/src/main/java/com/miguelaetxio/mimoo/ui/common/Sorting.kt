package com.miguelaetxio.mimoo.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Criterio y dirección de orden compartidos por todas las listas de
 * items de la app (H18, S032, petición explícita de Miguel Ángel:
 * "ordenar las listas por orden alfabético o por orden de adición,
 * ascendentes y descendentes"; alcance confirmado en la propia sesión
 * de apertura -- Favoritos, Listas de reproducción (H04), Canales
 * (H11) y Lista Negra (H16); Explorador (H12) queda fuera, ver
 * ANNEX_H18.md bloque 5).
 *
 * Extraído a este archivo compartido tras construir el control por
 * primera vez en `FavoritesScreen.kt` (bloque 3), para no duplicar la
 * misma UI y la misma lógica de ordenación en cada pantalla que lo
 * necesita después.
 * ---
 * Sort criterion and direction shared by every "list of items" screen
 * in the app (H18, S032, explicit request from Miguel Ángel: "sort
 * the lists alphabetically or by order added, ascending and
 * descending"; scope confirmed in the hito's own opening session --
 * Favorites, Playlists (H04), Channels (H11) and Blacklist (H16);
 * Explorer (H12) is out of scope, see ANNEX_H18.md block 5).
 *
 * Extracted to this shared file after building the control for the
 * first time in `FavoritesScreen.kt` (block 3), to avoid duplicating
 * the same UI and the same sorting logic in every screen that needs
 * it afterwards.
 */
enum class SortCriterion { ALPHABETICAL, DATE_ADDED }

/** Un único control la alterna, sin tocar el criterio activo -- punto 5 de diseño de H18, cerrado con Miguel Ángel. */
enum class SortDirection { ASCENDING, DESCENDING }

/**
 * Control de ordenación: dos `FilterChip` de criterio (alfabético/
 * adición) + un `IconButton` que solo invierte ascendente/descendente
 * del criterio ya elegido -- nunca un ciclo que rote por las cuatro
 * combinaciones con un solo toque (diseño cerrado con Miguel Ángel en
 * la apertura de H18).
 */
@Composable
fun SortControl(
    criterion: SortCriterion,
    direction: SortDirection,
    onCriterionChange: (SortCriterion) -> Unit,
    onToggleDirection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = criterion == SortCriterion.ALPHABETICAL,
            onClick = { onCriterionChange(SortCriterion.ALPHABETICAL) },
            label = { Text("Alfabético") },
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = criterion == SortCriterion.DATE_ADDED,
            onClick = { onCriterionChange(SortCriterion.DATE_ADDED) },
            label = { Text("Adición") },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleDirection) {
            Icon(
                if (direction == SortDirection.ASCENDING) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (direction == SortDirection.ASCENDING) {
                    "Orden ascendente, tocar para invertir"
                } else {
                    "Orden descendente, tocar para invertir"
                },
            )
        }
    }
}

/** Aplica criterio+dirección a cualquier lista (H18, S032) -- nameOf/addedAtOf desacoplan esta función del tipo de fila concreto. */
fun <T> sortedByCriterion(
    items: List<T>,
    criterion: SortCriterion,
    direction: SortDirection,
    nameOf: (T) -> String,
    addedAtOf: (T) -> Long,
): List<T> {
    val comparator = when (criterion) {
        SortCriterion.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER, nameOf)
        SortCriterion.DATE_ADDED -> compareBy(addedAtOf)
    }
    val sorted = items.sortedWith(comparator)
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}
