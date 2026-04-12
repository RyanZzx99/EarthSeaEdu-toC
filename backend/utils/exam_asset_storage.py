from __future__ import annotations

import os
from pathlib import Path


EXAM_ASSET_URL_PREFIX = "/exam-assets"


def get_project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def get_exam_asset_root() -> Path:
    configured_root = os.getenv("EXAM_ASSET_ROOT", "").strip()
    if configured_root:
        return Path(configured_root).expanduser().resolve()
    return get_project_root() / "storage" / "exam-assets"


def ensure_exam_asset_root() -> Path:
    exam_asset_root = get_exam_asset_root()
    exam_asset_root.mkdir(parents=True, exist_ok=True)
    return exam_asset_root


def build_exam_asset_url(storage_path: str | None) -> str:
    normalized_storage_path = normalize_storage_path(storage_path)
    if not normalized_storage_path:
        return ""
    if normalized_storage_path == "exam-assets" or normalized_storage_path.startswith("exam-assets/"):
        return f"/{normalized_storage_path}"
    return f"{EXAM_ASSET_URL_PREFIX}/{normalized_storage_path}"


def resolve_exam_asset_abspath(storage_path: str | None) -> Path:
    normalized_storage_path = normalize_storage_path(storage_path)
    if normalized_storage_path == "exam-assets":
        normalized_storage_path = ""
    elif normalized_storage_path.startswith("exam-assets/"):
        normalized_storage_path = normalized_storage_path[len("exam-assets/") :]
    return ensure_exam_asset_root() / Path(normalized_storage_path.replace("/", os.sep))


def normalize_storage_path(storage_path: str | None) -> str:
    raw_value = str(storage_path or "").replace("\\", "/").strip().strip("/")
    parts: list[str] = []
    for part in raw_value.split("/"):
        segment = part.strip()
        if not segment or segment == ".":
            continue
        if segment == "..":
            if parts:
                parts.pop()
            continue
        parts.append(segment)
    return "/".join(parts)
