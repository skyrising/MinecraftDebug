package de.skyrising.minecraft.deobf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import de.skyrising.util.StringView;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.zip.GZIPInputStream;

public final class YarnMappings {
    private static final Path CACHE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "fabric-yarn-cache");
    private static final Gson GSON = new Gson();
    private YarnMappings() {}

    public static String getLatestVersion(String gameVersion) throws IOException {
        Path versionFile = CACHE_DIR.resolve("version");
        if (Files.exists(versionFile) && Files.getLastModifiedTime(versionFile).toInstant().until(Instant.now(), ChronoUnit.HOURS) < 24) {
            System.out.println("Loading cached latest version from " + Files.getLastModifiedTime(versionFile));
            String version = new String(Files.readAllBytes(versionFile), StandardCharsets.UTF_8);
            String v = version.substring(version.lastIndexOf(':') + 1);
            if (v.startsWith(gameVersion)) return version;
        }
        System.out.println("Querying latest mappings for " + gameVersion);
        URL url = new URL("https://meta.fabricmc.net/v2/versions/yarn/" + gameVersion);
        JsonArray json = GSON.fromJson(new InputStreamReader(url.openStream()), JsonArray.class);
        return json.get(0).getAsJsonObject().get("maven").getAsString();
    }

    public static Mappings load(String yarnVersion) throws IOException {
        System.out.println("Loading " + yarnVersion);
        String[] splitVersion = yarnVersion.split(":");
        Path loomCached = Paths.get(System.getProperty("user.home"),
                ".gradle", "caches", "fabric-loom", "mappings",
                splitVersion[0] + "." + splitVersion[1] + "-tiny-" + splitVersion[2].replace("+build.", "-"));
        if (Files.exists(loomCached)) {
            System.out.println("Loading from fabric-loom cache");
            return TinyMappings.load(Files.newInputStream(loomCached));
        }
        Files.createDirectories(CACHE_DIR);
        Path versionFile = CACHE_DIR.resolve("version");
        Path mappingsFile = CACHE_DIR.resolve("mappings");
        if (!Files.exists(versionFile) || !new String(Files.readAllBytes(versionFile), StandardCharsets.UTF_8).equals(yarnVersion)) {
            String dir = splitVersion[0].replace('.', '/') + "/" + splitVersion[1] + "/" + splitVersion[2];
            String file = splitVersion[1] + "-" + splitVersion[2] + "-tiny.gz";
            URL url = new URL("https://maven.fabricmc.net/" + dir + "/" + file);
            Files.write(versionFile, yarnVersion.getBytes(StandardCharsets.UTF_8));
            Files.copy(new GZIPInputStream(url.openStream()), mappingsFile);
            System.out.println("Caching to " + CACHE_DIR);
        } else {
            System.out.println("Loading from cache");
        }
        return TinyMappings.load(Files.newInputStream(mappingsFile));
    }

    public static Mappings loadLatest(String gameVersion) throws IOException {
        return load(getLatestVersion(gameVersion));
    }
}
