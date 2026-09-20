package ez.factory.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class IO {

    public static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.toAbsolutePath().getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBytes(Path p, byte[] data) throws IOException {
        Files.createDirectories(p.toAbsolutePath().getParent());
        Files.write(p, data);
    }

    public static void ensureDir(Path p) throws IOException {
        Files.createDirectories(p);
    }

    public static void move(Path from, Path to) throws IOException {
        Files.createDirectories(to.toAbsolutePath().getParent());
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        }
    }

    public static String baseName(String filename) {
        int k = filename.lastIndexOf('.');
        return k < 0 ? filename : filename.substring(0, k);
    }
}
