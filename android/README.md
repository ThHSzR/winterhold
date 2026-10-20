# Winterhold Android

O APK Kotlin/Jetpack Compose é a central. Ele recebe MQTT dos ESP32, guarda histórico e alertas, entrega notificações e serve a web com os mesmos ajustes. Somente os **usuários de acesso web** são administrados exclusivamente no telefone.

## Instalar ou compilar

Instale o [APK de debug](../dist/winterhold-demo-debug.apk) no Android 8+ (API 26). Instalações anteriores com assinatura diferente podem exigir desinstalação, que apaga os dados locais. Antes de compilar o APK com runtime, execute `npm ci --omit=optional --ignore-scripts` em `backend/whatsapp`, instale `lief==0.17.6` no Python e rode `python android/prepare-runtime.py` na raiz. Os pacotes Android são fixados por URL/versão/SHA-256 em `android/runtime-lock.json`, com bibliotecas relocalizadas para o APK. Depois, abra `android/` no Android Studio com JDK 17 e SDK 36, ou execute `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Saída: `app/build/outputs/apk/debug/app-debug.apk`.

## ESP32 e ambientes

Conecte celular e ESP à mesma rede Wi-Fi. Em **Ajustes → ESP32 / MQTT** no app ou na web, copie a chave e anote o IP do celular. Em `backend/include/config.h`, configure `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_HOST` (IP do celular), `MQTT_PASSWORD`, `MQTT_USER = winterhold` e um `DEVICE_ID` único. Grave conforme [guia do firmware](../backend/README.md). Crie/edite o ambiente com nome, ícone e faixas mínima/máxima para temperatura, umidade e tensão; também é criado após a primeira leitura. Use a estrela para fixar no início.

A central escuta MQTT 3.1.1 na porta 1883, com `CONNECT`, `PUBLISH` QoS 0, `PINGREQ` e `DISCONNECT` e até oito conexões; não implementa assinaturas. Tópico `winterhold/<DEVICE_ID>/leituras`. Cada amostra aceita valores numéricos ou nulos para `temperatura_c`, `umidade_pct`, `tensao_rms_v`; o relógio do telefone marca o recebimento. São mantidas até 60 leituras por ESP (máximo 32). Leituras fora da faixa e silêncio acima de 15 segundos geram eventos; a recuperação também é registrada.

## Web, usuários e demonstração

Em **Ajustes → Web no celular**, abra `https://IP_DO_CELULAR:8792/`. O certificado é próprio do aparelho, com chave não exportável no Android Keystore: compare o SHA-256 exibido no APK antes de aceitá-lo no navegador. HTTP 8790 só admite autenticação/API de localhost, por exemplo `adb forward tcp:18790 tcp:8790`. Não há confiança em cabeçalhos de proxy.

Em **Usuários de acesso web**, crie, edite, desative, exclua, redefina senhas e revogue sessões. O primeiro login é `admin`; a senha inicial aparece apenas no processo de criação e pode ser redefinida no APK a qualquer momento. Senhas usam PBKDF2-HMAC-SHA256, salt individual e 210.000 iterações; nunca são retornadas na API. A web só oferece login/logout e cookie HttpOnly/SameSite=Strict (Secure no HTTPS), com sessão de oito horas. Reiniciar o processo exige novo login. Há limite por origem e global de tentativas. POST exige cabeçalho próprio e origem compatível. Rotas de administração web não existem.

**Migração:** cada conta antiga recebe login `usuario1`, `usuario2` etc., mostrado no APK. Sua chave anterior funciona como senha temporária por sete dias após a atualização. Ela é convertida em hash; o texto original é removido. Redefina uma senha de 12–256 caracteres pelo APK. A atualização preserva histórico, destinatários e canais.

O modo real inicia ativo. Ao ativar **Demonstração**, confirme que leituras fictícias podem enviar SMS, e-mail e WhatsApp reais. Os cinco ambientes avaliam seus próprios mínimos/máximos tanto na atualização automática quanto manual. Uma ocorrência abre por ambiente/variável/lado da faixa, sem repetir em cada leitura; a normalização é notificada e permite novo disparo. Reconhecer o alerta na interface não libera repetição enquanto a leitura continuar fora da faixa. Mensagens externas levam `[SIMULADO]`; ensaios explícitos levam `[TESTE SIMULADO]`. Destinos e estados, incluindo bloqueio por falta de configuração/permissão, aparecem em Alertas → Ver entregas. A prévia web independente continua sem envios externos.

O serviço Android em primeiro plano continua com a tela fechada e tenta retomar após boot/desbloqueio; otimizações agressivas de bateria e falta de rede podem interrompê-lo. **Parar central** fecha 8790/1883; retome pelo aplicativo. Saúde exibida na web é medida pelo Android. Não se consultam serviços externos do homelab.

## Notificações

Em **Equipe**, cadastre destinatários E.164 (`+5511999999999`) e e-mail, métricas e canais. Em **Ajustes → Canais**, habilite canais. Para SMS, permita `SEND_SMS` no telefone com SIM ativo. Para SMTP, informe qualquer servidor TLS na porta 465, usuário, remetente e senha de aplicativo; o certificado é validado. Para WhatsApp no A30/ARM64, o APK inicia seu próprio Node 24/Baileys em loopback 8791 com token privado. O QR aparece nos Ajustes do APK e na web; escaneie com a conta WhatsApp que enviará os alertas. A sessão fica em `files/whatsapp-session`, separada dos módulos, e sobrevive a atualização e reinício. **Novo pareamento** substitui a sessão local. A URL/token podem apontar para uma ponte HTTPS externa como alternativa. Segredos são cifrados com Android Keystore e omitidos das respostas web. A web pode alterar credenciais, mas apenas o telefone pode conceder permissões do sistema.

Tentativas são reservadas antes do envio. `failed` e `uncertain` exigem conferência manual. `submitted` indica que o provedor aceitou a submissão, não entrega final; sem reenvio automático. Os estados aparecem em **Alertas → Ver entregas**.

## Verificar

```sh
am start -W -n br.eng.thsouza.winterhold/.MainActivity
curl -i http://127.0.0.1:8790/healthz
curl -c cookies.txt -H 'X-Winterhold-Request: 1' -H 'Content-Type: application/json' \
  --data '{"username":"LOGIN_DO_APK","password":"SENHA_DO_APK"}' http://127.0.0.1:8790/api/v1/login
curl -b cookies.txt http://127.0.0.1:8790/api/v1/state
```

O workflow `.github/workflows/android-demo.yml` compila, roda testes e verifica emuladores Android 11 e 15. Veja também a [prévia web independente](../web/README.md).
