package netslice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Alokasi largest-first untuk LAN konvensional (network + broadcast dicadangkan). */
public final class Planner {
    private Planner() {}
    public static final int MAX_REQUESTS = 256;

    public record Request(String name, long hosts) {
        public Request {
            if (name == null || !name.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]{0,39}")) {
                throw new IllegalArgumentException("Nama segmen: 1-40 karakter huruf/angka/spasi/_/./-, diawali huruf atau angka.");
            }
            if (hosts < 1 || hosts > Subnet.ADDRESS_SPACE - 2) {
                throw new IllegalArgumentException("Kebutuhan host harus 1 sampai 4294967294.");
            }
        }

        public long blockSize() {
            long size = 4;
            while (size < hosts + 2) size <<= 1;
            return size;
        }
    }

    public record Allocation(Request request, Subnet subnet) {
        public long spareHosts() { return subnet.usableHosts() - request.hosts(); }
    }

    public record Plan(Subnet base, List<Allocation> allocations, List<Subnet> freeSubnets,
                       long requestedHosts, long allocatedAddresses, long allocatedUsableHosts) {
        public Plan {
            allocations = List.copyOf(allocations);
            freeSubnets = List.copyOf(freeSubnets);
        }
        public long freeAddresses() { return base.size() - allocatedAddresses; }
        public long spareHosts() { return allocatedUsableHosts - requestedHosts; }
    }

    public static final class CapacityException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public CapacityException(String message) { super(message); }
    }

    public static Request parseRequest(String text) {
        if (text == null || text.length() > 128) throw new IllegalArgumentException("Gunakan Nama=JumlahHost.");
        String[] parts = text.split("=", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Gunakan Nama=JumlahHost, misalnya Lab=60.");
        String count = parts[1].strip();
        if (!count.matches("[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException("Jumlah host harus bilangan bulat positif, tanpa nol di depan.");
        }
        return new Request(parts[0].strip(), Long.parseLong(count));
    }

    public static Plan allocate(Subnet base, List<Request> requests) {
        if (!base.aligned()) {
            throw new IllegalArgumentException("Jaringan induk harus network address. Gunakan " + base.cidr() + ".");
        }
        if (base.prefix() > 30) {
            throw new IllegalArgumentException("Perencana LAN menerima prefix /0 sampai /30; gunakan info untuk /31 atau /32.");
        }
        if (requests == null || requests.isEmpty() || requests.size() > MAX_REQUESTS) {
            throw new IllegalArgumentException("Masukkan 1 sampai 256 kebutuhan segmen.");
        }
        Set<String> names = new HashSet<>();
        long needed = 0;
        long hosts = 0;
        for (Request request : requests) {
            if (request == null) throw new IllegalArgumentException("Segmen tidak boleh kosong.");
            if (!names.add(request.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Nama segmen harus unik, termasuk perbedaan huruf besar/kecil.");
            }
            needed += request.blockSize();
            hosts += request.hosts();
        }
        if (needed > base.size()) {
            throw new CapacityException("Alamat tidak cukup: butuh " + needed + " alamat blok, tersedia "
                    + base.size() + " pada " + base.cidr() + ". Belum ada alokasi yang disimpan.");
        }
        List<Request> ordered = new ArrayList<>(requests);
        // List.sort stabil: ukuran blok sama mempertahankan urutan masukan.
        ordered.sort(Comparator.comparingLong(Request::blockSize).reversed());
        List<Allocation> allocations = new ArrayList<>();
        long cursor = base.network();
        long usable = 0;
        for (Request request : ordered) {
            long size = request.blockSize();
            if (cursor % size != 0) throw new IllegalStateException("Alokasi tidak sejajar pada batas CIDR.");
            Subnet subnet = new Subnet(cursor, prefixFor(size));
            allocations.add(new Allocation(request, subnet));
            usable += subnet.usableHosts();
            cursor += size;
        }
        List<Subnet> free = summarizeFree(cursor, base.lastAddress() + 1);
        return new Plan(base, allocations, free, hosts, needed, usable);
    }

    private static int prefixFor(long size) { return 32 - Long.numberOfTrailingZeros(size); }

    private static List<Subnet> summarizeFree(long start, long exclusiveEnd) {
        List<Subnet> result = new ArrayList<>();
        while (start < exclusiveEnd) {
            long alignment = start == 0 ? Subnet.ADDRESS_SPACE : Long.lowestOneBit(start);
            long size = Math.min(alignment, Long.highestOneBit(exclusiveEnd - start));
            result.add(new Subnet(start, prefixFor(size)));
            start += size;
        }
        return result;
    }
}
