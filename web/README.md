# Winterhold web

A página em `http://IP_DO_CELULAR:8790/` é servida **pelo APK**. O celular recebe MQTT, avalia alertas, persiste medições e mostra sua própria saúde. Para entrar, copie em **Ajustes → Web no celular** o endereço e a chave web. A chave fica na sessão do navegador, enviada no cabeçalho `X-Winterhold-Key` à API. A chave MQTT, credenciais SMTP e token da ponte WhatsApp são exibidos apenas nos ajustes nativos.

A aba **Ambientes** cadastra um `DEVICE_ID` por ESP32, edita nome, ícone e limites independentes e fixa favoritos no Início. Em **Alertas → Destinatários**, configure os destinos e filtros para SMS, e-mail e WhatsApp. O APK faz as entregas; a web mostra o estado das tentativas. A aba Início prioriza o ambiente selecionado, eventos recentes, favoritos e a saúde do celular. O modo demo do APK inicia desligado.

A API local oferece `GET /healthz` público e `GET /api/v1/state` e `POST /api/v1/action` protegidos pela chave web. HTTP local não cifra a chave; use VPN/HTTPS para acesso externo. O navegador tenta reconectar quando o APK retorna. A central Android não depende de homelab, Node ou PM2. O Termux é opcional apenas para a [ponte WhatsApp](../bridge/whatsapp/README.md).

## Prévia independente

Com Node.js 20 ou superior, execute `cd web && npm start` e abra `http://127.0.0.1:8787/`. `HOST=0.0.0.0 PORT=8787 npm start` disponibiliza esta prévia Node na rede local. Ela usa dados fictícios no navegador, sem ESP32, sem persistência e sem envios externos. Para testar o frontend: `npm test`.
