# Ponte WhatsApp Winterhold (Termux)

O APK continua sendo a central: recebe MQTT, decide os alertas e aciona esta ponte somente para WhatsApp. A ponte usa a biblioteca **Baileys**, como o projeto `bom-dia`, com sessão própria. Não compartilhe a pasta de sessão do `bom-dia`: a conta pode ser a mesma, mas cada instância precisa de dispositivo pareado separado. A interface web nunca acessa a ponte diretamente.

## Instalação no próprio Android

1. Instale/abra Termux no celular que executa o APK e instale Node.js 22 ou superior: `pkg update && pkg install nodejs git` (confira com `node -v`).
2. Clone o projeto: `git clone https://github.com/ThHSzR/winterhold.git && cd winterhold/bridge/whatsapp && npm install`.
3. No APK: **Ajustes → WhatsApp → Copiar token da ponte**. No Termux, configure o token na sessão atual com `export WINTERHOLD_BRIDGE_TOKEN='TOKEN_COPIADO'`. Não salve o token no git e não cole em logs públicos.
4. Execute `npm start`. O QR aparece no terminal; no WhatsApp, abra **Dispositivos conectados → Conectar dispositivo** e escaneie o QR. A sessão fica em `.local/session/` dentro da ponte. A primeira tentativa exige internet e pareamento concluído.
5. Mantenha a ponte executando em segundo plano no próprio aparelho. Em outra sessão do Termux, confira com `curl -H "Authorization: Bearer $WINTERHOLD_BRIDGE_TOKEN" http://127.0.0.1:8791/healthz` (retorno `connected: true`). O serviço escuta **apenas em 127.0.0.1**.

Configure o telefone E.164 (`+5511999999999`) e marque WhatsApp no destinatário pelo APK ou pela web com chave. O primeiro evento real novo envia pelo Baileys. Há reserva em disco antes de `sendMessage` e ID estável por evento/destinatário: reiniciar o processo não reenvia automaticamente um envio com resultado incerto. O APK consulta os recibos de servidor e de entrega periodicamente; **submitted** indica apenas que o envio foi submetido, **server_ack** que o servidor aceitou, **delivered** que o dispositivo destinatário acusou entrega e **uncertain** exige conferência manual antes de repetir. `failed` no APK pode indicar ponte ausente ou erro antes de o servidor aceitar. Não há reenvio automático de tentativas com erro.

Baileys é uma biblioteca não oficial para dispositivos conectados. Verifique termos e possíveis mudanças no protocolo antes de uso prolongado. O SMS e o e-mail funcionam sem Termux.
