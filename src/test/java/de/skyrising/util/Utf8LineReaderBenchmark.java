package de.skyrising.util;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(time = 1)
@Fork(1)
public class Utf8LineReaderBenchmark {
    private Utf8LineReader reader;

    @Param({"true", "false"})
    public boolean direct;
    @Param({"1024", "2048", "4096", "8192"})
    public int bufSize;

    @Setup
    public void setup() throws IOException {
        ReadableByteChannel channel = Files.newByteChannel(Paths.get(System.getProperty("user.home"), "/.gradle/caches/fabric-loom/mappings/net.fabricmc.yarn-tiny-1.14.4-pre1-1"));
        reader = new Utf8LineReader(channel, bufSize, direct);
    }

    @Benchmark
    public void testBasic(Blackhole bh) throws IOException {
        int pos = reader.buf.position();
        bh.consume(reader.readLine());
        reader.buf.position(pos);
    }
}
