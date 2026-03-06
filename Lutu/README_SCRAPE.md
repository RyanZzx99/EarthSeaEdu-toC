This project includes a script to build `data/schools.json` from the QS data CSV/XLSX and download logos.

Run locally (Windows PowerShell):

1. Create/activate a Python environment (optional but recommended):

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

2. Install dependencies:

```powershell
pip install -r requirements.txt
```

3. Run the builder script:

```powershell
python .\scripts\build_schools.py
```

What it does:
- Reads `data/2025_QS_World_University_Rankings_2.1_(For_qs.com).xlsx` (preferred) or CSV.
- Attempts to map columns automatically (university name, country, QS rank, logo, etc.).
- For each school, tries to download `logo` if a URL is available. If not, attempts to fetch the university's Wikipedia page and extract the infobox/og:image.
- Writes the combined normalized output to `data/schools.json` and saves images into `logos/`.

Notes:
- This is a best-effort script. Network errors, missing pages, or anti-scraping measures may cause some logos to be missing.
- You can edit `scripts/build_schools.py` to tune the column mappings if your CSV/XLSX uses different headers.
