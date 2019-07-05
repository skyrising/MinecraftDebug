package de.skyrising.minecraft.debug;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.HashMap;

public class Main {
        public static void main(String[] args) throws IOException {
        Path pathFrom = getPath(Paths.get(args[0]));
        Path pathTo = getPath(Paths.get(args[1]));
        System.out.println(pathFrom.toUri() + " -> " + pathTo.toUri());
        try {
            new DebugAnalyzer(pathFrom, pathTo).analyze();
        } finally {
            try {
                pathFrom.getFileSystem().close();
            } catch (UnsupportedOperationException ignored) {}
            try {
                pathTo.getFileSystem().close();
            } catch (UnsupportedOperationException ignored) {}
        }
    }

    private static Path getPath(Path path) throws IOException {
        if (Files.isDirectory(path)) return path;
        HashMap<String, String> env = new HashMap<>();
        env.put("create", "true");
        URI uri = URI.create("jar:" + path.toUri());
        return FileSystems.newFileSystem(uri, env).getRootDirectories().iterator().next();
    }
}
