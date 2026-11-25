package dev.privalo.items.pack;

import java.io.File;
import java.util.Map;

/**
 * A generated, zipped resource pack.
 *
 * @param zip      zip bytes (served from memory by the HTTP server)
 * @param sha1Hex  lowercase hex SHA-1 (deterministic: same content → same hash)
 * @param file     copy written to plugins/RadiantItems/textures/pack/RadiantItems-pack.zip
 * @param entries  number of files inside the pack
 * @param textures item id → texture source ("textures/items/x.png" or "procedural")
 */
public record BuiltPack(byte[] zip, String sha1Hex, File file, int entries, Map<String, String> textures) {

    public static final String PROCEDURAL = "procedural";

    /** Download file name; changes with the content (cache busting). */
    public String fileName() {
        return "radiantitems-" + sha1Hex.substring(0, 12) + ".zip";
    }
}
