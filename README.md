# SATTVA education law dashboard

This project includes:

- a frontend landing page in `index.html`
- a Python Flask backend in `backend/app.py`
- an optional scraper in `backend/scraper.py` for the Ministry of Education acts page

## Run the backend

```bash
cd backend
pip install -r requirements.txt
python app.py
```

Then open the frontend in a browser. It will call the live backend at:

- http://127.0.0.1:5000/health
- http://127.0.0.1:5000/api/acts

## Import your dataset

You can send your dataset to the backend through the API:

```bash
curl -X POST http://127.0.0.1:5000/api/acts/bulk \
  -H "Content-Type: application/json" \
  -d '{"acts":[{"title":"Sample Act","summary":"...","category":"foundational","ministry":"Ministry of Education"}]}'
```

## Use the scraper

```bash
cd backend
python scraper.py
```

This fetches the Ministry of Education acts-and-rules page and saves extracted records to `backend/data/acts.json`.
