# SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
"""Independent original-source M1 review probes; run after locked debug/release builds.

Usage: python /tmp/nexwrite-m1-core-review.py /workspace/Codex/NexWrite
No repository files are edited. Output is JSON, using only original synthetic text.
Performance is a single local native CLI observation, including process startup,
file I/O and JSON serialization to /dev/null. It is not a browser/mobile/CI budget.
"""
import json
import pathlib
import random
import subprocess
import sys
import tempfile
import time

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/workspace/Codex/NexWrite")
DEBUG = ROOT / "target/debug/nw"
RELEASE = ROOT / "target/release/nw"
report = {"scope": "independent original-source core review", "seed": 20261020,
          "generated_cases": 400, "regressions": [], "performance": [], "errors": []}


def analyze(text):
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8") as source:
        source.write(text)
        source.flush()
        result = subprocess.run([str(DEBUG), "analyze", source.name, "--format", "json",
                                 "--input-format", "markdown"], capture_output=True, text=True,
                                timeout=20)
        if result.returncode:
            raise AssertionError("valid synthetic Markdown rejected: " + result.stderr[:300])
        return json.loads(result.stdout)


def coordinates(text, analysis):
    raw = text.encode()

    def check(span):
        start, end = span["utf8"]
        assert 0 <= start < end <= len(raw)
        before, through = raw[:start].decode(), raw[:end].decode()
        assert span["utf16"] == [len(before.encode("utf-16-le")) // 2,
                                 len(through.encode("utf-16-le")) // 2]
        assert span["scalar"] == [len(before), len(through)]

    prose = analysis["prose_regions"]
    protected = [part["span"] for part in analysis["protected_regions"]]
    for regions in (prose, protected):
        for span in regions:
            check(span)
        assert all(left["utf8"][1] < right["utf8"][0]
                   for left, right in zip(regions, regions[1:]))
    for span in prose:
        assert all(span["utf8"][1] <= part["utf8"][0] or part["utf8"][1] <= span["utf8"][0]
                   for part in protected)
    for span in ([token["span"] for token in analysis["tokens"]] + analysis["sentences"]
                 + analysis["paragraphs"] + [item["span"] for item in analysis["suggestions"]]):
        check(span)
        assert any(part["utf8"][0] <= span["utf8"][0] and span["utf8"][1] <= part["utf8"][1]
                   for part in prose)


try:
    rng = random.Random(report["seed"])
    chunks = ["a", "Z", "😀", "e\u0301", "שלום", "<", ">", "\\", "`", "[", "]", "!", "&",
              ";", " ", "  ", "\n", "\r", "\t", "*", "#", "(", ")", ":", "/", '"', "0"]
    for index in range(report["generated_cases"]):
        text = "".join(rng.choice(chunks) for _ in range(rng.randint(0, 120)))
        coordinates(text, analyze(text))
    report["generated_coordinates_and_containment"] = "PASS"
except Exception as error:
    report["errors"].append({"phase": "generated_coordinates", "case": index, "error": str(error)})

definitions = [
    ('[a]:\n  /first "title"\n\n[a]:\n  /second "second  title"\n\nprose  text',
     '[a]:\n  /second "second  title"\n'),
    ('[a]: /first\n\n[a]: /second\n  "a [bracket]  title"\n\nprose  text',
     '[a]: /second\n  "a [bracket]  title"\n'),
    ('[a]: /first\n\n[a]:\n  /second "a [b]  title"\n\nprose  text',
     '[a]:\n  /second "a [b]  title"\n'),
]
for index, (text, excluded) in enumerate(definitions):
    try:
        analysis = analyze(text)
        coordinates(text, analysis)
        start = text.index(excluded)
        assert any(part["kind"] == "reference_definition"
                   and part["span"]["utf8"][0] <= start
                   and start + len(excluded) <= part["span"]["utf8"][1]
                   for part in analysis["protected_regions"])
        assert "markdown_prose_fragmented" not in analysis["metrics"]["warnings"]
        report["regressions"].append({"case": "duplicate_multiline_definition_" + str(index), "result": "PASS"})
    except Exception as error:
        report["errors"].append({"phase": "definition_regression", "case": index, "error": str(error)})
try:
    analysis = analyze("<script\nalert\n")
    assert "markdown_ambiguous_construct" in analysis["metrics"]["warnings"]
    assert not analysis["prose_regions"]
    report["regressions"].append({"case": "malformed_parser_html_warning", "result": "PASS"})
except Exception as error:
    report["errors"].append({"phase": "html_regression", "error": str(error)})

limit = 1_048_576
cases = {
    "unclosed_brackets": "[" * limit,
    "balanced_brackets": ("[] " * ((limit // 3) + 1))[:limit],
    "many_links": ("[a](b) " * ((limit // 7) + 1))[:limit],
    "many_inline_code": ("`x` " * ((limit // 4) + 1))[:limit],
    "many_entities": ("&amp; " * ((limit // 6) + 1))[:limit],
    "many_headings": ("# a\n" * ((limit // 4) + 1))[:limit],
    "plain_words": ("a " * ((limit // 2) + 1))[:limit],
    "dense_suggestions": ("a  " * ((limit // 3) + 1))[:limit],
    "html_suffix_scan_128k": "<a " * ((131_072 - 2) // 3) + "<>",
    "html_suffix_scan_512k": "<a " * ((524_288 - 2) // 3) + "<>",
    "html_suffix_scan_1m": "<a " * ((limit - 2) // 3) + "<>",
}
wrapper = ('import subprocess,sys,time,resource,json; t=time.monotonic(); '
           'r=subprocess.run([sys.argv[1],"analyze",sys.argv[2],"--format","json",'
           '"--input-format","markdown"],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,'
           'text=True,timeout=25); '
           'print(json.dumps({"exit":r.returncode,"wall_s":round(time.monotonic()-t,3),'
           '"max_rss_kib":resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss,'
           '"stderr":r.stderr[:300]}))')
for name, text in cases.items():
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8") as source:
            source.write(text)
            source.flush()
            result = subprocess.run([sys.executable, "-c", wrapper, str(RELEASE), source.name],
                                    capture_output=True, text=True, timeout=27)
            assert result.returncode == 0, result.stderr[:300]
            measurement = json.loads(result.stdout)
            assert measurement["exit"] == 0, measurement
            report["performance"].append({"case": name, "source_utf8_bytes": len(text.encode()),
                                          **measurement})
    except Exception as error:
        report["errors"].append({"phase": "performance", "case": name, "error": str(error)})
report["result"] = "PASS" if not report["errors"] else "FAIL"
print(json.dumps(report, indent=2))
sys.exit(bool(report["errors"]))
