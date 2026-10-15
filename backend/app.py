from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

BASE_DIR = Path(__file__).resolve().parent
DATA_FILE = BASE_DIR / "data" / "acts.json"


def load_acts() -> list[dict[str, Any]]:
    if not DATA_FILE.exists():
        return []
    with DATA_FILE.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    if isinstance(data, list):
        return data
    return []


def save_acts(acts: list[dict[str, Any]]) -> None:
    DATA_FILE.parent.mkdir(parents=True, exist_ok=True)
    with DATA_FILE.open("w", encoding="utf-8") as fh:
        json.dump(acts, fh, ensure_ascii=False, indent=2)


@app.get("/health")
def health_check():
    return jsonify({"status": "ok", "message": "Education law backend is running."})


@app.get("/api/acts")
def list_acts():
    acts = load_acts()
    query = (request.args.get("q") or "").strip().lower()
    ministry = (request.args.get("ministry") or "").strip().lower()
    category = (request.args.get("category") or "").strip().lower()

    if query:
        acts = [
            act
            for act in acts
            if query in (act.get("title") or "").lower()
            or query in (act.get("summary") or "").lower()
            or query in (act.get("citation") or "").lower()
            or any(query in (keyword or "").lower() for keyword in act.get("keywords", []))
        ]

    if ministry:
        acts = [
            act for act in acts if ministry in (act.get("ministry") or "").lower()
        ]

    if category:
        acts = [
            act for act in acts if category == (act.get("category") or "").lower()
        ]

    return jsonify({"count": len(acts), "items": acts})


@app.get("/api/acts/summary")
def acts_summary():
    acts = load_acts()
    ministries = sorted({act.get("ministry", "Unknown") for act in acts if act.get("ministry")})
    categories = sorted({act.get("category", "unknown") for act in acts if act.get("category")})
    return jsonify({"count": len(acts), "ministries": ministries, "categories": categories})


@app.post("/api/acts/bulk")
def bulk_import():
    payload = request.get_json(silent=True) or {}
    acts = payload.get("acts")
    if not isinstance(acts, list):
        return jsonify({"error": "Expected a JSON body with an 'acts' list."}), 400

    cleaned = []
    for act in acts:
        if not isinstance(act, dict):
            continue
        cleaned.append({
            "id": act.get("id") or act.get("title") or str(len(cleaned)),
            "title": act.get("title") or "Untitled act",
            "short": act.get("short") or act.get("title") or "Untitled act",
            "year": act.get("year") or "",
            "citation": act.get("citation") or "",
            "type": act.get("type") or "General",
            "category": act.get("category") or "policy",
            "color": act.get("color") or "mint-card",
            "icon": act.get("icon") or "book-open-check",
            "ministry": act.get("ministry") or "Unknown ministry",
            "summary": act.get("summary") or "",
            "detail": act.get("detail") or act.get("summary") or "",
            "sourceUrl": act.get("sourceUrl") or "",
            "sourceLabel": act.get("sourceLabel") or "Official source",
            "keywords": act.get("keywords") or [act.get("title") or ""],
        })

    save_acts(cleaned)
    return jsonify({"message": "Dataset imported successfully.", "count": len(cleaned)}), 201


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
