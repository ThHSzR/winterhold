# Winterhold Web — demonstração

Interface responsiva para explorar o painel Winterhold no navegador. Os dados são fictícios, gerados em memória pelo próprio navegador. Não há conexão MQTT, banco, API operacional ou envio de mensagens.

## Executar

Requer Node.js 20 ou superior. Não há dependências para instalar.

```bash
cd web
npm start
```

Acesse `http://127.0.0.1:8787`. Para mudar endereço e porta:

```bash
HOST=0.0.0.0 PORT=8787 npm start
```

`GET /healthz` responde com `{"status":"ok","mode":"demo","service":"winterhold-web"}`. Execute `npm test` para verificar o modelo da demo e o servidor.

## Telas

- Visão geral, leituras e histórico fictício de temperatura, umidade e tensão.
- Ambiente com presença, porta, luminosidade, ar-condicionado, relé, agenda e dispositivos ilustrativos.
- Infraestrutura com energia, bateria, CPU, RAM, armazenamento, rede, MQTT, Cloudflare Tunnel, Tailscale/SSH, Playit e Minecraft em estados de exemplo.
- Automações planejadas: climatização por agenda/presença, economia de energia, watchdog, queda/restauração e Wake-on-LAN.
- Alertas demo, filtro, resolução e prévia de roteamento por perfil e canal; equipe e ajustes locais.

As amostras surgem a cada cinco segundos enquanto a demo estiver ativa. Nada é persistido após recarregar a página. Ajustar limiares não dispara regras reais. A interface nunca envia comando ao ar-condicionado ou a outro dispositivo.

## Homelab Android/Termux

No celular que hospeda o repositório, com Node.js 20+ e PM2 disponíveis:

```bash
git fetch origin APK
git checkout APK
cd web
HOST=0.0.0.0 PORT=8787 pm2 start server.mjs --name winterhold-web --update-env
pm2 save
curl http://127.0.0.1:8787/healthz
```

Pela rede Tailscale, abra `http://IP_TAILSCALE_DO_CELULAR:8787` no navegador de outro dispositivo autorizado. Se a porta já estiver em uso, escolha outra em `PORT`. Para atualização: `git pull --ff-only origin APK` e `pm2 restart winterhold-web --update-env`. A demo não exige Android Studio.

Expor este servidor diretamente à internet não é a etapa prevista: ele não tem autenticação. A publicação futura deve passar por autenticação e HTTPS antes de disponibilizar a API da central.

## Contrato da próxima etapa

O celular Android será a central: recebe ESP/Pico por MQTT, processa regras, registra eventos e histórico local e expõe uma API autenticada para a web. O navegador consulta somente o celular. Uma versão inicial da API poderá oferecer `GET /api/v1/status`, `GET /api/v1/leituras`, `GET /api/v1/eventos` e `GET /api/v1/contatos`, com atualização por SSE ou WebSocket; **essas rotas ainda não existem**. Permissões, controle de acesso, retomada offline e entrega de alertas precisarão ser definidos antes da ligação a dados reais.
