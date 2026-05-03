#!/usr/bin/env python3
"""Generate and normalize a batch of ready hatch-pet row candidates."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


def load_manifest(run_dir: Path) -> dict[str, object]:
    path = run_dir / "imagegen-jobs.json"
    if not path.exists():
        raise SystemExit(f"job manifest not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def job_list(manifest: dict[str, object]) -> list[dict[str, object]]:
    jobs = manifest.get("jobs")
    if not isinstance(jobs, list):
        raise SystemExit("invalid imagegen-jobs.json: jobs must be a list")
    return [job for job in jobs if isinstance(job, dict)]


def completed_job_ids(manifest: dict[str, object]) -> set[str]:
    return {
        str(job["id"])
        for job in job_list(manifest)
        if job.get("status") == "complete" and isinstance(job.get("id"), str)
    }


def ready_jobs(manifest: dict[str, object]) -> list[dict[str, object]]:
    completed = completed_job_ids(manifest)
    ready: list[dict[str, object]] = []
    for job in job_list(manifest):
        if job.get("status", "pending") == "complete":
            continue
        deps = job.get("depends_on", [])
        if isinstance(deps, list) and all(isinstance(dep, str) and dep in completed for dep in deps):
            ready.append(job)
    return ready


STRICT_PREFIX = """This is a strict sprite-strip generation request for hatch-pet.

Critical output corrections:
- The entire final canvas must be a perfectly flat pure magenta #FF00FF background edge to edge.
- Do not output white, off-white, gray, or checkerboard background anywhere.
- Do not reproduce layout guide pixels, white guide backing, boxes, dividers, borders, labels, or slot lines.
- Use the layout guide only mentally for spacing, then discard it completely.
- Keep one complete pose per slot with no cropping or slot crossing.

"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--model", default="gpt-image-2")
    parser.add_argument("--size", default="1536x1024")
    parser.add_argument("--job-id", action="append", default=[])
    parser.add_argument("--threshold", type=float, default=36.0)
    parser.add_argument(
        "--strict-prompts",
        action="store_true",
        help="Prepend stricter background and anti-guide instructions to each prompt.",
    )
    args = parser.parse_args()

    run_dir = Path(args.run_dir).expanduser().resolve()
    manifest = load_manifest(run_dir)
    selected_ids = set(args.job_id)
    jobs = []
    for job in ready_jobs(manifest):
        job_id = str(job.get("id"))
        if selected_ids and job_id not in selected_ids:
            continue
        if job.get("kind") != "row-strip":
            continue
        jobs.append(job)
    if not jobs:
        raise SystemExit("no matching ready row-strip jobs found")

    generator = run_dir / "generate_image_candidate_compatible.py"
    normalizer = run_dir / "normalize_border_background.py"
    strict_prompt_dir = run_dir / "prompts" / "strict-batch"
    strict_prompt_dir.mkdir(parents=True, exist_ok=True)
    for job in jobs:
        job_id = str(job.get("id"))
        prompt_file = run_dir / str(job.get("prompt_file"))
        effective_prompt = prompt_file
        if args.strict_prompts:
            strict_prompt = strict_prompt_dir / f"{job_id}.md"
            strict_prompt.write_text(
                STRICT_PREFIX + prompt_file.read_text(encoding="utf-8"),
                encoding="utf-8",
            )
            effective_prompt = strict_prompt
        output_image = run_dir / "candidates" / f"{job_id}.png"
        output_json = run_dir / "candidates" / "raw" / f"{job_id}.response.json"
        command = [
            sys.executable,
            str(generator),
            "--prompt-file",
            str(effective_prompt),
            "--output-image",
            str(output_image),
            "--output-json",
            str(output_json),
            "--model",
            args.model,
            "--size",
            args.size,
            "--base-url",
            args.base_url,
        ]
        inputs = job.get("input_images")
        if isinstance(inputs, list):
            for item in inputs:
                if isinstance(item, dict) and isinstance(item.get("path"), str):
                    command.extend(["--input-image", str(run_dir / item["path"])])
        print(f"Generating {job_id}...")
        subprocess.run(command, check=True)
        subprocess.run(
            [
                sys.executable,
                str(normalizer),
                "--input",
                str(output_image),
                "--target",
                "#FF00FF",
                "--threshold",
                str(args.threshold),
            ],
            check=True,
        )
    print(json.dumps({"ok": True, "generated_jobs": [str(job.get("id")) for job in jobs]}, indent=2))


if __name__ == "__main__":
    main()
