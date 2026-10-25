# Winterhold web

A página `https://IP_DO_CELULAR:8792/` é servida pelo APK, com os mesmos ambientes, destinatários, canais, SMTP, WhatsApp, MQTT e demo. A gestão de usuários, senhas e sessões existe somente no telefone.

Crie um login no APK e entre com usuário/senha. Confira o SHA-256 do certificado local nos Ajustes do APK antes de aceitá-lo no navegador. O cookie de sessão é HttpOnly/SameSite=Strict/Secure; credenciais SMTP/WhatsApp e hashes não retornam na API. HTTP 8790 rejeita a API pela LAN; localhost via ADB é permitido para diagnóstico. Rotas: POST `/api/v1/login`, POST `/api/v1/logout`, GET `/api/v1/state`, POST `/api/v1/action`, GET `/api/v1/bridge` (estado/QR) e POST `/api/v1/bridge` (novo pareamento). POST exige `X-Winterhold-Request: 1`.

O demo oferece cinco ambientes independentes. Ao ativá-lo, confirme que violações/normalizações podem enviar mensagens reais conforme os filtros configurados; elas levam `[SIMULADO]`. O ensaio explícito leva `[TESTE SIMULADO]`. O Baileys acompanha o APK ARM64, com QR no aplicativo e na web. Consulte [implementação e limites](../docs/ISSUE_2.md).

## Prévia independente

Com Node.js 20+, execute `cd web && npm start` e abra `http://127.0.0.1:8787/`. `HOST=0.0.0.0 PORT=8787 npm start` disponibiliza a prévia na rede local. Ela usa dados fictícios no navegador, sem ESP, sem persistência nem envios externos. Testes: `npm test`.
