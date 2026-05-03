#!/usr/bin/env python3
"""Record a generated compatible fallback image for one hatch-pet job."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

CANONICAL_BASE_PATH = "references/canonical-base.png"


def load_manifest(path: Path) -> dict[str, object]:
    if not path.exists():
        raise SystemExit(f"job manifest not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def job_list(manifest: dict[str, object]) -> list[dict[str, object]]:
    jobs = manifest.get("jobs")
    if not isinstance(jobs, list):
        raise SystemExit("invalid imagegen-jobs.json: jobs must be a list")
    return [job for job in jobs if isinstance(job, dict)]


def find_job(manifest: dict[str, object], job_id: str) -> dict[str, object]:
    for job in job_list(manifest):
        if job.get("id") == job_id:
            return job
    raise SystemExit(f"unknown job id: {job_id}")


def completed_job_ids(manifest: dict[str, object]) -> set[str]:
    return {
        str(job["id"])
        for job in job_list(manifest)
        if job.get("status") == "complete" and isinstance(job.get("id"), str)
    }


def image_metadata(path: Path) -> dict[str, object]:
    with Image.open(path) as image:
        image.verify()
    with Image.open(path) as image:
        return {
            "width": image.width,
            "height": image.height,
            "mode": image.mode,
            "format": image.format,
        }


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def manifest_relative(path: Path, run_dir: Path) -> str:
    return str(path.resolve().relative_to(run_dir.resolve()))


def update_base_canonical_reference(
    *,
    run_dir: Path,
    output: Path,
    manifest: dict[str, object],
    job: dict[str, object],
) -> None:
    if job.get("id") != "base":
        return

    canonical = run_dir / CANONICAL_BASE_PATH
    canonical.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(output, canonical)
    reference = {
        "path": manifest_relative(canonical, run_dir),
        "source_job": "base",
        "sha256": file_sha256(canonical),
    }
    manifest["canonical_identity_reference"] = reference
    job["canonical_reference_path"] = reference["path"]

    request_path = run_dir / "pet_request.json"
    if request_path.exists():
        request = json.loads(request_path.read_text(encoding="utf-8"))
        request["canonical_identity_reference"] = reference
        request_path.write_text(json.dumps(request, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--job-id", required=True)
    parser.add_argument("--source-image", required=True)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    run_dir = Path(args.run_dir).expanduser().resolve()
    source_image = Path(args.source_image).expanduser().resolve()
    if not source_image.is_file():
        raise SystemExit(f"source image not found: {source_image}")

    manifest_path = run_dir / "imagegen-jobs.json"
    manifest = load_manifest(manifest_path)
    job = find_job(manifest, args.job_id)

    missing_deps = [
        dep
        for dep in job.get("depends_on", [])
        if isinstance(dep, str) and dep not in completed_job_ids(manifest)
    ]
    if missing_deps:
        raise SystemExit(
            f"job {args.job_id} is not ready; missing dependency result(s): {', '.join(missing_deps)}"
        )

    output_raw = job.get("output_path")
    if not isinstance(output_raw, str):
        raise SystemExit(f"job {args.job_id} has no output_path")
    output = run_dir / output_raw
    if output.exists() and not args.force:
        raise SystemExit(f"{output} already exists; pass --force to replace it")

    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_image, output)
    metadata = image_metadata(output)

    job["status"] = "complete"
    job["source_path"] = str(source_image)
    job["source_provenance"] = "secondary-fallback-image-api"
    job["source_sha256"] = file_sha256(source_image)
    job["output_sha256"] = file_sha256(output)
    job["completed_at"] = datetime.now(timezone.utc).isoformat()
    job["metadata"] = metadata
    job["secondary_fallback"] = True
    job["secondary_fallback_base_url"] = args.base_url
    for key in [
        "last_error",
        "synthetic_test_source",
        "derived_from",
        "mirror_decision",
        "repair_reason",
        "queued_at",
    ]:
        job.pop(key, None)

    update_base_canonical_reference(
        run_dir=run_dir,
        output=output,
        manifest=manifest,
        job=job,
    )
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    print(
        json.dumps(
            {
                "ok": True,
                "job_id": args.job_id,
                "output": str(output),
                "source_image": str(source_image),
                "metadata": metadata,
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
