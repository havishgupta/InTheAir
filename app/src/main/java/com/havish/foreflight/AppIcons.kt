package com.havish.foreflight

/**
 * Central registry for all icons used in the app.
 * View, edit, and manage all icon mappings from this single file.
 *
 * Icon naming convention:
 *   - Navigation/action icons: ic_<action> (e.g. ic_delete, ic_rename)
 *   - Note marker icons: ic_<object> (e.g. ic_tent, ic_food, ic_gas_station)
 *   - Utility icons: ic_<purpose> (e.g. ic_menu, ic_plane)
 *
 * Note marker icons (28dp) use white fill + black outline for map visibility.
 * Action icons (24dp) use tint-based coloring via colorControlNormal.
 */
object AppIcons {

    // ── Note Marker Icons (used on map + in Add Note dialog) ──────────
    val NOTE_ICONS: Map<String, Int> = linkedMapOf(
        "ic_note_marker"   to R.drawable.ic_note_marker,
        "ic_speedbreaker"  to R.drawable.ic_speedbreaker,
        "ic_tent"          to R.drawable.ic_tent,
        "ic_danger"        to R.drawable.ic_danger,
        "ic_food"          to R.drawable.ic_food,
        "ic_gas_station"   to R.drawable.ic_gas_station,
        "ic_parking"       to R.drawable.ic_parking,
        "ic_water"         to R.drawable.ic_water,
        "ic_hospital"      to R.drawable.ic_hospital,
        "ic_hotel"         to R.drawable.ic_hotel,
        "ic_star"          to R.drawable.ic_star,
        "ic_flag"          to R.drawable.ic_flag,
        "ic_camera"        to R.drawable.ic_camera,
        "ic_viewpoint"     to R.drawable.ic_viewpoint,
        "ic_info"          to R.drawable.ic_info
    )

    // ── Navigation / Action Icons ────────────────────────────────────
    val ACTION_ICONS: Map<String, Int> = linkedMapOf(
        "ic_voyage"        to R.drawable.ic_voyage,
        "ic_view_voyage"   to R.drawable.ic_view_voyage,
        "ic_rename"        to R.drawable.ic_rename,
        "ic_delete"        to R.drawable.ic_delete,
        "ic_cloud"         to R.drawable.ic_cloud,
        "ic_gear"          to R.drawable.ic_gear,
        "ic_menu"          to R.drawable.ic_menu,
        "ic_plane"         to R.drawable.ic_plane,
        "ic_add_note"      to R.drawable.ic_add_note,
        "ic_note_marker"   to R.drawable.ic_note_marker
    )

    // ── Combined lookup (all icons) ──────────────────────────────────
    val ALL_ICONS: Map<String, Int> = NOTE_ICONS + ACTION_ICONS

    /**
     * Get drawable resource ID by icon name string.
     * Falls back to ic_note_marker if not found.
     */
    fun getIconRes(name: String): Int {
        return ALL_ICONS[name] ?: R.drawable.ic_note_marker
    }
}
