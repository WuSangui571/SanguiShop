#!/usr/bin/env python3
"""Generate one compatible image candidate without mutating hatch-pet manifests."""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
from pathlib import Path


def normalize_base_url(raw: str) -> str:
    value = raw.strip()
    if not value:
        raise SystemExit("image API base URL is empty")
    return value.rstrip("/")


def api_url(base_url: str, path: str) -> str:
    return f"{normalize_base_url(base_url)}{path}"


def read_prompt(prompt_file: Path) -> str:
    prompt = prompt_file.read_text(encoding="utf-8").strip()
    if not prompt:
        raise SystemExit(f"prompt file is empty: {prompt_file}")
    return prompt


def run_image_edit(
    *,
    base_url: str,
    model: str,
    prompt: str,
    image_paths: list[Path],
    output_json: Path,
    size: str,
    api_key: str,
) -> dict[str, object]:
    output_json.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "curl",
        "-sS",
        "-X",
        "POST",
        api_url(base_url, "/images/edits"),
        "-H",
        f"Authorization: Bearer {api_key}",
        "-F",
        f"model={model}",
    ]
    for image_path in image_paths:
        command.extend(["-F", f"image[]=@{image_path}"])
    command.extend(
        [
            "-F",
            f"prompt={prompt}",
            "-F",
            f"size={size}",
            "-F",
            "output_format=png",
            "-o",
            str(output_json),
        ]
    )
    subprocess.run(command, check=True)
    response = json.loads(output_json.read_text(encoding="utf-8"))
    if response.get("error"):
        raise SystemExit(json.dumps(response["error"], indent=2))
    return response


def run_image_generation(
    *,
    base_url: str,
    model: str,
    prompt: str,
    output_json: Path,
    size: str,
    api_key: str,
) -> dict[str, object]:
    output_json.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(
        {
            "model": model,
            "prompt": prompt,
            "size": size,
            "output_format": "png",
        }
    )
    command = [
        "curl",
        "-sS",
        "-X",
        "POST",
        api_url(base_url, "/images/generations"),
        "-H",
        f"Authorization: Bearer {api_key}",
        "-H",
        "Content-Type: application/json",
        "--data-binary",
        payload,
        "-o",
        str(output_json),
    ]
    subprocess.run(command, check=True)
    response = json.loads(output_json.read_text(encoding="utf-8"))
    if response.get("error"):
        raise SystemExit(json.dumps(response["error"], indent=2))
    return response


def decode_response(response: dict[str, object], output_image: Path) -> None:
    data = response.get("data")
    if not isinstance(data, list) or not data:
        raise SystemExit("image API response did not contain data[0]")
    first = data[0]
    if not isinstance(first, dict) or not isinstance(first.get("b64_json"), str):
        raise SystemExit("image API response did not contain data[0].b64_json")
    output_image.parent.mkdir(parents=True, exist_ok=True)
    output_image.write_bytes(base64.b64decode(first["b64_json"]))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prompt-file", required=True)
    parser.add_argument("--output-image", required=True)
    parser.add_argument("--output-json", default="")
    parser.add_argument("--input-image", action="append", default=[])
    parser.add_argument("--model", default="gpt-image-2")
    parser.add_argument("--size", default="1024x1024")
    parser.add_argument(
        "--base-url",
        default=os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1"),
    )
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        raise SystemExit("OPENAI_API_KEY is not set")

    prompt_file = Path(args.prompt_file).expanduser().resolve()
    output_image = Path(args.output_image).expanduser().resolve()
    output_json = (
        Path(args.output_json).expanduser().resolve()
        if args.output_json
        else output_image.with_suffix(".response.json")
    )
    input_images = [Path(value).expanduser().resolve() for value in args.input_image]
    for path in input_images:
        if not path.is_file():
            raise SystemExit(f"input image not found: {path}")

    prompt = read_prompt(prompt_file)
    if input_images:
        response = run_image_edit(
            base_url=args.base_url,
            model=args.model,
            prompt=prompt,
            image_paths=input_images,
            output_json=output_json,
            size=args.size,
            api_key=api_key,
        )
    else:
        response = run_image_generation(
            base_url=args.base_url,
            model=args.model,
            prompt=prompt,
            output_json=output_json,
            size=args.size,
            api_key=api_key,
        )
    decode_response(response, output_image)
    print(
        json.dumps(
            {
                "ok": True,
                "output_image": str(output_image),
                "response_json": str(output_json),
                "base_url": normalize_base_url(args.base_url),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
