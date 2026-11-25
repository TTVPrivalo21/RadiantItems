package dev.privalo.items.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.privalo.items.util.Text;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the resource pack from textures/items/*.png (or procedural icons):
 * item models (handheld/generated), 1.21.4+ item definitions for item_model and optional
 * custom_model_data range_dispatch on the base material. Zipped deterministically.
 */
public final class PackBuilder {

    public static final String NS = "radiantitems";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2024, 1, 1, 0, 0);
    /** Materials whose vanilla item definition is not a plain model (would lose animations/tints). */
    private static final Set<String> NO_DISPATCH = Set.of(
            "BOW", "CROSSBOW", "SHIELD", "TRIDENT", "COMPASS", "RECOVERY_COMPASS", "CLOCK", "FISHING_ROD",
            "PLAYER_HEAD", "POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW", "LEATHER_HELMET",
            "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS", "LEATHER_HORSE_ARMOR", "BUNDLE",
            "LIGHT", "FILLED_MAP", "FIREWORK_STAR", "CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "SPAWNER",
            "WOLF_ARMOR", "GOAT_HORN", "BRUSH", "ELYTRA");

    /** What the builder needs from each item (copied on the main thread). */
    public record Entry(String id, String texture, String parent, String material, Integer customModelData) {
    }

    public record Input(List<Entry> entries, int packFormat, int minFormat, int maxFormat, String description,
                        boolean dispatch) {
    }

    private final File dataFolder;
    private final Logger logger;

    public PackBuilder(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public File texturesDir() {
        return new File(dataFolder, "textures");
    }

    public File itemTexturesDir() {
        return new File(texturesDir(), "items");
    }

    public File packDir() {
        return new File(texturesDir(), "pack");
    }

    public BuiltPack build(Input input) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        Map<String, String> report = new TreeMap<>();
        files.put("pack.mcmeta", json(mcmeta(input)));
        File customIcon = new File(packDir(), "pack.png");
        files.put("pack.png", customIcon.isFile() ? Files.readAllBytes(customIcon.toPath()) : IconPainter.packIcon().png());
        exportDefault("pack.default.png", IconPainter.packIcon().png(), packDir());

        Map<String, List<Entry>> dispatch = new TreeMap<>();
        for (Entry e : input.entries()) {
            if (e.texture() == null) {
                continue;
            }
            String texturePath = "assets/" + NS + "/textures/item/" + e.texture() + ".png";
            if (!files.containsKey(texturePath)) {
                File png = new File(itemTexturesDir(), e.texture() + ".png");
                byte[] bytes;
                String source;
                if (png.isFile()) {
                    bytes = Files.readAllBytes(png.toPath());
                    source = "textures/items/" + e.texture() + ".png";
                    File meta = new File(itemTexturesDir(), e.texture() + ".png.mcmeta");
                    if (meta.isFile()) {
                        files.put(texturePath + ".mcmeta", Files.readAllBytes(meta.toPath()));
                        source += " (+mcmeta)";
                    }
                } else {
                    PixelCanvas procedural = IconPainter.paint(e.texture());
                    if (procedural == null) {
                        logger.warning("[" + e.id() + "] falta textures/items/" + e.texture() + ".png y no hay icono procedural: se omite del pack.");
                        report.put(e.id(), "FALTA " + e.texture() + ".png");
                        continue;
                    }
                    bytes = procedural.png();
                    source = BuiltPack.PROCEDURAL;
                    exportDefault(e.texture() + ".png", bytes, new File(itemTexturesDir(), "_defaults"));
                }
                files.put(texturePath, bytes);
                report.put(e.id(), source);
            } else {
                report.put(e.id(), "compartida: " + e.texture());
            }
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", NS + ":item/" + e.texture());
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/" + e.parent());
            model.add("textures", textures);
            files.put("assets/" + NS + "/models/item/" + e.id() + ".json", json(model));
            files.put("assets/" + NS + "/items/" + e.id() + ".json", json(itemDefinition(modelRef(NS + ":item/" + e.id()))));
            if (input.dispatch() && e.customModelData() != null) {
                if (NO_DISPATCH.contains(e.material())) {
                    logger.info("[" + e.id() + "] " + e.material() + " tiene un modelo vanilla especial: se usa solo item_model (sin custom-model-data dispatch).");
                } else {
                    dispatch.computeIfAbsent(e.material(), k -> new ArrayList<>()).add(e);
                }
            }
        }
        for (Map.Entry<String, List<Entry>> d : dispatch.entrySet()) {
            String material = d.getKey().toLowerCase(Locale.ROOT);
            List<Entry> list = d.getValue();
            list.sort((a, b) -> Integer.compare(a.customModelData(), b.customModelData()));
            JsonArray entries = new JsonArray();
            for (Entry e : list) {
                JsonObject entry = new JsonObject();
                entry.addProperty("threshold", e.customModelData());
                entry.add("model", modelRef(NS + ":item/" + e.id()));
                entries.add(entry);
            }
            JsonObject range = new JsonObject();
            range.addProperty("type", "minecraft:range_dispatch");
            range.addProperty("property", "minecraft:custom_model_data");
            range.addProperty("index", 0);
            range.add("entries", entries);
            range.add("fallback", modelRef("minecraft:item/" + material));
            JsonObject root = new JsonObject();
            root.add("model", range);
            files.put("assets/minecraft/items/" + material + ".json", json(root));
        }
        int extra = copyExtra(new File(packDir(), "extra"), files);
        if (extra > 0) {
            logger.info(extra + " archivo(s) de textures/pack/extra/ añadidos al pack.");
        }
        byte[] zip = zip(files);
        String sha1 = HexFormat.of().formatHex(sha1(zip));
        File out = new File(packDir(), "RadiantItems-pack.zip");
        if (!packDir().isDirectory() && !packDir().mkdirs()) {
            throw new IOException("No se pudo crear " + packDir());
        }
        Files.write(out.toPath(), zip);
        return new BuiltPack(zip, sha1, out, files.size(), report);
    }

    private static JsonObject modelRef(String model) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", model);
        return m;
    }

    private static JsonObject itemDefinition(JsonObject model) {
        JsonObject root = new JsonObject();
        root.add("model", model);
        return root;
    }

    private static JsonObject mcmeta(Input input) {
        int min = input.minFormat();
        int max = Math.max(min, input.maxFormat());
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", min);
        supported.addProperty("max_inclusive", max);
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", Math.max(min, Math.min(max, input.packFormat())));
        pack.add("supported_formats", supported);
        pack.addProperty("min_format", min);
        pack.addProperty("max_format", max);
        JsonElement description = GsonComponentSerializer.gson().serializeToTree(Text.parse(input.description()));
        pack.add("description", description);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private void exportDefault(String name, byte[] bytes, File dir) {
        File target = new File(dir, name);
        if (target.isFile()) {
            return;
        }
        try {
            Files.createDirectories(dir.toPath());
            Files.write(target.toPath(), bytes);
        } catch (IOException ex) {
            logger.fine("No se pudo exportar " + target + ": " + ex.getMessage());
        }
    }

    private static int copyExtra(File root, Map<String, byte[]> files) throws IOException {
        if (!root.isDirectory()) {
            return 0;
        }
        Path base = root.toPath();
        int count = 0;
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
                files.put(base.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
                count++;
            }
        }
        return count;
    }

    private static byte[] json(JsonElement element) {
        return GSON.toJson(element).getBytes(StandardCharsets.UTF_8);
    }

    /** Deterministic zip (sorted entries, fixed timestamps) → same content = same SHA-1. */
    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 15);
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setLevel(9);
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTimeLocal(FIXED_TIME);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 no disponible", ex);
        }
    }

    /** Creates textures/items/, textures/pack/ and the LEEME.txt guide. */
    public void ensureStructure() {
        try {
            Files.createDirectories(itemTexturesDir().toPath());
            Files.createDirectories(packDir().toPath());
            Files.createDirectories(new File(packDir(), "extra").toPath());
            File readme = new File(texturesDir(), "LEEME.txt");
            if (!readme.isFile()) {
                Files.writeString(readme.toPath(), README, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            logger.warning("No se pudo crear la carpeta textures/: " + ex.getMessage());
        }
    }

    /** Changes when any texture file is added/removed/modified (auto-reload). */
    public long stamp() {
        long value = 17;
        File root = texturesDir();
        if (!root.isDirectory()) {
            return value;
        }
        try (Stream<Path> walk = Files.walk(root.toPath())) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
                String name = p.getFileName().toString();
                if (name.endsWith(".zip") || p.toString().contains("_defaults") || name.equals("pack.default.png")) {
                    continue;
                }
                value = value * 31 + p.toString().hashCode();
                value = value * 31 + Files.getLastModifiedTime(p).toMillis();
                value = value * 31 + Files.size(p);
            }
        } catch (IOException ex) {
            return value;
        }
        return value;
    }

    private static final String README = """
            RadiantItems · texturas del resource pack
            =========================================

            Esta carpeta genera automáticamente el resource pack que se envía a los jugadores
            (servidor HTTP integrado, puerto http.port de config.yml, por defecto 8166).

            ESTRUCTURA
              textures/items/<nombre>.png          icono del ítem (16x16, 32x32, 64x64...)
              textures/items/<nombre>.png.mcmeta   (opcional) animación
              textures/items/_defaults/            iconos procedurales de los ejemplos (plantillas)
              textures/pack/pack.png               icono del pack (si no existe se usa uno generado)
              textures/pack/extra/                 archivos copiados tal cual a la raíz del zip (avanzado)
              textures/pack/RadiantItems-pack.zip  último pack generado (solo informativo)

            CÓMO AÑADIR UN ICONO
              1. Copia mi_espada.png en textures/items/
              2. En el YAML del ítem (items/<id>.yml) pon:
                     texture: mi_espada
                     model: handheld        # handheld = herramienta en diagonal, generated = ítem plano
                 (si omites model: se elige solo: espadas/picos/hachas/palos → handheld)
              3. /ri reload  (o espera unos segundos: la recarga automática detecta el cambio)
              4. El plugin genera:
                     assets/radiantitems/textures/item/mi_espada.png
                     assets/radiantitems/models/item/<id>.json
                     assets/radiantitems/items/<id>.json      (1.21.4+, se asigna con item_model)
                 y si el ítem tiene custom-model-data: N, también
                     assets/minecraft/items/<material>.json  (range_dispatch por custom_model_data)

            ANIMACIONES
              Un PNG vertical con varios frames (ej. 16x64 = 4 frames) + mi_espada.png.mcmeta:
                  { "animation": { "frametime": 2, "interpolate": true } }

            NOTAS
              - Los nombres deben ir en minúsculas (a-z 0-9 _ - . /).
              - El SHA-1 del pack es determinista: si nada cambia, los clientes usan la caché.
              - Si los jugadores no descargan el pack, configura http.public-url con tu IP/dominio
                pública y abre el puerto en el firewall/panel del hosting.
              - /ri pack            reenvía el pack
                /ri pack rebuild    lo regenera a mano
            """;
}
