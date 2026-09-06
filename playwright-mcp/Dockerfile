FROM mcr.microsoft.com/playwright:latest

WORKDIR /app

# Installiere Playwright MCP Server und den passenden Chromium-Browser
RUN npm install -g @playwright/mcp \
    && npx @playwright/mcp install-browser chromium

EXPOSE 3002

# Playwright Browser sind im Base-Image enthalten.
# --port startet den Server im SSE-Transport-Modus.
# Ohne --headless: Browser-Fenster wird auf dem Host-Display (X11) angezeigt.
ENTRYPOINT ["npx", "@playwright/mcp", "--port", "3002", "--host", "0.0.0.0", "--browser", "chromium", "--allowed-hosts", "*"]