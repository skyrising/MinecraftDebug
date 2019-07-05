package de.skyrising.minecraft.debug;

import de.skyrising.minecraft.deobf.Deobfuscator;
import de.skyrising.minecraft.deobf.Mappings;
import de.skyrising.minecraft.deobf.YarnMappings;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DebugAnalyzer {
    private final Path from;
    private final Path to;
    private ClassLoader classLoader;
    private Mappings mappings;
    private Deobfuscator deobfuscator;

    public DebugAnalyzer(Path from, Path to) {
        this.from = from;
        this.to = to;
    }

    public void analyze() throws IOException {
        Path classPathFile = from.resolve("classpath.txt");
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
        this.mappings = YarnMappings.loadLatest(version.get());
        this.classLoader = createClassLoader(cp);
        this.deobfuscator = new Deobfuscator(mappings, classLoader);
        Files.walk(this.from).forEach(from -> {
            if (Files.isDirectory(from)) return;
            Path to = this.to.resolve(this.from.relativize(from).toString());
            try {
                Files.createDirectories(to.getParent());
                transformFile(from, to);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static ClassLoader createClassLoader(List<String> paths) throws MalformedURLException {
        Path basePath = Paths.get(System.getProperty("user.home"), ".minecraft");
        URL[] urls = new URL[paths.size()];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = basePath.resolve(paths.get(i)).toUri().toURL();
        }
        return new URLClassLoader(urls);
    }

    private void transformFile(Path from, Path to) throws IOException {
        if (from.endsWith("example_crash.txt")) {
            try (BufferedReader reader = Files.newBufferedReader(from);
                 BufferedWriter writer = Files.newBufferedWriter(to, StandardOpenOption.TRUNCATE_EXISTING)) {
                String line;

                while ((line = reader.readLine()) != null) {
                    writer.write(transformCrashLine(line));
                    writer.write('\n');
                }
            }
            return;
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private String transformCrashLine(String line) {
        StackTraceElement ste = StackTraceUtil.parse(line);
        if (ste != null) return "\tat " + deobfuscator.deobfuscate(ste);
        if (line.startsWith("\t") && line.contains(":")) {
            int index = line.indexOf(':');
            String key = line.substring(1, index);
            String value = line.substring(index + 2);
            value = transformCrashReportDetail(key, value);
            return "\t" + key + ": " + value;
        }
        return line;
    }

    private String transformCrashReportDetail(String key, String value) {
        switch (key) {
            case "All players": case "Player Count": {
                String playerList = value.substring(value.indexOf('[') + 1, value.lastIndexOf(']'));
                String players = Arrays.stream(playerList.split("], \\["))
                    .map(p -> {
                        if (p.isEmpty()) return p;
                        int index = p.indexOf('[');
                        String className = mappings.deobfuscateClass(p.substring(0, index));
                        return className.substring(className.lastIndexOf('/') + 1) + p.substring(index);
                    }).collect(Collectors.joining("], ["));
                return value.replace(playerList, players);
            }
        }
        return value;
    }

    private static void readLines(InputStream stream, Consumer<String> lineHandler) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) lineHandler.accept(line);
        }
    }
}
