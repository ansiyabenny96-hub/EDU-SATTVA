# SATTVA education law dashboard

SATTVA includes a single-page frontend and a Java Spring Boot backend. The backend serves the Indian education law dataset from `backend/data/acts.json`.

## Requirements

- JDK 17 or newer, with `JAVA_HOME` set to the JDK installation

## Run the backend

From the project root, use the Maven Wrapper:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:5000`. Open `index.html` in a browser; the frontend calls:

- `GET http://127.0.0.1:5000/health`
- `GET http://127.0.0.1:5000/api/acts`

## API

- `GET /api/v1/acts` returns all acts in the structured dataset.
- `GET /api/v1/acts/{id}` returns one act by ID, or `404` if it is not found.
- `GET /api/v1/acts/category/{category}` filters by its category.
- `GET /api/v1/acts/search?query=<text>` searches act names, categories, purpose, level, jurisdiction, and related terms.
- `GET /api/acts?q=<text>&ministry=<text>&category=<category>` retains the dashboard-compatible response format.
- `GET /api/acts/summary` returns the dataset count and available ministries and categories.
- `POST /api/acts/bulk` replaces the dataset. Send JSON with an `acts` array; records are saved to `data/acts.json` in the structured act schema.

Each dataset record contains `id`, `category`, `level`, `actName`, `year`, `purpose`, and `jurisdiction`. The bundled dataset is a practical reference index; consult current official legal texts for authoritative wording and amendments.

Example:

```bash
curl.exe -X POST http://127.0.0.1:5000/api/acts/bulk -H "Content-Type: application/json" -d "{\"acts\":[{\"title\":\"Sample Act\",\"summary\":\"...\",\"category\":\"foundational\",\"ministry\":\"Ministry of Education\"}]}"
```

## Refresh the dataset from the official catalog

The optional Java scraper reads the Ministry of Education catalog and replaces the local dataset:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--sattva.scrape-on-start=true"
```

Scraping requires an internet connection. The regular backend startup does not scrape.

## Run backend tests

```powershell
cd backend
.\mvnw.cmd test
```
