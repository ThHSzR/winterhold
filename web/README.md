# Winterhold web

A página em `http://IP_DO_CELULAR:8790/` é servida pelo **APK**. O celular recebe MQTT, avalia limites, persiste medições, envia alertas e exibe sua própria saúde. A web compartilha ambientes, destinatários, canais, SMTP, ponte WhatsApp, MQTT e modo demo com o aplicativo. Apenas a gestão dos usuários de acesso web e das suas chaves permanece exclusivamente no telefone.

Em **Ajustes → Usuários de acesso web** no celular, copie uma chave pessoal. Insira-a na página; ela fica na sessão do navegador e acompanha as chamadas à API em `X-Winterhold-Key`. O navegador consulta `GET /api/v1/state`, aplica `POST /api/v1/action` e mostra a situação/QR da ponte via `GET /api/v1/bridge`. As credenciais secretas SMTP e WhatsApp são salvas cifradas no Android e não voltam nas respostas; a chave MQTT é visível na página autenticada. HTTP local não cifra tráfego: use VPN ou proxy HTTPS para acesso externo.

**Ambientes** cadastra um `DEVICE_ID` por ESP, edita faixas, nome e ícone e fixa favoritos. O modo demo, desligado após iniciar o APK, oferece cinco ambientes fictícios com leituras independentes e ajustáveis. **Alertas → Testar** pode enviar mensagens reais marcadas `[TESTE]` aos destinatários cadastrados; eventos demo automáticos só aparecem no painel. A ponte [Node/Baileys](../backend/whatsapp/README.md) é auxiliar opcional para WhatsApp; a web e a recepção MQTT continuam no APK sem Node/PM2.

## Prévia independente

Com Node.js 20+, execute `cd web && npm start` e abra `http://127.0.0.1:8787/`. `HOST=0.0.0.0 PORT=8787 npm start` disponibiliza a prévia na rede local. Ela usa dados fictícios no navegador, sem ESP, sem persistência nem envios externos. Testes: `npm test`.
