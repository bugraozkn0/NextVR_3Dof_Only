"""Summarize executed BE JUnit XML, not inferred coverage or mocked integration."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET
files=list(Path("app/build/test-results/testBeDebugUnitTest").glob("TEST-*.xml"))
if not files:
    raise SystemExit("No executed BE unit-test XML found")
suites=[ET.parse(p).getroot() for p in files]
summary={k:sum(int(s.get(k,"0")) for s in suites) for k in ["tests","failures","errors","skipped"]}
summary["cinema"]=[{"suite":s.get("name"),"tests":int(s.get("tests","0")),"failures":int(s.get("failures","0"))} for s in suites if "Cinema" in s.get("name","")]
print("CINEMA_JUNIT_SUMMARY="+json.dumps(summary,sort_keys=True))
if summary["failures"] or summary["errors"]:
    raise SystemExit("BE unit tests failed")
