#!/usr/bin/env python3
"""Birim test sonuçlarını (JUnit XML) GitHub iş özeti için Markdown olarak yazar. Her zaman 0 döner."""
import glob
import sys
import xml.etree.ElementTree as ET

directory = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest"
files = sorted(glob.glob(f"{directory}/*.xml"))
if not files:
    print("### 🧪 Birim testleri\n\nTest sonuç dosyası yok (test kodu derlenemedi olabilir; günlükteki `e:` satırlarına bakın).\n")
    sys.exit(0)

total = failed = skipped = 0
lines = []
for path in files:
    for case in ET.parse(path).getroot().iter("testcase"):
        total += 1
        if case.find("skipped") is not None:
            skipped += 1
        for tag in ("failure", "error"):
            for problem in case.findall(tag):
                failed += 1
                message = (problem.get("message") or "").replace("\n", " ")[:200]
                lines.append(f"- `{case.get('classname')}.{case.get('name')}`: {message}")

icon = "✅" if failed == 0 else "❌"
print(f"### {icon} Birim testleri — toplam {total}, başarısız {failed}, atlanan {skipped}\n")
print("\n".join(lines) if lines else "Tüm testler geçti.")
print()
