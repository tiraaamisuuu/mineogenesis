"""Check that the release JAR is self-contained and excludes the test mod."""
import io
import json
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as mod:
    names = mod.namelist()
    metadata = json.loads(mod.read("fabric.mod.json"))
    assert metadata["version"] == "0.1.0", "Unexpanded mod version"
    assert "LICENSE_mineogenesis" in names, "Missing project license"
    assert any(name.endswith("/AgentRenderer.class") for name in names), "Missing client renderer"
    assert not any("GameTests" in name or "mineogenesis-test" in name for name in names), "Test mod leaked into release"
    nested = metadata.get("jars", [])
    assert nested, "Core library was not included"
    core_class = "io/github/tiraaamisuuu/mineogenesis/core/ExperimentManager.class"
    assert any(core_class in zipfile.ZipFile(io.BytesIO(mod.read(entry["file"]))).namelist()
               for entry in nested), "Nested core library is incomplete"
print("PASS release packaging: renderer, embedded core, license, no integration-test mod")
