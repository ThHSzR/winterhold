# Baileys no APK e ponte alternativa

O APK ARM64 inclui Node 24 e os módulos fixados no lockfile. O aplicativo prepara e supervisiona a ponte local sem Termux/PM2. Pareamento QR e estado ficam no APK e na web autenticada. Consulte [implementação e validação](../../docs/ISSUE_2.md).

As instruções abaixo são para hospedar opcionalmente a ponte fora do APK. Use `npm ci --omit=optional --ignore-scripts`, Node 22+ e preserve o diretório privado de sessão.

# Ponte WhatsApp Node/Baileys

Processo auxiliar **opcional** para WhatsApp. O Android continua recebendo MQTT, avaliando alertas e servindo a única interface web remota. A ponte recebe apenas mensagens de saída do APK; não consulta sensores nem outros serviços do homelab. Baileys é uma integração não oficial do WhatsApp Web, que pode quebrar com alterações do protocolo ou restrições da conta. O projeto não depende de token Meta ou Termux.

## Executar

Requer Node.js 20+, acesso à internet para pareamento/WhatsApp e armazenamento privado persistente. Na pasta `backend/whatsapp`:

```sh
npm install
export WINTERHOLD_BRIDGE_TOKEN="$(openssl rand -hex 32)"
export WINTERHOLD_BRIDGE_DATA=/caminho/privado/winterhold-whatsapp
npm start
```

Por padrão escuta apenas `127.0.0.1:8791`, útil quando Node roda **no mesmo aparelho** que a central, situação ainda não validada em Android. Para outro host, publique a ponte **somente por HTTPS** em rede privada com proxy reverso; configure `WINTERHOLD_BRIDGE_HOST` conforme a rede e bloqueie acessos públicos. Em **Ajustes → WhatsApp** no APK ou na web, informe a URL base HTTPS e o mesmo token. Na web, clique **Ver conexão e QR do WhatsApp** e leia o QR no WhatsApp do telefone que enviará as mensagens. Credenciais de sessão ficam no diretório privado e não devem ir ao Git.

A API interna aceita `POST /send` com `{ "id", "to": "+5511...", "message" }`, responde 202 após submissão e oferece `GET /status` com conexão e QR em data URL. Ambos exigem `Authorization: Bearer`. A web recebe o estado/QR por proxy autenticado no APK (`/api/v1/bridge`); não abre outra interface pública. IDs são reservados em disco antes do envio para evitar repetição automática. Uma falha entre reserva e confirmação é incerta e requer inspeção manual. Use um processo supervisionado (systemd/PM2) para reiniciar a ponte caso necessário; isso não é requisito para a web do APK.

**Limite atual:** o runtime Node/Baileys não foi integrado nem testado dentro do APK; portanto, WhatsApp requer um processo Node separado e rede HTTPS até ele. O envio real e o pareamento precisam ser verificados com uma conta de teste antes de uso contínuo.
