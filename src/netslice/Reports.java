package netslice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Reports {
    private Reports() {}

    public static Map<String, Object> subnetData(Subnet subnet) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cidr", subnet.cidr());
        data.put("network", Subnet.ip(subnet.network()));
        data.put("prefix", subnet.prefix());
        data.put("mask", Subnet.ip(subnet.mask()));
        data.put("wildcard", Subnet.ip(subnet.wildcard()));
        data.put("lastAddress", Subnet.ip(subnet.lastAddress()));
        data.put("broadcast", subnet.broadcast() == null ? null : Subnet.ip(subnet.broadcast()));
        data.put("firstHost", Subnet.ip(subnet.firstHost()));
        data.put("lastHost", Subnet.ip(subnet.lastHost()));
        data.put("totalAddresses", subnet.size());
        data.put("usableHosts", subnet.usableHosts());
        data.put("kind", subnet.kind());
        return data;
    }

    public static String infoJson(Subnet subnet) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("command", "info");
        data.put("inputCidr", subnet.inputCidr());
        data.put("normalized", !subnet.aligned());
        data.put("subnet", subnetData(subnet));
        return json(data) + "\n";
    }

    public static String planJson(Planner.Plan plan) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("command", "plan");
        data.put("base", subnetData(plan.base()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requestedHosts", plan.requestedHosts());
        summary.put("allocatedAddresses", plan.allocatedAddresses());
        summary.put("allocatedUsableHosts", plan.allocatedUsableHosts());
        summary.put("spareUsableHosts", plan.spareHosts());
        summary.put("freeAddresses", plan.freeAddresses());
        data.put("summary", summary);
        List<Map<String, Object>> allocations = new ArrayList<>();
        for (Planner.Allocation allocation : plan.allocations()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", allocation.request().name());
            item.put("requestedHosts", allocation.request().hosts());
            item.put("spareUsableHosts", allocation.spareHosts());
            item.put("subnet", subnetData(allocation.subnet()));
            allocations.add(item);
        }
        data.put("allocations", allocations);
        data.put("freeCidrs", plan.freeSubnets().stream().map(Subnet::cidr).toList());
        return json(data) + "\n";
    }

    public static String infoMarkdown(Subnet subnet) {
        StringBuilder out = new StringBuilder("# NetSlice - Informasi Subnet\n\n");
        out.append("| Properti | Nilai |\n| --- | --- |\n");
        row(out, "Masukan", subnet.inputCidr());
        row(out, "Network CIDR", subnet.cidr());
        row(out, "Subnet mask", Subnet.ip(subnet.mask()));
        row(out, "Wildcard mask", Subnet.ip(subnet.wildcard()));
        row(out, "Network address", Subnet.ip(subnet.network()));
        row(out, "Alamat terakhir blok", Subnet.ip(subnet.lastAddress()));
        row(out, "Broadcast", subnet.broadcast() == null ? "Tidak ada broadcast khusus" : Subnet.ip(subnet.broadcast()));
        row(out, "Host pertama", Subnet.ip(subnet.firstHost()));
        row(out, "Host terakhir", Subnet.ip(subnet.lastHost()));
        row(out, "Jumlah alamat", Long.toString(subnet.size()));
        row(out, "Kapasitas host", Long.toString(subnet.usableHosts()));
        out.append('\n');
        if (!subnet.aligned()) out.append("Masukan dinormalisasi ke network address ").append(subnet.cidr()).append(".\n\n");
        if (subnet.prefix() == 31) {
            out.append("/31 ditafsirkan sebagai sambungan point-to-point: kedua alamat menjadi endpoint (RFC 3021).\n");
        } else if (subnet.prefix() == 32) {
            out.append("/32 ditafsirkan sebagai host route satu alamat, bukan LAN dengan network/broadcast terpisah.\n");
        } else {
            out.append("Kapasitas LAN dihitung sebagai jumlah alamat dikurangi network address dan broadcast.\n");
        }
        out.append("Perhitungan ini tidak memeriksa status reservasi atau keterjangkauan alamat di jaringan nyata.\n");
        return out.toString();
    }

    public static String planMarkdown(Planner.Plan plan) {
        StringBuilder out = new StringBuilder("# NetSlice - Rencana VLSM\n\n");
        out.append("Jaringan induk: **").append(plan.base().cidr()).append("**. Alokasi dari blok terbesar; ukuran sama mengikuti urutan masukan.\n\n");
        out.append("| Segmen | Host diminta | CIDR | Subnet mask | Host pertama | Host terakhir | Broadcast | Host cadangan |\n");
        out.append("| --- | ---: | --- | --- | --- | --- | --- | ---: |\n");
        for (Planner.Allocation allocation : plan.allocations()) {
            Subnet subnet = allocation.subnet();
            out.append("| ").append(allocation.request().name()).append(" | ").append(allocation.request().hosts())
                    .append(" | ").append(subnet.cidr()).append(" | ").append(Subnet.ip(subnet.mask()))
                    .append(" | ").append(Subnet.ip(subnet.firstHost())).append(" | ").append(Subnet.ip(subnet.lastHost()))
                    .append(" | ").append(Subnet.ip(subnet.lastAddress())).append(" | ").append(allocation.spareHosts()).append(" |\n");
        }
        out.append("\nHost diminta: **").append(plan.requestedHosts()).append("**. Kapasitas host dialokasikan: **")
                .append(plan.allocatedUsableHosts()).append("**. Host cadangan di dalam alokasi: **")
                .append(plan.spareHosts()).append("**.\n\n");
        out.append("Alamat blok terpakai: **").append(plan.allocatedAddresses()).append("** dari **")
                .append(plan.base().size()).append("**. Alamat belum dialokasikan: **").append(plan.freeAddresses()).append("**.\n\n");
        out.append("## Blok yang belum dialokasikan\n\n");
        if (plan.freeSubnets().isEmpty()) out.append("Tidak ada; seluruh jaringan induk telah dialokasikan.\n");
        else for (Subnet subnet : plan.freeSubnets()) out.append("- ").append(subnet.cidr()).append(" (")
                .append(subnet.size()).append(" alamat)\n");
        out.append("\nJumlah host diminta harus sudah mencakup gateway dan perangkat infrastruktur. Tidak ada gateway yang dicadangkan otomatis.\n");
        return out.toString();
    }

    private static void row(StringBuilder out, String key, String value) {
        out.append("| ").append(key).append(" | ").append(value).append(" |\n");
    }

    private static String json(Object value) {
        StringBuilder out = new StringBuilder();
        encode(value, out, 0);
        return out.toString();
    }

    private static void encode(Object value, StringBuilder out, int level) {
        if (value == null) out.append("null");
        else if (value instanceof String text) quote(text, out);
        else if (value instanceof Number || value instanceof Boolean) out.append(value);
        else if (value instanceof Map<?, ?> map) {
            out.append('{');
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index++ > 0) out.append(',');
                indent(out, level + 1);
                quote(entry.getKey().toString(), out);
                out.append(": ");
                encode(entry.getValue(), out, level + 1);
            }
            if (!map.isEmpty()) indent(out, level);
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                indent(out, level + 1);
                encode(list.get(i), out, level + 1);
            }
            if (!list.isEmpty()) indent(out, level);
            out.append(']');
        } else throw new IllegalArgumentException("Tipe laporan JSON tidak didukung.");
    }

    private static void indent(StringBuilder out, int level) {
        out.append('\n').append("  ".repeat(level));
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
