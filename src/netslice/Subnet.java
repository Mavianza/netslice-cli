package netslice;

/** Perhitungan IPv4 murni; tidak melakukan DNS atau mengubah konfigurasi jaringan. */
public record Subnet(long address, int prefix) {
    public static final long MAX_ADDRESS = 0xffff_ffffL;
    public static final long ADDRESS_SPACE = 1L << 32;

    public Subnet {
        if (address < 0 || address > MAX_ADDRESS) {
            throw new IllegalArgumentException("Alamat IPv4 di luar rentang 32 bit.");
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Prefix CIDR harus 0 sampai 32.");
        }
    }

    public static Subnet parse(String cidr) {
        if (cidr == null || cidr.length() > 18) {
            throw new IllegalArgumentException("Gunakan format IPv4/prefix, misalnya 192.168.10.0/24.");
        }
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2 || !parts[1].matches("0|[1-9][0-9]?")) {
            throw new IllegalArgumentException("Gunakan format IPv4/prefix; prefix berupa angka 0 sampai 32.");
        }
        return new Subnet(parseAddress(parts[0]), Integer.parseInt(parts[1]));
    }

    public static long parseAddress(String value) {
        if (value == null || value.length() > 15) {
            throw new IllegalArgumentException("IPv4 harus terdiri dari empat oktet desimal.");
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("IPv4 harus terdiri dari empat oktet desimal.");
        }
        long result = 0;
        for (String octet : octets) {
            if (!octet.matches("0|[1-9][0-9]{0,2}")) {
                throw new IllegalArgumentException("Oktet IPv4 harus desimal 0-255, tanpa nol di depan.");
            }
            int number = Integer.parseInt(octet);
            if (number > 255) throw new IllegalArgumentException("Setiap oktet IPv4 harus 0 sampai 255.");
            result = (result << 8) | number;
        }
        return result;
    }

    public static String ip(long address) {
        if (address < 0 || address > MAX_ADDRESS) {
            throw new IllegalArgumentException("Alamat IPv4 di luar rentang 32 bit.");
        }
        return ((address >>> 24) & 255) + "." + ((address >>> 16) & 255) + "."
                + ((address >>> 8) & 255) + "." + (address & 255);
    }

    public long mask() { return (MAX_ADDRESS << (32 - prefix)) & MAX_ADDRESS; }
    public long wildcard() { return MAX_ADDRESS ^ mask(); }
    public long network() { return address & mask(); }
    public long size() { return 1L << (32 - prefix); }
    public long lastAddress() { return network() + size() - 1; }
    public long usableHosts() { return prefix <= 30 ? size() - 2 : size(); }
    public long firstHost() { return prefix <= 30 ? network() + 1 : network(); }
    public long lastHost() { return prefix <= 30 ? lastAddress() - 1 : lastAddress(); }
    public Long broadcast() { return prefix <= 30 ? lastAddress() : null; }
    public String cidr() { return ip(network()) + "/" + prefix; }
    public String inputCidr() { return ip(address) + "/" + prefix; }
    public boolean aligned() { return address == network(); }
    public Subnet canonical() { return new Subnet(network(), prefix); }
    public String kind() { return prefix == 32 ? "host_route" : prefix == 31 ? "point_to_point" : "lan"; }
}
