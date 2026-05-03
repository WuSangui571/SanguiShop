#!/usr/bin/env python3
"""Normalize a border-connected flat background to a target chroma key color."""

from __future__ import annotations

import argparse
import math
from collections import deque
from pathlib import Path

from PIL import Image


def parse_hex_color(value: str) -> tuple[int, int, int]:
    value = value.strip()
    if len(value) != 7 or not value.startswith("#"):
        raise SystemExit(f"invalid color {value}; expected #RRGGBB")
    try:
        return tuple(int(value[index : index + 2], 16) for index in (1, 3, 5))
    except ValueError as exc:
        raise SystemExit(f"invalid color {value}; expected #RRGGBB") from exc


def distance(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    return math.sqrt(sum((left - right) ** 2 for left, right in zip(a, b)))


def collect_border_seed(
    image: Image.Image,
    threshold: float,
) -> tuple[tuple[int, int, int], set[tuple[int, int]]]:
    rgb = image.convert("RGB")
    width, height = rgb.size
    pixels = rgb.load()
    samples: list[tuple[int, int, int]] = []
    border_points: list[tuple[int, int]] = []

    for x in range(width):
        border_points.append((x, 0))
        border_points.append((x, height - 1))
    for y in range(height):
        border_points.append((0, y))
        border_points.append((width - 1, y))

    for x, y in border_points:
        samples.append(pixels[x, y])

    reference = tuple(
        round(sum(channel_values) / len(samples))
        for channel_values in zip(*samples)
    )
    eligible = {
        (x, y)
        for x, y in border_points
        if distance(pixels[x, y], reference) <= threshold
    }
    if not eligible:
        raise SystemExit("could not identify a consistent border background to normalize")
    return reference, eligible


def normalize(
    *,
    image: Image.Image,
    target: tuple[int, int, int],
    threshold: float,
) -> Image.Image:
    rgb = image.convert("RGB")
    width, height = rgb.size
    pixels = rgb.load()
    reference, queue_seed = collect_border_seed(rgb, threshold)

    queue: deque[tuple[int, int]] = deque(queue_seed)
    visited = set(queue_seed)

    while queue:
        x, y = queue.popleft()
        pixels[x, y] = target
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if nx < 0 or ny < 0 or nx >= width or ny >= height:
                continue
            if (nx, ny) in visited:
                continue
            if distance(pixels[nx, ny], reference) <= threshold:
                visited.add((nx, ny))
                queue.append((nx, ny))
    return rgb


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", default="")
    parser.add_argument("--target", default="#FF00FF")
    parser.add_argument("--threshold", type=float, default=36.0)
    args = parser.parse_args()

    input_path = Path(args.input).expanduser().resolve()
    output_path = (
        Path(args.output).expanduser().resolve() if args.output else input_path
    )
    target = parse_hex_color(args.target)

    with Image.open(input_path) as image:
        normalized = normalize(image=image, target=target, threshold=args.threshold)
        normalized.save(output_path)
    print(output_path)


if __name__ == "__main__":
    main()
