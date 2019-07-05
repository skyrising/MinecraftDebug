package de.skyrising.minecraft.debug;

import de.skyrising.minecraft.deobf.Deobfuscator;
import de.skyrising.minecraft.deobf.Mappings;
import de.skyrising.minecraft.deobf.YarnMappings;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class Main {
    // TODO: use classpath
    private static final String JAR = "jar:file:" + System.getProperty("user.home") + ".gradle/caches/fabric-loom/minecraft-1.14.4-pre1-merged.jar!/";

    public static void main(String[] args) throws IOException {
        Path pathFrom = getPath(Paths.get(args[0]));
        Path pathTo = getPath(Paths.get(args[1]));
        analyze(pathFrom, pathTo);
    }

    private static Path getPath(Path path) throws IOException {
        if (Files.isDirectory(path)) return path;
        HashMap<String, String> env = new HashMap<>();
        env.put("create", "true");
        URI uri = URI.create("jar:" + path.toUri());
        return FileSystems.newFileSystem(uri, env).getRootDirectories().iterator().next();
    }

    private static void readLines(InputStream stream, Consumer<String> lineHandler) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) lineHandler.accept(line);
        }
    }

    public static void analyze(Path pathFrom, Path pathTo) throws IOException {
        Path classPathFile = pathFrom.resolve("classpath.txt");
        if (!Files.exists(classPathFile)) {
            throw new IllegalArgumentException("No classpath.txt");
        }
        List<String> cp = new ArrayList<>();
        AtomicReference<String> version = new AtomicReference<>();
        readLines(Files.newInputStream(classPathFile), line -> {
            line = line.replace('\\', '/');
            if (line.contains("/libraries")) {
                cp.add(line.substring(line.indexOf("/libraries") + 1));
            } else if (line.contains("/versions")) {
                cp.add(line.substring(line.indexOf("/versions") + 1));
                version.set(line.substring(line.lastIndexOf('/') + 1, line.lastIndexOf(".jar")));
            }
        });
        Mappings mappings = YarnMappings.loadLatest(version.get());
        Deobfuscator deobfuscator = new Deobfuscator(mappings, new URLClassLoader(new URL[]{new URL(JAR)}));
        Files.walk(pathFrom).forEach(from -> {
            if (Files.isDirectory(from)) return;
            Path to = pathTo.resolve(pathFrom.relativize(from).toString());
            try {
                Files.createDirectories(to.getParent());
                transformFile(from, to, deobfuscator);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void transformFile(Path from, Path to, Deobfuscator deobfuscator) throws IOException {
        if (from.endsWith("example_crash.txt")) {
            try (BufferedReader reader = Files.newBufferedReader(from);
                 BufferedWriter writer = Files.newBufferedWriter(to)) {
                String line;

                while ((line = reader.readLine()) != null) {
                    StackTraceElement ste = StackTraceUtil.parse(line);
                    if (ste == null) writer.write(line);
                    else writer.write("\tat " + deobfuscator.deobfuscate(ste));
                    writer.write('\n');
                }
            }
            return;
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
}
