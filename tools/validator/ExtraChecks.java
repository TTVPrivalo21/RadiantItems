import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemLoader;
import dev.privalo.items.pack.BuiltPack;
import dev.privalo.items.pack.PackBuilder;
import dev.privalo.items.util.Expr;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;

public class ExtraChecks {
    public static void main(String[] args) throws Exception {
        int fails = 0, roundTrips = 0;
        File res = new File(args[0]);
        List<PackBuilder.Entry> entries = new ArrayList<>();
        List<File> all = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(new File(res, "items").toPath())) {
            walk.filter(x -> x.toString().endsWith(".yml")).sorted().forEach(x -> all.add(x.toFile()));
        }
        for (File f : all) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(f);
            List<String> errs = new ArrayList<>();
            CustomItem a = ItemLoader.read(y.getString("id"), y, errs);
            YamlConfiguration out = new YamlConfiguration();
            ItemLoader.write(a, out);
            YamlConfiguration back = new YamlConfiguration();
            back.loadFromString(out.saveToString());
            CustomItem b = ItemLoader.read(a.id, back, errs);
            boolean same = a.revision == b.revision && errs.isEmpty();
            if (!same) System.out.println("ERR round-trip " + a.id + " rev " + Integer.toHexString(a.revision) + " / " + Integer.toHexString(b.revision) + " " + errs);
            if (!same) fails++; else roundTrips++;
            entries.add(new PackBuilder.Entry(a.id, a.texture, a.model == null ? "generated" : a.model, a.material, a.customModelData));
        }
        System.out.println("OK  round-trip estable en " + roundTrips + " ítems");
        String[][] exprs = {{"5 + 3 * 2", "11"}, {"(4.5 + 0.5) * 0.25", "1.25"}, {"max(2, 7, 3)", "7"}, {"-3 + 10 / 4", "-0.5"}};
        for (String[] e : exprs) {
            String got = Expr.format(Expr.eval(e[0]));
            boolean ok = got.equals(e[1]);
            System.out.println((ok ? "OK  " : "ERR ") + "expr " + e[0] + " = " + got);
            if (!ok) fails++;
        }
        String[][] cmps = {{"12 >= 10", "true"}, {"3 == 3.0", "true"}, {"abc = ABC", "true"}, {"5 < 2", "false"}, {"25 >= 25", "true"}};
        for (String[] c : cmps) {
            boolean got = Expr.compare(c[0]);
            boolean ok = String.valueOf(got).equals(c[1]);
            System.out.println((ok ? "OK  " : "ERR ") + "compare " + c[0] + " -> " + got);
            if (!ok) fails++;
        }
        File data = new File(args[1]);
        PackBuilder builder = new PackBuilder(data, Logger.getLogger("test"));
        builder.ensureStructure();
        PackBuilder.Input input = new PackBuilder.Input(entries, 46, 46, 999, "<gold>RadiantItems", true);
        BuiltPack p1 = builder.build(input);
        BuiltPack p2 = builder.build(input);
        boolean det = p1.sha1Hex().equals(p2.sha1Hex());
        System.out.println((det ? "OK  " : "ERR ") + "pack determinista sha1=" + p1.sha1Hex() + " entries=" + p1.entries() + " " + p1.zip().length / 1024 + "KB");
        if (!det) fails++;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(p1.zip()))) {
            int n = 0; for (var e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) n++; System.out.println("    entradas zip: " + n);
        }
        System.out.println("    texturas: " + p1.textures());
        System.out.println(fails == 0 ? "EXTRA OK" : "EXTRA CON " + fails + " FALLOS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
