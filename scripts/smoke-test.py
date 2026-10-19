#!/usr/bin/env python3
"""Validate the release and a custom-content fixture against official Geyser 2.10.0 b1162."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import tempfile
import time
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--geyser-jar", required=True, type=Path)
    args = parser.parse_args()
    geyser = args.geyser_jar.resolve()
    expected = "ab1faf71f02655fcc35d41459eb8aaac6467e91890af4bb3963dcec8a0ec461b"
    with geyser.open("rb") as jar:
        if hashlib.file_digest(jar, "sha256").hexdigest() != expected:
            raise RuntimeError("Expected the official Geyser 2.10.0 build 1162 JAR (SHA-256 mismatch)")
    repo = Path(__file__).resolve().parent.parent
    release = repo / "build/libs/geyserreversion.jar"
    fixture = repo / "build/smoke-test/custom-content-fixture.jar"
    if not release.is_file() or not fixture.is_file():
        raise RuntimeError("Run: bash ./gradlew build shadowJar customContentFixtureJar")
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else shutil.which("java")
    if not java:
        raise RuntimeError("Java 21 is required")
    flags = ["-Xmx1g"]
    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("HTTP_PROXY")
    if proxy:
        parsed = urlsplit(proxy)
        if not parsed.hostname or parsed.username or parsed.password:
            raise RuntimeError("Unsupported proxy configuration")
        for scheme in ("http", "https"):
            flags += [f"-D{scheme}.proxyHost={parsed.hostname}", f"-D{scheme}.proxyPort={parsed.port or 80}"]
    truststore = Path("/etc/ssl/certs/java/cacerts")
    if truststore.is_file():
        flags.append(f"-Djavax.net.ssl.trustStore={truststore}")

    with tempfile.TemporaryDirectory(prefix="geyserreversion-smoke-") as directory:
        root = Path(directory)
        (root / "extensions").mkdir()
        shutil.copy2(release, root / "extensions/geyserreversion.jar")
        shutil.copy2(fixture, root / "extensions/custom-content-fixture.jar")
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        (root / "config.yml").write_text(f"""bedrock:
  address: 127.0.0.1
  port: {port}
java:
  address: 127.0.0.1
  port: 25565
  auth-type: offline
motd:
  primary-motd: Reversion compatibility smoke test
  passthrough-motd: false
  passthrough-player-counts: false
enable-metrics: false
notify-on-new-bedrock-update: false
""")
        log_path = root / "smoke.log"
        report = repo / "build/reports/smoke-test.log"
        report.parent.mkdir(parents=True, exist_ok=True)
        with log_path.open("w") as log:
            process = subprocess.Popen([java, *flags, "-jar", str(geyser), "--nogui"], cwd=root,
                                       stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 50
                while time.monotonic() < deadline:
                    output = log_path.read_text(errors="replace")
                    if process.poll() is not None or "CUSTOM_CONTENT_SMOKE_FAIL" in output:
                        raise RuntimeError("Geyser custom-content startup failed; see build/reports/smoke-test.log")
                    if "CUSTOM_CONTENT_SMOKE_PASS" in output and "Done (" in output:
                        break
                    time.sleep(0.2)
                else:
                    raise RuntimeError("Geyser readiness timed out; see build/reports/smoke-test.log")
                magic = bytes.fromhex("00ffff00fefefefefdfdfdfd12345678")
                ping = b"\x01" + struct.pack(">q", int(time.time() * 1000)) + magic + struct.pack(">q", 123456789)
                with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
                    client.settimeout(3)
                    client.sendto(ping, ("127.0.0.1", port))
                    pong, _ = client.recvfrom(4096)
                if pong[:1] != b"\x1c" or pong[17:33] != magic:
                    raise RuntimeError("Invalid RakNet pong")
                length = struct.unpack(">H", pong[33:35])[0]
                motd = pong[35:35 + length].decode()
                if "Reversion compatibility smoke test" not in motd or ";975;" not in motd:
                    raise RuntimeError("Unexpected Bedrock response: " + motd)
                print("PASS: Geyser startup, custom block palette, runtime item transformer and Bedrock ping")
                print(motd)
            finally:
                if process.poll() is None:
                    try:
                        process.communicate("geyser stop\n", timeout=10)
                    except subprocess.TimeoutExpired:
                        process.terminate()
                        try:
                            process.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait()
                shutil.copy2(log_path, report)


if __name__ == "__main__":
    main()
