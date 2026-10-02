#!/usr/bin/env python3
"""Guard-rail: every chart-declared wait-gate seed key must be seeded somewhere
in each ansible values override.

A Helm values override list REPLACES the chart's default list of the same key
(Helm does not merge list elements) — so when the chart's default values.yaml
adds a new `services.*.waits[]` entry of type `serverconfProperty`, every
ansible override that sets its own `configSeed.properties` list must carry
that key too, or the gated service deadlocks waiting for a config row the
seed Job never writes. For example: if the chart default gains a new wait
on `xroad.proxy.server.listen-port` but an ansible override's own
`configSeed.properties` list is never updated to seed it, that override's
proxy pod hangs forever at startup.

This is a textual, not a semantic, check: it looks for a literal
`- key: <name>` line anywhere in the override's raw Jinja2 source, regardless
of which `{% if %}` branch it sits in. An override that only seeds a required
key conditionally (while the service it gates is instantiated unconditionally)
will pass this check and still deadlock in that branch; catching that needs a
render-aware check per environment, which this script intentionally does not
attempt.
"""
import argparse
import re
import sys

import yaml

SEED_KEY_LINE = re.compile(r"^\s*-\s*key:\s*(\S+)\s*$", re.MULTILINE)


def required_keys(chart_values_path):
    with open(chart_values_path, encoding="utf-8") as f:
        chart = yaml.safe_load(f) or {}
    keys = []
    for service, cfg in (chart.get("services") or {}).items():
        for wait in (cfg or {}).get("waits", []) or []:
            if wait.get("type") == "serverconfProperty" and wait.get("key"):
                keys.append((service, wait["key"]))
    return keys


def seeded_keys(override_template_path):
    with open(override_template_path, encoding="utf-8") as f:
        text = f.read()
    return set(SEED_KEY_LINE.findall(text))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chart", required=True, help="path to the chart's default values.yaml")
    parser.add_argument(
        "--override",
        required=True,
        action="append",
        help="path to an ansible values override template (.j2); repeatable",
    )
    args = parser.parse_args()

    required = required_keys(args.chart)
    if not required:
        print(f"no serverconfProperty waits declared in {args.chart} — nothing to check")
        return 0

    status = 0
    for override_path in args.override:
        present = seeded_keys(override_path)
        missing = [(svc, key) for svc, key in required if key not in present]
        if missing:
            status = 1
            print(f"FAIL  {override_path}")
            for svc, key in missing:
                print(f"      missing seed key '{key}' required by services.{svc}.waits[]")
        else:
            print(f"OK    {override_path}  ({len(required)} wait-gated key(s) all present)")

    return status


if __name__ == "__main__":
    sys.exit(main())
