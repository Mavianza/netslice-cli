package netslice;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    private Main() {}
    private static final String HELP = """
            NetSlice 1.0.0 - Kalkulator IPv4 dan perencana VLSM LAN

            Penggunaan:
              java -jar dist/netslice.jar info IPv4/PREFIX [--json] [--out BERKAS] [--force]
              java -jar dist/netslice.jar plan NETWORK/PREFIX NAMA=HOST... [--json] [--out BERKAS] [--force]
              java -jar dist/netslice.jar plan NETWORK/PREFIX --file BERKAS [--json] [--out BERKAS] [--force]
              java -jar dist/netslice.jar --help
              java -jar dist/netslice.jar --version

            Contoh:
              java -jar dist/netslice.jar info 192.168.10.37/24
              java -jar dist/netslice.jar plan 192.168.10.0/24 Lab=60 Kelas=30 Server=12

            Default keluaran Markdown. Laporan tidak ditimpa kecuali memakai --force.
            plan: 1-256 segmen LAN; host diminta mencakup gateway jika dibutuhkan.
            Exit code: 0 berhasil, 2 masukan invalid, 3 kapasitas kurang, 4 operasi berkas gagal.
            """;

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        PrintWriter err = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    public static int run(String[] args, PrintWriter out, PrintWriter err) {
        try {
            if (args.length == 0 || (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h")))) {
                out.print(HELP);
                out.flush();
                return 0;
            }
            if (args.length == 1 && args[0].equals("--version")) {
                out.println("1.0.0");
                return 0;
            }
            if (!args[0].equals("info") && !args[0].equals("plan")) {
                throw new IllegalArgumentException("Perintah tidak dikenal. Gunakan --help.");
            }
            if (args.length == 2 && args[1].equals("--help")) {
                out.print(HELP);
                out.flush();
                return 0;
            }
            if (args.length < 2) throw new IllegalArgumentException("Alamat CIDR belum diberikan. Gunakan --help.");
            Subnet subnet = Subnet.parse(args[1]);
            boolean json = false;
            boolean force = false;
            Path output = null;
            Path input = null;
            List<Planner.Request> requests = new ArrayList<>();
            for (int index = 2; index < args.length; index++) {
                String argument = args[index];
                switch (argument) {
                    case "--json" -> {
                        if (json) throw new IllegalArgumentException("Opsi --json tidak boleh diulang.");
                        json = true;
                    }
                    case "--force" -> {
                        if (force) throw new IllegalArgumentException("Opsi --force tidak boleh diulang.");
                        force = true;
                    }
                    case "--out", "--file" -> {
                        if (++index >= args.length || args[index].startsWith("--") || args[index].isBlank()) {
                            throw new IllegalArgumentException("Opsi " + argument + " membutuhkan lokasi berkas.");
                        }
                        Path path = Path.of(args[index]);
                        if (argument.equals("--out")) {
                            if (output != null) throw new IllegalArgumentException("Opsi --out tidak boleh diulang.");
                            output = path;
                        } else {
                            if (!args[0].equals("plan")) throw new IllegalArgumentException("--file hanya tersedia pada plan.");
                            if (input != null) throw new IllegalArgumentException("Opsi --file tidak boleh diulang.");
                            input = path;
                        }
                    }
                    default -> {
                        if (argument.startsWith("-") || !args[0].equals("plan")) {
                            throw new IllegalArgumentException("Opsi atau argumen tidak dikenal. Gunakan --help.");
                        }
                        if (requests.size() >= Planner.MAX_REQUESTS) throw new IllegalArgumentException("Maksimum 256 segmen.");
                        requests.add(Planner.parseRequest(argument));
                    }
                }
            }
            if (force && output == null) throw new IllegalArgumentException("--force memerlukan --out.");
            if (input != null && !requests.isEmpty()) {
                throw new IllegalArgumentException("Pilih kebutuhan dari argumen atau --file, jangan keduanya.");
            }
            if (input != null && output != null) {
                if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                        || (Files.exists(output) && Files.isSameFile(input, output))) {
                    throw new IllegalArgumentException("Laporan tidak boleh menimpa berkas masukan.");
                }
            }
            if (input != null) requests = new ArrayList<>(PlanFile.read(input));
            String report;
            if (args[0].equals("info")) report = json ? Reports.infoJson(subnet) : Reports.infoMarkdown(subnet);
            else {
                Planner.Plan plan = Planner.allocate(subnet, requests);
                report = json ? Reports.planJson(plan) : Reports.planMarkdown(plan);
            }
            if (output != null) save(output, report, force);
            out.print(report);
            out.flush();
            return 0;
        } catch (Planner.CapacityException error) {
            err.println("Kapasitas tidak cukup: " + error.getMessage());
            return 3;
        } catch (IllegalArgumentException error) {
            err.println("Masukan tidak valid: " + error.getMessage());
            return 2;
        } catch (FileAlreadyExistsException error) {
            err.println("Laporan sudah ada. Pilih berkas lain atau gunakan --force.");
            return 4;
        } catch (IOException error) {
            err.println("Operasi berkas gagal: " + error.getMessage());
            return 4;
        }
    }

    private static void save(Path path, String report, boolean force) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        if (!force) {
            // CREATE_NEW menolak file atau symlink yang sudah ada, termasuk pada kondisi balapan.
            boolean created = false;
            try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                writer.write(report);
            } catch (IOException error) {
                if (created) {
                    try { Files.deleteIfExists(target); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
                }
                throw error;
            }
            return;
        }
        Path temporary = Files.createTempFile(target.getParent(), ".netslice-", ".tmp");
        try {
            Files.writeString(temporary, report, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
