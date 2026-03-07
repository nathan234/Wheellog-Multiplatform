package org.freewheel.core.domain.dashboard

/**
 * Hand-rolled text serializer for [NavigationConfig].
 *
 * v1 format: `v1|tabs:DEVICES,RIDES,SETTINGS`
 * v2 format: `v2|tabs:DEVICES,custom_racing,SETTINGS|custom:custom_racing;Racing;speed`
 *
 * v2 adds custom tab definitions. Built-in tabs are referenced by ID in the tabs list.
 * Custom tabs are defined in `custom:id;label;iconName` sections and referenced by ID.
 *
 * Unknown tab names are skipped gracefully (forward-compatible).
 * Invalid or empty input returns null (caller falls back to default).
 */
object NavigationConfigSerializer {

    private const val CURRENT_VERSION = "v2"

    fun serialize(config: NavigationConfig): String {
        val sb = StringBuilder(CURRENT_VERSION)
        sb.append("|tabs:")
        sb.append(config.tabs.joinToString(",") { it.id })
        for (tab in config.customTabs) {
            sb.append("|custom:")
            sb.append(tab.id).append(';')
            sb.append(sanitizeLabel(tab.label)).append(';')
            sb.append(tab.iconName)
        }
        return sb.toString()
    }

    fun deserialize(input: String): NavigationConfig? {
        if (input.isBlank()) return null
        val parts = input.split("|")
        if (parts.isEmpty()) return null
        return when (parts[0]) {
            "v1" -> parseTabsOnly(parts)
            "v2" -> parseV2(parts)
            else -> null
        }
    }

    /**
     * v2: first collect all `custom:` definitions into a lookup map,
     * then resolve the `tabs:` list against both built-ins and custom defs.
     */
    private fun parseV2(parts: List<String>): NavigationConfig? {
        val sections = parseSections(parts.drop(1))
        val customDefs = buildCustomDefs(sections["custom"] ?: emptyList())
        val tabsCsv = sections["tabs"]?.firstOrNull() ?: return null
        val tabs = resolveTabIds(tabsCsv, customDefs)
        return validateAndReturn(tabs)
    }

    /** v1: built-in tabs only, no custom definitions. */
    private fun parseTabsOnly(parts: List<String>): NavigationConfig? {
        val sections = parseSections(parts.drop(1))
        val tabsCsv = sections["tabs"]?.firstOrNull() ?: return null
        val tabs = resolveTabIds(tabsCsv, emptyMap())
        return validateAndReturn(tabs)
    }

    /**
     * Parse `key:value` sections from the pipe-delimited parts.
     * Returns a map of key → list of values (multiple `custom:` sections are collected).
     */
    private fun parseSections(parts: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        for (part in parts) {
            val colonIdx = part.indexOf(':')
            if (colonIdx < 0) continue
            val key = part.substring(0, colonIdx)
            val value = part.substring(colonIdx + 1)
            result.getOrPut(key) { mutableListOf() }.add(value)
        }
        return result
    }

    /**
     * Build a lookup map of custom tab ID → [NavigationTab.Custom]
     * from semicolon-delimited definition strings: `id;label;iconName`
     */
    private fun buildCustomDefs(defs: List<String>): Map<String, NavigationTab.Custom> {
        val map = mutableMapOf<String, NavigationTab.Custom>()
        for (def in defs) {
            val fields = def.split(";", limit = 3)
            if (fields.size < 3) continue
            val (id, label, iconName) = fields
            if (id.isNotBlank() && label.isNotBlank() && iconName.isNotBlank()) {
                map[id] = NavigationTab.Custom(id = id, label = label, iconName = iconName)
            }
        }
        return map
    }

    /**
     * Resolve a comma-separated list of tab IDs against built-in tabs and custom definitions.
     * Unknown IDs are silently skipped.
     */
    private fun resolveTabIds(csv: String, customDefs: Map<String, NavigationTab.Custom>): List<NavigationTab> {
        if (csv.isBlank()) return emptyList()
        return csv.split(",").mapNotNull { id ->
            val trimmed = id.trim()
            NavigationTab.builtInById(trimmed) ?: customDefs[trimmed]
        }
    }

    /** Validate resolved tabs and return config, or null if invalid. */
    private fun validateAndReturn(tabs: List<NavigationTab>): NavigationConfig? {
        if (tabs.isEmpty()) return null
        val config = NavigationConfig(tabs = tabs)
        return if (config.isValid()) config else null
    }

    /** Strip characters that would break the serialization format. */
    private fun sanitizeLabel(label: String): String {
        return label.replace("|", "").replace(",", "").replace(";", "")
    }
}
