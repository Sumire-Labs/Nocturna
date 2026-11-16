import me.flashyreese.mods.reeses_sodium_options.client.search.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** The same driver runs the unmodified release on Java 25 and the port on Java 17. */
public final class RsoSearchTrace {
    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
    }
    public static void main(String[] args) throws Exception {
        var normalization = MessageDigest.getInstance("SHA-256");
        var grams = MessageDigest.getInstance("SHA-256");
        var generator = new NgramGenerator(2,3,true);
        for (boolean fold : new boolean[]{false, true}) {
            var normalizer = new SearchNormalizer(fold);
            for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
                if (cp >= 0xD800 && cp <= 0xDFFF) continue;
                String value = new String(Character.toChars(cp));
                String normalized=normalizer.normalize(value);
                update(normalization, normalized);
                for(String gram:generator.generate(normalized))update(grams,gram);
                update(grams,"|");
            }
            for (String value : List.of("ΟΣ ΟΣΑ AΣ AΣ\u0301", "İ I ı i", "Å A\u030a Å", "É é e\u0301",
                    "ＡＢＣ＿１２３", "設定・画質　検索", "Σ\u1acfA", "\ud80c\udcf0Ａ", "가 가", "\uD81B\uDE40\uD81B\uDE60")) {
                update(normalization, normalizer.normalize(value));
            }
        }
        var ranking = MessageDigest.getInstance("SHA-256");
        var corpus = new ArrayList<String>(List.of("Render distance chunks", "Simulation Distance world updates",
                "VSync vertical synchronization", "Maximum Framerate unlimited FPS", "Weather rain snow",
                "Shader packs external screen", "Mipmap levels texture quality", "Animate visible textures",
                "Chunk update threads", "Renderer fog occlusion", "Rendering chunk culling", "Entity distance scaling",
                "Éclairage ambiant", "描画距離 チャンク", "画質 雨 雪", "Helligkeit", "ＡＢＣ＿１２３", "ΟΔΌΣ"));
        var recentUnicode=new ArrayList<String>();
        for(int cp:new int[]{0x1E4D0,0x11F02,0x31350,0x11380,0x16100,0x1C89}) {
            String text=new String(Character.toChars(cp));recentUnicode.add(text+text);corpus.add(text+text+" settings");
        }
        Random random = new Random(0x52534f);
        for (int i = 0; i < 500; i++) corpus.add(corpus.get(random.nextInt(18)) + " " + i);
        var queries = new ArrayList<String>(Arrays.asList(null, "", "   ", "distance", "render", "render distnce", "fps", "quality",
                "thread chunk", "chunk thread", "framerate", "éclairage", "eclairage", "描画", "雨", "abc 123", "ΟΔΟΣ", "!!!"));
        queries.addAll(recentUnicode);
        for (int i = 0; i < 500; i++) {
            String value = corpus.get(random.nextInt(corpus.size()));
            int start = random.nextInt(value.length());
            queries.add(value.substring(start, Math.min(value.length(), start + 2 + random.nextInt(15))));
        }
        int searches = 0;
        for (boolean fold : new boolean[]{false, true}) for (boolean rerank : new boolean[]{false, true}) {
            for (int max : new int[]{1, 15, 50}) {
                var index = SearchIndex.<String>builder(s -> s).addAll(corpus).foldDiacritics(fold)
                        .maxResults(max).minScore(0.3).rerankWithEditDistance(rerank).rerankLimit(50).rerankWeight(0.1).build();
                for (String query : queries) {
                    update(ranking, String.valueOf(query));
                    for (var result : index.newSession(query).results()) {
                        update(ranking, result.item()); update(ranking, Double.toHexString(result.score()));
                    }
                    searches++;
                }
            }
        }
        System.out.println("normalization=" + HexFormat.of().formatHex(normalization.digest()));
        System.out.println("grams=" + HexFormat.of().formatHex(grams.digest()));
        System.out.println("ranking=" + HexFormat.of().formatHex(ranking.digest()) + "; searches=" + searches);
    }
}
