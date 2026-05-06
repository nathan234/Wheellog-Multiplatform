package org.freewheel.core.ble

/**
 * Matches a wheel's [DiscoveredServices] against a list of typed
 * [WheelTopology] fingerprints.
 *
 * A topology matches when, after canonicalizing every UUID in the
 * discovered services to lowercase 128-bit form:
 *
 *  - the discovered service count equals the fingerprint service count;
 *  - every discovered service has a unique canonicalized UUID (the legacy
 *    JSON expressed services as object keys, so fingerprints can never
 *    contain duplicate service UUIDs — a discovered list that does cannot
 *    match any fingerprint, even when the total count happens to align);
 *  - for each fingerprint service, the discovered service with the same
 *    UUID exposes the same characteristic count *and* the same canonical
 *    characteristic set as the fingerprint.
 *
 * Both axes — services and per-service characteristics — preserve
 * multiplicity, mirroring legacy WheelLog `WheelData.detectWheel`
 * (`services.length() - 1 != wheelServices.size()` and
 * `service_uuid.length() != service.getCharacteristics().size()`). Order,
 * however, is intentionally insensitive: the discovered services may
 * arrive in any order from the platform, and characteristic UUIDs may
 * appear in any order within a service.
 *
 * Pass 2 of the S22 fingerprinting plan ports the legacy matching logic
 * into commonMain. The matcher is standalone in this pass — Pass 3a
 * wires it into `WheelTypeDetector.detect`.
 *
 * The match function returns `List<Match>` (not `Match?`) so callers can
 * see when more than one fingerprint claims the same topology — possible
 * because the legacy JSON contains duplicate entries (e.g. the two
 * byte-identical `gotway` proxy fingerprints) and because two adapters
 * could in principle expose identical service trees.
 */
class WheelTopologyMatcher(
    private val topologies: List<WheelTopology> = WheelTopologies.ALL,
) {

    /** A successful fingerprint hit. */
    data class Match(val topology: WheelTopology)

    /**
     * Return every topology in [topologies] that matches [services] under
     * the rules described in the class doc. Empty list means no
     * fingerprint matched.
     */
    fun match(services: DiscoveredServices): List<Match> =
        topologies.filter { matchesTopology(services, it) }.map { Match(it) }

    private fun matchesTopology(
        discovered: DiscoveredServices,
        topology: WheelTopology,
    ): Boolean {
        if (discovered.services.size != topology.services.size) return false

        val byCanonicalUuid = HashMap<String, DiscoveredService>(discovered.services.size)
        for (svc in discovered.services) {
            val uuid = BleUuids.canonicalize(svc.uuid)
            if (byCanonicalUuid.put(uuid, svc) != null) return false
        }

        for (fpSvc in topology.services) {
            val discoveredSvc = byCanonicalUuid[fpSvc.uuid] ?: return false
            val canonicalChars = discoveredSvc.characteristics.map { BleUuids.canonicalize(it) }
            if (canonicalChars.size != fpSvc.characteristics.size) return false
            if (canonicalChars.toSet() != fpSvc.characteristics) return false
        }
        return true
    }
}
