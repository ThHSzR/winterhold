# Winterhold Web — demonstração

Interface responsiva que acompanha o aplicativo Android Winterhold. O APK será a central de recepção MQTT, processamento, histórico e alertas; a web será uma forma de consultar essa central. Nesta fase, as duas interfaces são demos independentes e seus dados são fictícios. A web não tem conexão MQTT, banco, API operacional ou envio de mensagens.

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

- Início: temperatura em destaque, umidade, tensão, estado do ESP32, eventos recentes e saúde ilustrativa do celular central.
- Histórico: filtros por variável, gráfico e amostras fictícias.
- Alertas: eventos, filtros, resolução e prévia de roteamento.
- Equipe: perfis por variável e canal, como no APK.
- Ajustes: simulação, limiares e canais SMS, e-mail e WhatsApp na prévia.

As amostras surgem a cada cinco segundos enquanto a demo estiver ativa. Nada é persistido após recarregar a página. A saúde do celular exibida na web também é fictícia; ainda não existe coleta do Android. Ajustar limiares não dispara regras reais.

## Homelab Android/Termux

No celular que hospeda o repositório, com Node.js 20+:

```bash
git fetch origin APK
git checkout APK
cd web
HOST=0.0.0.0 PORT=8787 npm start
```

Em outra sessão, confira:

```bash
curl http://127.0.0.1:8787/healthz
```

Pela rede Tailscale, abra `http://IP_TAILSCALE_DO_CELULAR:8787` no navegador de outro dispositivo autorizado. O PM2 é opcional para manter o processo em execução após fechar o terminal: `HOST=0.0.0.0 PORT=8787 pm2 start server.mjs --name winterhold-web --update-env`. Se a porta já estiver em uso, escolha outra em `PORT`. A demo web não exige Android Studio.

O homelab hospeda apenas esta interface. A aplicação não coleta estado de Minecraft, Tailscale, Cloudflare ou outros serviços. Este servidor demo não tem autenticação; uma publicação externa futura requer autenticação e HTTPS.

## Contrato da próxima etapa

O celular Android será a central: recebe ESP32 por MQTT, processa regras, registra eventos e histórico local e expõe uma API autenticada para a web. O navegador consulta somente o celular. Uma versão inicial da API poderá oferecer `GET /api/v1/status`, `GET /api/v1/leituras`, `GET /api/v1/eventos`, `GET /api/v1/contatos` e um estado de saúde do próprio celular, com atualização por SSE ou WebSocket; **essas rotas ainda não existem**. A demo web e o APK não compartilham estado nesta fase.
