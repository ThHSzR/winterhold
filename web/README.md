# Winterhold Web — demonstração

Interface responsiva que acompanha o aplicativo Android Winterhold. Ao abrir o APK, ele inicia um servidor demo na porta `8790`, serve esta página e compartilha o mesmo estado fictício com ela. A web consulta o APK e envia a ele as ações de demonstração. MQTT, SQLite, regras reais e envio de mensagens ainda não estão implementados.

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

Este servidor Node é somente uma prévia independente para desenvolvimento. `GET /healthz` responde com `{"status":"ok","mode":"demo","service":"winterhold-web"}`. Execute `npm test` para verificar o modelo da prévia e o servidor. Abrir essa prévia não conecta ao APK.

## Telas

- Início: temperatura em destaque, umidade, tensão, estado do ESP32, eventos recentes e saúde ilustrativa do celular central.
- Histórico: filtros por variável, gráfico e amostras fictícias.
- Alertas: eventos, filtros, resolução e prévia de roteamento.
- Equipe: perfis por variável e canal, como no APK.
- Ajustes: simulação, limiares e canais SMS, e-mail e WhatsApp na prévia.

As amostras surgem a cada cinco segundos enquanto a demo estiver ativa. Ao acessar pelo APK, as alterações feitas na web aparecem no aplicativo e vice-versa; o estado demo é salvo no celular e restaurado após reiniciar o processo. Bateria, memória, armazenamento e tempo ligado vêm do Android. Na prévia Node, esses valores são fictícios e o estado continua independente. Ajustar limiares não dispara regras reais.

## Acesso remoto servido pelo APK

1. Instale e abra o APK de debug atualizado.
2. Em **Ajustes → Acesso web pelo aplicativo**, copie o endereço completo mostrado pelo celular.
3. Abra `http://IP_DO_CELULAR:8790/` em outro dispositivo da mesma rede local. Não é necessário informar chave ou login.

O APK usa a porta `8790`, distinta da porta `8787` usada pela prévia Node. Termux e PM2 não são necessários. O serviço Android mantém a central com a tela do aplicativo fechada, retoma após reboot/desbloqueio e preserva dados demo em um arquivo local. A página tenta reconectar automaticamente após interrupção. Esta fase acadêmica usa HTTP direto na rede local, sem túnel, HTTPS ou autenticação. Se o IP mudar, abra o novo endereço. Limites de bateria, parada explícita e preparação inicial estão detalhados no [guia Android](../android/README.md#execução-contínua-e-recuperação).

## Prévia opcional no Termux/Node

Para executar apenas a prévia independente:

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

Pela rede Tailscale, abra `http://IP_TAILSCALE_DO_CELULAR:8787` no navegador de outro dispositivo autorizado. O PM2 é opcional para manter a prévia Node em execução após fechar o terminal. Essa prévia gera seus próprios dados fictícios e não recebe alterações do APK.

O homelab não fornece dados de outros serviços ao Winterhold. O servidor Node de prévia não tem autenticação.

## Contrato da próxima etapa

O APK oferece uma API **demo** local: `GET /api/v1/state` e `POST /api/v1/action`, sem autenticação nesta fase acadêmica. O navegador usa essas rotas e atualiza os dados a cada cinco segundos. A API manipula somente estado fictício compartilhado e salvo no celular; não recebe MQTT nem envia mensagens. Contrato de produção, SQLite, autenticação e HTTPS continuam fora desta entrega.
