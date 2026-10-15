from __future__ import annotations

import json
import re
from pathlib import Path

import requests
from bs4 import BeautifulSoup

URL = "https://www.india.gov.in/my-government/acts-and-rules?ministry=Ministry%20of%20Education"


def clean_text(value: str) -> str:
    return " ".join(value.split())


def extract_ministry_acts(url: str = URL) -> list[dict]:
    response = requests.get(
        url,
        timeout=30,
        headers={"User-Agent": "Mozilla/5.0"},
    )
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")

    acts: list[dict] = []
    seen: set[str] = set()

    for link in soup.select("a[href*='view-casepdf']"):
        title = clean_text(link.get_text(" ", strip=True))
        if not title:
            continue
        if title in seen:
            continue
        seen.add(title)

        container = link.parent
        summary = ""
        ministry = "Ministry of Education"
        department = ""
        state = ""
        year = ""

        for sibling in link.next_siblings:
            if getattr(sibling, "name", None) == "p":
                paragraph_text = clean_text(sibling.get_text(" ", strip=True))
                if paragraph_text:
                    summary = paragraph_text
                    break

        for tag in container.find_all("p"):
            text = clean_text(tag.get_text(" ", strip=True))
            if not text:
                continue
            lower = text.lower()
            if lower.startswith("year:"):
                year = text.split(":", 1)[1].strip()
            elif lower.startswith("ministry:"):
                ministry = text.split(":", 1)[1].strip()
            elif lower.startswith("department:"):
                department = text.split(":", 1)[1].strip()
            elif lower.startswith("state:"):
                state = text.split(":", 1)[1].strip()

        if not year:
            match = re.search(r"(19|20)\d{2}", title)
            if match:
                year = match.group(0)

        acts.append(
            {
                "id": re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")[:80] or f"act-{len(acts)+1}",
                "title": title,
                "short": title,
                "year": year,
                "citation": title,
                "type": "Central Act",
                "category": "policy",
                "color": "mint-card",
                "icon": "book-open-check",
                "ministry": ministry,
                "department": department,
                "state": state,
                "summary": summary or title,
                "detail": summary or title,
                "sourceUrl": link.get("href") if link.get("href", "").startswith("http") else f"https://www.india.gov.in{link.get('href', '')}",
                "sourceLabel": "Official source",
                "keywords": [title.lower()],
            }
        )

    return acts


if __name__ == "__main__":
    data = extract_ministry_acts()
    output_path = Path(__file__).resolve().parent / "data" / "acts.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)

    print(f"Saved {len(data)} items to {output_path}")
