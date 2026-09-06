# Playwright MCP Server

## Beschreibung

Der Playwright MCP Server gibt der AI einen kontrollierbaren Browser zum Ausführen von Web-Automationen. Im Gegensatz zu Apify (Web-Scraping) kann Playwright aktive Interaktionen durchführen: Klicken, Tippen, Scrollen, Hochladen, Herunterladen.

**Funktionen:**
- Browser-Automation (Chromium, Firefox, WebKit)
- Interaktionen: Klicken, Tippen, Scrollen
- Screenshot-Erstellung
- Dateidownloads
- Multi-Tab-Support
- Warten auf Elemente/Events

**Vorteile:**
- Kostenlos
- Läuft lokal und in CI
- Von Microsoft entwickelt
- Headless oder mit UI

## Docker Setup

### Dockerfile

```dockerfile
FROM mcr.microsoft.com/playwright:v1.42.0-noble

WORKDIR /app

# Installiere Playwright MCP Server
RUN npm install -g @playwright/mcp

EXPOSE 3002

# Playwright Browser sind im Base-Image enthalten
ENTRYPOINT ["npx", "@playwright/mcp"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  playwright-mcp:
    build: .
    container_name: playwright-mcp
    restart: unless-stopped
    environment:
      - NODE_ENV=production
      # Headless-Modus für Container
      - PLAYWRIGHT_HEADLESS=true
      # Optional: UI-Modus für Debugging
      # - PLAYWRIGHT_HEADLESS=false
      # - DISPLAY=:99
    ports:
      - "${MCP_PORT:-3002}:3002"
    networks:
      - mcp-network
    # Optional: Temporäre Daten für Downloads
    volumes:
      - playwright-downloads:/downloads
    healthcheck:
      test: ["CMD", "pgrep", "-f", "playwright"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  playwright-downloads:

networks:
  mcp-network:
    driver: bridge
```

### .env Datei

```bash
# Port für MCP Server
MCP_PORT=3002
```

## Nutzung

### Server starten

```bash
# Build und Start
docker-compose up -d --build
```

### Mit MCP Client verbinden

Die Server-URL für die MCP-Konfiguration:
```
http://localhost:3002/sse
```

### Beispiel-Prompts

- "Logge dich ins Dashboard ein und lade den täglichen Report herunter"
- "Mache einen Screenshot der Startseite"
- "Fülle das Kontaktformular aus und sende es"
- "Teste den Checkout-Prozess mit Testdaten"
- "Extrahiere alle Produktpreise von der Katalogseite"

## Sicherheitshinweise

- Keine sensiblen Credentials im Container speichern
- Für Login-Tests Environment-Variablen verwenden
- Downloads in isolierten Volumes speichern
- Headless-Modus für Produktivsysteme empfohlen

## Browser-Support

Das offizielle Playwright-Image enthält alle Browser:
- Chromium (Chrome/Edge)
- Firefox
- WebKit (Safari)
