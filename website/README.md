# MANHNT marketing site

Static landing page for [MANHNT](https://github.com/dedsec-1337/MANHNT).

**Live:** https://dedsec-1337.github.io/MANHNT/

## Local preview

```bash
cd website
python3 -m http.server 8080
# open http://localhost:8080
```

## Assets

| File | Use |
|------|-----|
| `index.html` | Full page |
| `og-image.png` | Open Graph / Twitter (1200×630) |
| `apple-touch-icon.png` | Home-screen icon |
| `logo-mark.svg` | Square M mark |
| `logo.svg` | Full wordmark |

Deployed by `.github/workflows/deploy-pages.yml` from this folder on every push to `main`.
