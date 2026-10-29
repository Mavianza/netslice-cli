package netslice;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/** Harness uji mandiri: semua assertion aktif tanpa opsi -ea atau dependensi JUnit. */
public final class NetSliceTest {
    private int total;
    private int passed;
    private int directories;
    private final Path root;

    private NetSliceTest() throws IOException { root = Files.createTempDirectory("netslice-test-"); }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private record Result(int code, String out, String err) {}

    public static void main(String[] args) throws Exception {
        NetSliceTest suite = new NetSliceTest();
        try { suite.all(); } finally { suite.cleanup(); }
        System.out.println("\nHasil: " + suite.passed + "/" + suite.total + " tes lulus; " + (suite.total - suite.passed) + " gagal.");
        if (suite.passed != suite.total) System.exit(1);
    }

    private void test(String label, Checked body) {
        total++;
        try {
            body.run();
            passed++;
            System.out.println("LULUS " + total + " - " + label);
        } catch (Throwable error) {
            System.out.println("GAGAL " + total + " - " + label);
            error.printStackTrace(System.out);
        }
    }

    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Diharapkan " + expected + ", diterima " + actual);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static <T extends Throwable> T expect(Class<T> type, Checked body) throws Exception {
        try { body.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return type.cast(error);
            throw new AssertionError("Jenis exception tidak sesuai", error);
        }
        throw new AssertionError("Exception " + type.getSimpleName() + " seharusnya muncul.");
    }
    private Path directory() throws IOException { return Files.createDirectory(root.resolve("case-" + (++directories))); }
    private static Planner.Request req(String name, long hosts) { return new Planner.Request(name, hosts); }
    private static Planner.Plan sample() {
        return Planner.allocate(Subnet.parse("192.168.10.0/24"), List.of(req("Server", 12), req("Lab", 60), req("Kelas", 30)));
    }
    private static Result cli(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = Main.run(args, new PrintWriter(out, true), new PrintWriter(err, true));
        return new Result(code, out.toString(), err.toString());
    }

    private void all() {
        test("IPv4 /24: network, mask, broadcast, wildcard, dan rentang host", () -> {
            Subnet subnet = Subnet.parse("192.168.10.37/24");
            equal("192.168.10.0/24", subnet.cidr());
            equal("255.255.255.0", Subnet.ip(subnet.mask()));
            equal("0.0.0.255", Subnet.ip(subnet.wildcard()));
            equal("192.168.10.255", Subnet.ip(subnet.broadcast()));
            equal("192.168.10.1", Subnet.ip(subnet.firstHost()));
            equal("192.168.10.254", Subnet.ip(subnet.lastHost()));
            equal(254L, subnet.usableHosts());
        });
        test("IPv4 /0 tidak overflow dan mencakup seluruh ruang 32 bit", () -> {
            Subnet subnet = Subnet.parse("255.255.255.255/0");
            equal(4294967296L, subnet.size());
            equal(4294967294L, subnet.usableHosts());
            equal("0.0.0.0", Subnet.ip(subnet.mask()));
            equal("0.0.0.0/0", subnet.cidr());
            equal("255.255.255.255", Subnet.ip(subnet.lastAddress()));
        });
        test("/31 memakai kedua endpoint tanpa broadcast khusus", () -> {
            Subnet subnet = Subnet.parse("203.0.113.7/31");
            equal("203.0.113.6", Subnet.ip(subnet.firstHost()));
            equal("203.0.113.7", Subnet.ip(subnet.lastHost()));
            equal(2L, subnet.usableHosts());
            equal(null, subnet.broadcast());
            equal("point_to_point", subnet.kind());
        });
        test("/32 adalah host route satu alamat", () -> {
            Subnet subnet = Subnet.parse("255.255.255.255/32");
            equal(1L, subnet.size());
            equal(1L, subnet.usableHosts());
            equal(subnet.address(), subnet.firstHost());
            equal(subnet.address(), subnet.lastHost());
            equal(null, subnet.broadcast());
            equal("host_route", subnet.kind());
        });
        test("/30 pada batas atas IPv4 menghitung dua host dengan benar", () -> {
            Subnet subnet = Subnet.parse("255.255.255.253/30");
            equal("255.255.255.252/30", subnet.cidr());
            equal("255.255.255.254", Subnet.ip(subnet.lastHost()));
            equal("255.255.255.255", Subnet.ip(subnet.broadcast()));
        });
        test("parser menolak alamat ambigu, non-IPv4, dan prefix invalid", () -> {
            for (String value : List.of("localhost/24", "127.1/24", "192.168.01.1/24", "256.1.1.1/24",
                    "1.2.3.4/33", "1.2.3.4/-1", "1.2.3.4/024", "1.2.3.4", "::1/32", "1.2.3.4/24/1",
                    " 1.2.3.4/24", "1.2.3.4/24\n", "1..2.3/24")) {
                expect(IllegalArgumentException.class, () -> Subnet.parse(value));
            }
        });
        test("serialisasi alamat desimal kembali identik pada semua oktet", () -> {
            for (long value : new long[] {0, 1, 255, 256, 65535, 65536, 2147483648L, 4294967295L}) {
                equal(value, Subnet.parseAddress(Subnet.ip(value)));
            }
            expect(IllegalArgumentException.class, () -> Subnet.ip(4294967296L));
        });
        test("contoh VLSM: hasil largest-first sesuai perhitungan manual", () -> {
            Planner.Plan plan = sample();
            equal(List.of("192.168.10.0/26", "192.168.10.64/27", "192.168.10.96/28"),
                    plan.allocations().stream().map(item -> item.subnet().cidr()).toList());
            equal(List.of("Lab", "Kelas", "Server"), plan.allocations().stream().map(item -> item.request().name()).toList());
            equal(102L, plan.requestedHosts());
            equal(112L, plan.allocatedAddresses());
            equal(106L, plan.allocatedUsableHosts());
            equal(4L, plan.spareHosts());
            equal(144L, plan.freeAddresses());
        });
        test("sisa alokasi diringkas menjadi CIDR yang tepat", () -> {
            equal(List.of("192.168.10.112/28", "192.168.10.128/25"), sample().freeSubnets().stream().map(Subnet::cidr).toList());
        });
        test("ukuran blok sama mempertahankan urutan masukan", () -> {
            List<Planner.Request> input = new ArrayList<>(List.of(req("A", 3), req("B", 6), req("C", 4)));
            List<Planner.Request> before = List.copyOf(input);
            Planner.Plan plan = Planner.allocate(Subnet.parse("10.0.0.0/24"), input);
            equal(before, input);
            equal(List.of("A", "B", "C"), plan.allocations().stream().map(item -> item.request().name()).toList());
        });
        test("kebutuhan satu atau dua host tetap memakai LAN /30", () -> {
            Planner.Plan plan = Planner.allocate(Subnet.parse("10.0.0.0/29"), List.of(req("Satu", 1), req("Dua", 2)));
            equal(List.of(30, 30), plan.allocations().stream().map(item -> item.subnet().prefix()).toList());
            equal(0L, plan.freeAddresses());
            equal(1L, plan.spareHosts());
        });
        test("kapasitas pas tidak menghasilkan blok sisa", () -> {
            Planner.Plan plan = Planner.allocate(Subnet.parse("10.0.0.0/24"), List.of(req("Penuh", 254)));
            equal(0L, plan.freeAddresses());
            check(plan.freeSubnets().isEmpty(), "Tidak boleh ada blok sisa");
        });
        test("kapasitas kurang dihitung dari blok, bukan jumlah host saja", () -> {
            Planner.CapacityException error = expect(Planner.CapacityException.class,
                    () -> Planner.allocate(Subnet.parse("10.0.0.0/25"), List.of(req("Besar", 126), req("Kecil", 1))));
            check(error.getMessage().contains("132") && error.getMessage().contains("128"), "Pesan harus mencantumkan kapasitas");
        });
        test("induk non-network ditolak dengan saran network address", () -> {
            IllegalArgumentException error = expect(IllegalArgumentException.class,
                    () -> Planner.allocate(Subnet.parse("10.0.0.1/24"), List.of(req("A", 2))));
            check(error.getMessage().contains("10.0.0.0/24"), "Saran network tidak tersedia");
        });
        test("perencana LAN menolak induk /31 dan /32", () -> {
            for (int prefix : List.of(31, 32)) expect(IllegalArgumentException.class,
                    () -> Planner.allocate(Subnet.parse("10.0.0.0/" + prefix), List.of(req("A", 1))));
        });
        test("nama duplikat tanpa membedakan huruf besar ditolak", () -> {
            expect(IllegalArgumentException.class, () -> Planner.allocate(Subnet.parse("10.0.0.0/24"), List.of(req("Lab", 2), req("lab", 2))));
        });
        test("nama, jumlah, dan jumlah segmen divalidasi", () -> {
            for (String value : List.of("A=0", "A=-1", "A=01", "A=1.5", "A=4294967295", "=10", "A|B=10", "A=1=2")) {
                expect(IllegalArgumentException.class, () -> Planner.parseRequest(value));
            }
            expect(IllegalArgumentException.class, () -> Planner.allocate(Subnet.parse("10.0.0.0/24"), List.of()));
            List<Planner.Request> many = new ArrayList<>();
            for (int i = 0; i < 257; i++) many.add(req("A" + i, 1));
            expect(IllegalArgumentException.class, () -> Planner.allocate(Subnet.parse("0.0.0.0/0"), many));
        });
        test("permintaan maksimum /0 tidak overflow", () -> {
            Planner.Plan plan = Planner.allocate(Subnet.parse("0.0.0.0/0"), List.of(req("Seluruh", 4294967294L)));
            equal("0.0.0.0/0", plan.allocations().get(0).subnet().cidr());
            equal(4294967296L, plan.allocatedAddresses());
            equal(0L, plan.freeAddresses());
            expect(Planner.CapacityException.class,
                    () -> Planner.allocate(Subnet.parse("0.0.0.0/0"), List.of(req("A", 4294967294L), req("B", 1))));
        });
        test("300 rencana acak: kapasitas, alignment, tanpa overlap, dan cakupan sisa", () -> {
            Random random = new Random(20261029L);
            for (int iteration = 0; iteration < 300; iteration++) {
                int prefix = 16 + random.nextInt(15);
                Subnet base = new Subnet(Integer.toUnsignedLong(random.nextInt()), prefix).canonical();
                List<Planner.Request> requests = new ArrayList<>();
                long expectedAddresses = 0;
                int count = 1 + random.nextInt(12);
                for (int i = 0; i < count; i++) {
                    long hosts = 1 + random.nextInt(500);
                    requests.add(req("Segmen" + i, hosts));
                    expectedAddresses += 1L << (64 - Long.numberOfLeadingZeros(hosts + 1));
                }
                if (expectedAddresses > base.size()) {
                    expect(Planner.CapacityException.class, () -> Planner.allocate(base, requests));
                    continue;
                }
                Planner.Plan plan = Planner.allocate(base, requests);
                equal(expectedAddresses, plan.allocatedAddresses());
                long cursor = base.network();
                for (Planner.Allocation allocation : plan.allocations()) {
                    Subnet subnet = allocation.subnet();
                    check(subnet.aligned(), "Subnet tidak aligned");
                    equal(cursor, subnet.network());
                    check(subnet.usableHosts() >= allocation.request().hosts(), "Host tidak cukup");
                    check(subnet.size() == 4 || subnet.size() / 2 - 2 < allocation.request().hosts(), "Blok tidak minimal");
                    cursor = subnet.lastAddress() + 1;
                }
                for (Subnet free : plan.freeSubnets()) {
                    check(free.aligned(), "Blok sisa tidak aligned");
                    equal(cursor, free.network());
                    cursor = free.lastAddress() + 1;
                }
                equal(base.lastAddress() + 1, cursor);
            }
        });
        test("berkas mendukung BOM, CRLF, komentar, dan nama dengan spasi", () -> {
            Path path = directory().resolve("segments.plan");
            Files.writeString(path, "\uFEFF# daftar\r\n\r\nLab Komputer = 60 # termasuk gateway\r\nKelas=30\r\n", StandardCharsets.UTF_8);
            equal(List.of(req("Lab Komputer", 60), req("Kelas", 30)), PlanFile.read(path));
        });
        test("kesalahan berkas menyebut nomor baris", () -> {
            Path path = directory().resolve("segments.plan");
            Files.writeString(path, "# header\nA=20\nB=x\n");
            IllegalArgumentException error = expect(IllegalArgumentException.class, () -> PlanFile.read(path));
            check(error.getMessage().startsWith("Baris 3:"), "Nomor baris salah");
        });
        test("berkas lebih dari 64 KiB, non-UTF8, dan hilang ditangani", () -> {
            Path path = directory().resolve("segments.plan");
            Files.writeString(path, "#".repeat(PlanFile.MAX_BYTES + 1));
            expect(IllegalArgumentException.class, () -> PlanFile.read(path));
            Files.write(path, new byte[] {(byte) 0xff, (byte) 0xfe});
            expect(IOException.class, () -> PlanFile.read(path));
            expect(IOException.class, () -> PlanFile.read(path.resolveSibling("missing")));
        });
        test("CLI bantuan dan versi tanpa input lain", () -> {
            equal(0, cli().code());
            check(cli("--help").out().contains("Penggunaan"), "Bantuan tidak tersedia");
            equal("1.0.0", cli("--version").out().strip());
            equal(0, cli("plan", "--help").code());
        });
        test("CLI info menormalisasi host dan JSON /31 memakai null broadcast", () -> {
            Result result = cli("info", "192.168.1.37/24");
            equal(0, result.code());
            check(result.out().contains("192.168.1.0/24") && result.out().contains("dinormalisasi"), "Normalisasi tidak jelas");
            Result json = cli("info", "203.0.113.6/31", "--json");
            equal(0, json.code());
            check(json.out().contains("\"broadcast\": null"), "Broadcast harus null");
            check(json.out().contains("\"usableHosts\": 2"), "Kapasitas /31 salah");
        });
        test("CLI plan membaca file dan menyimpan laporan UTF8", () -> {
            Path dir = directory();
            Path input = dir.resolve("plan.txt");
            Path output = dir.resolve("hasil baru/report.json");
            Files.writeString(input, "Jaringan É=12\nLab=60\n", StandardCharsets.UTF_8);
            Result result = cli("plan", "192.168.10.0/24", "--file", input.toString(), "--json", "--out", output.toString());
            equal(0, result.code());
            equal(result.out(), Files.readString(output, StandardCharsets.UTF_8));
            check(result.out().contains("Jaringan É"), "Nama UTF8 hilang");
        });
        test("laporan tidak ditimpa tanpa --force dan bisa diganti dengan --force", () -> {
            Path output = directory().resolve("report.md");
            Files.writeString(output, "lama");
            equal(4, cli("info", "10.0.0.0/24", "--out", output.toString()).code());
            equal("lama", Files.readString(output));
            equal(0, cli("info", "10.0.0.0/24", "--out", output.toString(), "--force").code());
            check(Files.readString(output).startsWith("# NetSlice"), "Laporan baru tidak tersimpan");
            try (Stream<Path> files = Files.list(output.getParent())) { equal(1L, files.count()); }
        });
        test("input tidak dapat ditimpa walau memakai --force", () -> {
            Path input = directory().resolve("plan.txt");
            Files.writeString(input, "A=20\n");
            Result result = cli("plan", "10.0.0.0/24", "--file", input.toString(), "--out", input.toString(), "--force");
            equal(2, result.code());
            equal("A=20\n", Files.readString(input));
        });
        test("kapasitas kurang memberi exit code 3 dan tidak membuat laporan", () -> {
            Path output = directory().resolve("must-not-exist.md");
            Result result = cli("plan", "10.0.0.0/30", "A=3", "--out", output.toString());
            equal(3, result.code());
            check(!Files.exists(output), "Laporan tidak boleh dibuat");
            equal("", result.out());
        });
        test("opsi invalid, berulang, atau tidak lengkap memberi exit code 2", () -> {
            List<String[]> cases = List.of(
                    new String[] {"unknown"}, new String[] {"info"},
                    new String[] {"info", "10.0.0.0/24", "--json", "--json"},
                    new String[] {"info", "10.0.0.0/24", "--out"},
                    new String[] {"info", "10.0.0.0/24", "--force"},
                    new String[] {"info", "10.0.0.0/24", "--file", "x"},
                    new String[] {"plan", "10.0.0.0/24", "--file", "x", "A=20"},
                    new String[] {"plan", "10.0.0.0/24", "A=20", "--unknown"},
                    new String[] {"plan", "10.0.0.0/24"});
            for (String[] args : cases) equal(2, cli(args).code());
        });
        test("berkas input hilang dan tujuan bukan berkas memberi exit code 4", () -> {
            Path dir = directory();
            equal(4, cli("plan", "10.0.0.0/24", "--file", dir.resolve("missing").toString()).code());
            equal(4, cli("info", "10.0.0.0/24", "--out", dir.toString()).code());
        });
        test("contoh berkas kampus menghasilkan laporan Markdown lengkap", () -> {
            Result result = cli("plan", "192.168.10.0/24", "--file", "examples/kampus.plan");
            equal(0, result.code());
            check(result.out().contains("Laboratorium") && result.out().contains("192.168.10.0/26"), "Alokasi contoh hilang");
            check(result.out().contains("192.168.10.112/28") && result.out().contains("192.168.10.128/25"), "Sisa CIDR hilang");
            check(result.out().contains("**144**"), "Jumlah sisa alamat salah");
        });
    }

    private void cleanup() throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
