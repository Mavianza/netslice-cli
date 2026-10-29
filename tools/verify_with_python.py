#!/usr/bin/env python3
"""Oracle opsional: bandingkan keluaran JAR dengan ipaddress bawaan Python."""
import argparse
import ipaddress
import json
import platform
import random
import subprocess
from pathlib import Path


def invoke(jar, *arguments):
    return subprocess.run(
        ["java", "-jar", str(jar), *arguments],
        capture_output=True, text=True, encoding="utf-8", timeout=10, check=False,
    )


def compare_subnet(actual, network):
    prefix = network.prefixlen
    addresses = network.num_addresses
    assert actual["cidr"] == str(network)
    assert actual["network"] == str(network.network_address)
    assert actual["prefix"] == prefix
    assert actual["mask"] == str(network.netmask)
    assert actual["wildcard"] == str(network.hostmask)
    assert actual["lastAddress"] == str(network.broadcast_address)
    assert actual["totalAddresses"] == addresses
    if prefix <= 30:
        assert actual["usableHosts"] == addresses - 2
        assert actual["firstHost"] == str(network.network_address + 1)
        assert actual["lastHost"] == str(network.broadcast_address - 1)
        assert actual["broadcast"] == str(network.broadcast_address)
        assert actual["kind"] == "lan"
    else:
        assert actual["usableHosts"] == addresses
        assert actual["firstHost"] == str(network.network_address)
        assert actual["lastHost"] == str(network.broadcast_address)
        assert actual["broadcast"] is None
        assert actual["kind"] == ("point_to_point" if prefix == 31 else "host_route")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=Path("dist/netslice.jar"))
    args = parser.parse_args()
    if not args.jar.is_file():
        parser.error("JAR tidak ditemukan; jalankan java Build.java terlebih dahulu.")
    randomizer = random.Random(20261029)
    cases = [f"{ipaddress.IPv4Address(randomizer.randrange(2**32))}/{prefix}" for prefix in range(33)]
    cases += ["0.0.0.0/0", "255.255.255.255/0", "255.255.255.255/31", "255.255.255.255/32"]
    while len(cases) < 100:
        cases.append(f"{ipaddress.IPv4Address(randomizer.randrange(2**32))}/{randomizer.randrange(33)}")
    for cidr in cases:
        result = invoke(args.jar, "info", cidr, "--json")
        assert result.returncode == 0, (cidr, result.stderr)
        data = json.loads(result.stdout)
        interface = ipaddress.IPv4Interface(cidr)
        assert data["normalized"] == (interface.ip != interface.network.network_address)
        compare_subnet(data["subnet"], interface.network)

    plans = [
        (ipaddress.IPv4Network("192.168.10.0/24"), [12, 60, 30]),
        (ipaddress.IPv4Network("0.0.0.0/0"), [4294967294]),
        (ipaddress.IPv4Network("255.255.255.252/30"), [1]),
        (ipaddress.IPv4Network("10.0.0.0/29"), [2, 2]),
        (ipaddress.IPv4Network("10.0.0.0/25"), [126, 1]),
    ]
    while len(plans) < 40:
        prefix = randomizer.randrange(16, 31)
        network = ipaddress.IPv4Network((randomizer.randrange(2**32), prefix), strict=False)
        counts = [randomizer.randrange(1, 501) for _ in range(randomizer.randrange(1, 10))]
        plans.append((network, counts))
    success = insufficient = 0
    for network, counts in plans:
        requests = [(f"Segmen{i}", hosts) for i, hosts in enumerate(counts)]
        blocks = [(name, hosts, 1 << (hosts + 1).bit_length()) for name, hosts in requests]
        result = invoke(args.jar, "plan", str(network), *(f"{name}={hosts}" for name, hosts in requests), "--json")
        needed = sum(block for _, _, block in blocks)
        if needed > network.num_addresses:
            assert result.returncode == 3, (str(network), result.stderr)
            assert not result.stdout
            insufficient += 1
            continue
        assert result.returncode == 0, (str(network), result.stderr)
        data = json.loads(result.stdout)
        compare_subnet(data["base"], network)
        ordered = sorted(blocks, key=lambda item: -item[2])
        assert len(data["allocations"]) == len(ordered)
        cursor = int(network.network_address)
        usable = 0
        for actual, (name, hosts, block) in zip(data["allocations"], ordered):
            expected = ipaddress.IPv4Network((cursor, 32 - (block.bit_length() - 1)), strict=True)
            assert actual["name"] == name
            assert actual["requestedHosts"] == hosts
            assert actual["spareUsableHosts"] == block - 2 - hosts
            compare_subnet(actual["subnet"], expected)
            cursor += block
            usable += block - 2
        expected_free = [] if cursor > int(network.broadcast_address) else [
            str(item) for item in ipaddress.summarize_address_range(ipaddress.IPv4Address(cursor), network.broadcast_address)
        ]
        assert data["freeCidrs"] == expected_free
        assert data["summary"] == {
            "requestedHosts": sum(counts),
            "allocatedAddresses": needed,
            "allocatedUsableHosts": usable,
            "spareUsableHosts": usable - sum(counts),
            "freeAddresses": network.num_addresses - needed,
        }
        success += 1
    print(json.dumps({
        "status": "passed",
        "oracle": "Python standard library ipaddress",
        "pythonVersion": platform.python_version(),
        "randomSeed": 20261029,
        "infoCases": len(cases),
        "planCases": len(plans),
        "validPlans": success,
        "correctlyRejectedPlans": insufficient,
        "totalCases": len(cases) + len(plans),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
