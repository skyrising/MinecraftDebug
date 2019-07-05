package de.skyrising.minecraft.deobf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

public final class YarnMappings {
    private static final Logger LOGGER = LogManager.getLogManager().getLogger("yarn-mappings");
    private static final Gson GSON = new Gson();
    private YarnMappings() {}

    public static String getLatestVersion(String gameVersion) throws IOException {
        System.out.println("Querying latest mappings for " + gameVersion);
        URL url = new URL("https://meta.fabricmc.net/v2/versions/yarn/" + gameVersion);
        JsonArray json = GSON.fromJson(new InputStreamReader(url.openStream()), JsonArray.class);
        return json.get(0).getAsJsonObject().get("maven").getAsString();
    }

    public static Mappings load(String yarnVersion) throws IOException {
        System.out.println("Loading " + yarnVersion);
        String[] splitVersion = yarnVersion.split(":");
        String dir = splitVersion[0].replace('.', '/') + "/" + splitVersion[1] + "/" + splitVersion[2];
        String file = splitVersion[1] + "-" + splitVersion[2] + "-tiny.gz";
        URL url = new URL("https://maven.fabricmc.net/" + dir + "/" + file);
        return TinyMappings.load(new GZIPInputStream(url.openStream()));
    }

    public static Mappings loadLatest(String gameVersion) throws IOException {
        return load(getLatestVersion(gameVersion));
    }
}
