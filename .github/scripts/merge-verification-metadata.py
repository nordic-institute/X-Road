#!/usr/bin/env python3
"""
merge-verification-metadata.py

Merges multiple Gradle verification-metadata.xml files, preserving all unique <component> and <artifact> hashes.
Usage:
  python3 merge-verification-metadata.py file1.xml file2.xml ... output.xml
"""
import sys
import xml.etree.ElementTree as ET
import re

if len(sys.argv) < 4:
    print("Usage: python3 merge-verification-metadata.py file1.xml file2.xml ... output.xml")
    sys.exit(1)

input_files = sys.argv[1:-1]
output_file = sys.argv[-1]

ROOT_TAG = "verification-metadata"
COMPONENTS_TAG = "components"
NAMESPACE = "https://schema.gradle.org/dependency-verification"

def merge_metadata(files):
    components = dict()
    ns_map = {}
    config_elem = None
    for f in files:
        tree = ET.parse(f)
        root = tree.getroot()
        ns = root.tag[root.tag.find("{")+1:root.tag.find("}")]
        ns_map[f] = ns
        if config_elem is None:
            config_elem = root.find(f".//{{{ns}}}configuration")
        comps_parent = root.find(f".//{{{ns}}}components")
        comps = comps_parent.findall(f".//{{{ns}}}component") if comps_parent is not None else []
        for comp in comps:
            key = (comp.attrib['group'], comp.attrib['name'], comp.attrib['version'])
            if key not in components:
                components[key] = ET.Element(comp.tag, comp.attrib)
            existing_hashes = set()
            for art in components[key].findall(f".//{{{ns}}}artifact"):
                existing_hashes.add((art.attrib['name'], art.attrib.get('sha256'), art.attrib.get('pgp')))
            for art in comp.findall(f".//{{{ns}}}artifact"):
                art_key = (art.attrib['name'], art.attrib.get('sha256'), art.attrib.get('pgp'))
                if art_key not in existing_hashes:
                    components[key].append(art)
                    existing_hashes.add(art_key)
    first_ns = list(ns_map.values())[0] if ns_map else NAMESPACE
    new_root = ET.Element(
        f'{{{first_ns}}}{ROOT_TAG}',
        {
            'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance',
            'xsi:schemaLocation': f'{first_ns} {first_ns}/dependency-verification-1.3.xsd'
        }
    )
    if config_elem is not None:
        new_root.append(config_elem)
    comps_elem = ET.Element(COMPONENTS_TAG)
    for comp in sorted(components.values(), key=lambda c: (c.attrib['group'], c.attrib['name'], c.attrib['version'])):
        comps_elem.append(comp)
    new_root.append(comps_elem)
    return new_root

def indent(elem, level=0, spaces=3):
    i = "\n" + level * (" " * spaces)
    j = "\n" + (level - 1) * (" " * spaces) if level > 0 else "\n"
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = i + (" " * spaces)
        for idx, e in enumerate(elem):
            indent(e, level + 1, spaces)
            if idx < len(elem) - 1:
                if not e.tail or not e.tail.strip():
                    e.tail = i + (" " * spaces)
            else:
                if not e.tail or not e.tail.strip():
                    e.tail = i
        if not elem.tail or not elem.tail.strip():
            elem.tail = j
    else:
        if not elem.text or not elem.text.strip():
            elem.text = ''
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = j

ET.register_namespace('', NAMESPACE)
merged = merge_metadata(input_files)
indent(merged, spaces=3)
ET.ElementTree(merged).write(output_file, encoding='utf-8', xml_declaration=True)
# Post-process to remove space before slash in self-closing tags, matching Gradle's output
with open(output_file, 'r', encoding='utf-8') as f:
    xml_text = f.read()
xml_text = re.sub(r'<(\w+)([^>]*)\s/>', r'<\1\2/>', xml_text)
# Fix XML declaration to use double quotes and match Gradle exactly
xml_text = re.sub(
    r"<\?xml version='1.0' encoding='utf-8'\?>",
    '<?xml version="1.0" encoding="UTF-8"?>',
    xml_text,
    count=1
)
with open(output_file, 'w', encoding='utf-8') as f:
    f.write(xml_text)
print(f"Merged {len(input_files)} files into {output_file}")

