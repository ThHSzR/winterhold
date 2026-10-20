# Winterhold Android

O APK Kotlin/Jetpack Compose é a central. Ele recebe MQTT dos ESP32, guarda histórico e alertas, entrega notificações e serve a web com os mesmos ajustes. Somente os **usuários de acesso web** são administrados exclusivamente no telefone.

## Instalar ou compilar

Instale o [APK de debug](../dist/winterhold-demo-debug.apk) no Android 8+ (API 26). Instalações anteriores com assinatura diferente podem exigir desinstalação, que apaga os dados locais. Para compilar, abra `android/` no Android Studio com JDK 17 e SDK 36, ou execute `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Saída: `app/build/outputs/apk/debug/app-debug.apk`.

## ESP32 e ambientes

Conecte celular e ESP à mesma rede Wi-Fi. Em **Ajustes → ESP32 / MQTT** no app ou na web, copie a chave e anote o IP do celular. Em `backend/include/config.h`, configure `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_HOST` (IP do celular), `MQTT_PASSWORD`, `MQTT_USER = winterhold` e um `DEVICE_ID` único. Grave conforme [guia do firmware](../backend/README.md). Crie/edite o ambiente com nome, ícone e faixas mínima/máxima para temperatura, umidade e tensão; também é criado após a primeira leitura. Use a estrela para fixar no início.

A central escuta MQTT 3.1.1 na porta 1883, com `CONNECT`, `PUBLISH` QoS 0, `PINGREQ` e `DISCONNECT` e até oito conexões; não implementa assinaturas. Tópico `winterhold/<DEVICE_ID>/leituras`. Cada amostra aceita valores numéricos ou nulos para `temperatura_c`, `umidade_pct`, `tensao_rms_v`; o relógio do telefone marca o recebimento. São mantidas até 60 leituras por ESP (máximo 32). Leituras fora da faixa e silêncio acima de 15 segundos geram eventos; a recuperação também é registrada.

## Web, usuários e demonstração

Em **Ajustes → Web no celular**, abra `http://IP_DO_CELULAR:8790/` de outro dispositivo. Em **Usuários de acesso web** no app, crie usuários, copie, rotacione ou desative as chaves. Mantenha pelo menos um usuário ativo. A web pede uma chave, guarda-a só na sessão do navegador e usa `GET /api/v1/state`, `POST /api/v1/action` e `GET /api/v1/bridge` autenticados. A API não fornece a lista de usuários nem suas chaves. A rede local HTTP não cifra o tráfego; use VPN/proxy HTTPS externamente.

O modo real inicia ativo. Sem sensores, ative **Ajustes → Demonstração** no app ou na web: cinco ambientes fictícios têm faixas distintas, favoritos e históricos independentes. Em **Ambientes**, ajuste uma leitura fictícia; desvios criam apenas eventos locais. Em **Alertas → Testar**, escolha uma variável para enviar uma notificação marcada `[TESTE]` aos destinatários habilitados. Esse botão pode gerar custos de SMS ou e-mail; eventos fictícios automáticos não saem do aparelho. O modo demo não substitui nem mistura as leituras reais. Ao reiniciar, a central retorna ao modo real com a simulação pausada.

O serviço Android em primeiro plano continua com a tela fechada e tenta retomar após boot/desbloqueio; otimizações agressivas de bateria e falta de rede podem interrompê-lo. **Parar central** fecha 8790/1883; retome pelo aplicativo. Saúde exibida na web é medida pelo Android. Não se consultam serviços externos do homelab.

## Notificações

Em **Equipe**, cadastre destinatários E.164 (`+5511999999999`) e e-mail, métricas e canais. Em **Ajustes → Canais**, habilite canais. Para SMS, permita `SEND_SMS` no telefone com SIM ativo. Para SMTP, informe qualquer servidor TLS na porta 465, usuário, remetente e senha de aplicativo; o certificado é validado. Para WhatsApp, configure URL HTTPS e chave da [ponte Node/Baileys](../backend/whatsapp/README.md), consulte o estado/QR pela web e pareie o telefone WhatsApp. A ponte é opcional e pode executar em outro host; a interface remota continua no APK. Segredos são cifrados com Android Keystore e omitidos das respostas web. A web pode alterar credenciais, mas apenas o telefone pode conceder permissões do sistema.

Tentativas são reservadas antes do envio. `failed` e `uncertain` exigem conferência manual. `submitted` indica que o provedor aceitou a submissão, não entrega final; sem reenvio automático. Os estados aparecem em **Alertas → Ver entregas**.

## Verificar

```sh
am start -W -n br.eng.thsouza.winterhold/.MainActivity
curl -i http://127.0.0.1:8790/healthz
curl -i -H "X-Winterhold-Key: CHAVE_WEB_DO_APK" http://127.0.0.1:8790/api/v1/state
```

O workflow `.github/workflows/android-demo.yml` compila, roda testes e verifica emuladores Android 11 e 15. Veja também a [prévia web independente](../web/README.md).
