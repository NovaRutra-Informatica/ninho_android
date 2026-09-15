import argparse
import datetime
import json
from pathlib import Path
import sys
import time
import urllib.request


CONTROL_QUERY = {
    "package": {"ecosystem": "Maven", "name": "org.apache.commons:commons-compress"},
    "version": "1.20",
}
CONTROL_ADVISORY = "GHSA-7hfm-57qf-j43q"


def request(endpoint, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        "https://api.osv.dev/v1/" + endpoint,
        data=data,
        headers={"Content-Type": "application/json", "User-Agent": "Ninho-dependency-audit"},
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=45) as response:
                return json.load(response)
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)


def query_packages(packages, transport=request):
    queries = [
        {"package": {"ecosystem": "Maven", "name": item["name"]}, "version": item["version"]}
        for item in packages
    ] + [CONTROL_QUERY]
    results = []
    for offset in range(0, len(queries), 100):
        batch = queries[offset:offset + 100]
        partial = transport("querybatch", {"queries": batch})["results"]
        if len(partial) != len(batch):
            raise RuntimeError("OSV returned an incomplete batch")
        for query, value in zip(batch, partial):
            seen_tokens = set()
            while value.get("next_page_token"):
                token = value.pop("next_page_token")
                if token in seen_tokens:
                    raise RuntimeError("OSV repeated a pagination token")
                seen_tokens.add(token)
                page = transport("querybatch", {"queries": [{**query, "page_token": token}]})["results"]
                if len(page) != 1:
                    raise RuntimeError("OSV returned an incomplete page")
                value.setdefault("vulns", []).extend(page[0].get("vulns", []))
                if page[0].get("next_page_token"):
                    value["next_page_token"] = page[0]["next_page_token"]
        results.extend(partial)
    control = {item["id"] for item in results.pop().get("vulns", [])}
    if CONTROL_ADVISORY not in control:
        raise RuntimeError("OSV positive control failed; results cannot be accepted")
    findings = [
        {**package, "vulnerabilities": result["vulns"]}
        for package, result in zip(packages, results) if result.get("vulns")
    ]
    return findings, sorted(control)


def audit(inventory, transport=request):
    if inventory["errors"] or not inventory["packages"] or not inventory["scopes"]:
        raise RuntimeError("Dependency inventory is empty or has resolution errors")
    findings, control = query_packages(inventory["packages"], transport)
    gradle_version = tuple(int(part) for part in inventory["gradle"].split("."))
    gradle_affected = gradle_version < (8, 14, 4) or (9, 0, 0) <= gradle_version < (9, 3, 0)
    if gradle_affected:
        findings.append({
            "name": "org.gradle:gradle-core", "version": inventory["gradle"], "scopes": ["build-tool"],
            "vulnerabilities": [{"id": "GHSA-mqwm-5m85-gmcv"}, {"id": "GHSA-w78c-w6vf-rw82"}],
        })
    return {
        "checkedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "source": "https://api.osv.dev/v1/querybatch",
        "packagesChecked": len(inventory["packages"]),
        "configurations": inventory["scopes"],
        "gradle": inventory["gradle"],
        "gradleAdvisorySources": [
            "https://github.com/gradle/gradle/security/advisories/GHSA-mqwm-5m85-gmcv",
            "https://github.com/gradle/gradle/security/advisories/GHSA-w78c-w6vf-rw82",
        ],
        "positiveControl": control,
        "findings": findings,
    }


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", type=Path, default=root / "build/security/dependencies.json")
    parser.add_argument("--output", type=Path, default=root / "build/security/osv-report.json")
    args = parser.parse_args()
    report = audit(json.loads(args.inventory.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"packagesChecked": report["packagesChecked"], "vulnerablePackages": len(report["findings"]), "report": str(args.output)}))
    return 1 if report["findings"] else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Audit failed: {error}", file=sys.stderr)
        sys.exit(2)
