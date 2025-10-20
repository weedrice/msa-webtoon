#!/usr/bin/env python3
import xml.etree.ElementTree as ET
import glob
import os

# Change to repo root based on this script location
ROOT = os.path.dirname(os.path.abspath(__file__))
os.chdir(ROOT)

services = glob.glob('services/*/build/reports/jacoco/test/jacocoTestReport.xml')
print('테스트 커버리지 요약:')
print('=' * 80)
for service_xml in sorted(services):
    # services\api-gateway\build\... -> api-gateway
    parts = service_xml.replace('\\', '/').split('/')
    service_name = parts[1] if len(parts) > 1 else 'unknown'
    tree = ET.parse(service_xml)
    root = tree.getroot()

    # 루트 레벨의 INSTRUCTION counter 찾기
    for counter in root.findall('counter'):
        if counter.get('type') == 'INSTRUCTION':
            missed = int(counter.get('missed'))
            covered = int(counter.get('covered'))
            total = missed + covered
            if total > 0:
                coverage_pct = (covered / total) * 100
            else:
                coverage_pct = 0
            print(f'{service_name:25s}: {coverage_pct:5.1f}% ({covered:5d}/{total:5d} instructions)')
            break
