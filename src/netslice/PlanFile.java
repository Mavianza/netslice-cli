package netslice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PlanFile {
    private PlanFile() {}
    public static final int MAX_BYTES = 65536;

    public static List<Planner.Request> read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Daftar segmen harus berupa berkas biasa yang dapat dibaca.");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Daftar segmen maksimum 64 KiB.");
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\\R", -1);
        List<Planner.Request> requests = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            if (line.isBlank()) continue;
            try {
                requests.add(Planner.parseRequest(line));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Baris " + (index + 1) + ": " + error.getMessage(), error);
            }
            if (requests.size() > Planner.MAX_REQUESTS) {
                throw new IllegalArgumentException("Daftar segmen maksimum 256 kebutuhan.");
            }
        }
        return List.copyOf(requests);
    }
}
