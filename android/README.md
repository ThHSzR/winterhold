# Winterhold Android

O APK Kotlin/Jetpack Compose é a central: recebe publicações MQTT do ESP32, avalia limiares, mantém histórico local e serve a interface web. O modo demo permanece disponível sem sensores.

## Instalar ou compilar

Baixe o [APK de debug da main](../dist/winterhold-demo-debug.apk), instale e abra em Android 8.0 (API 26) ou superior. Se uma instalação anterior tiver assinatura de debug diferente, desinstale-a antes; isso apaga o estado local. Para compilar, abra `android/` no Android Studio com JDK 17 e SDK 36, ou execute `./gradlew :app:testDebugUnitTest :app:assembleDebug`. O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Configurar o ESP32

1. Conecte celular e ESP32 à mesma rede Wi-Fi. Abra o aplicativo; conceda as solicitações de notificação e bateria necessárias para uso contínuo.
2. Em **Ajustes → ESP32 / MQTT**, confira que a porta `1883` está ativa, copie a chave MQTT do aparelho e anote o IP do celular em **Acesso web pelo aplicativo**.
3. No `backend/include/config.h`, preencha `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_HOST` com o IP do celular e `MQTT_PASSWORD` com a chave. Mantenha `MQTT_USER` como `winterhold`. Defina um `DEVICE_ID` único por ESP32. Não publique senhas ou chaves em commits.
4. Compile/grave conforme [backend/README.md](../backend/README.md). Ative **Mostrar leituras reais** no APK. Se houver vários dispositivos, selecione um nos Ajustes.

A central aceita MQTT 3.1.1 QoS 0, `CONNECT`, `PUBLISH`, `PINGREQ` e `DISCONNECT`, com até oito conexões simultâneas. Ela não é um broker MQTT completo e não implementa assinaturas. O tópico deve ser `winterhold/<DEVICE_ID>/leituras`, e o JSON deve conter `device_id` correspondente e os campos `temperatura_c`, `umidade_pct`, `tensao_rms_v` numéricos ou nulos. O horário de recebimento é usado no histórico.

## Web e estado

Acesse `http://IP_DO_CELULAR:8790/` em outro dispositivo da mesma rede local. A página consulta `GET /api/v1/state` e altera o estado compartilhado por `POST /api/v1/action`. A chave MQTT só aparece nos Ajustes nativos. A API web acadêmica usa HTTP sem autenticação; utilize uma rede confiável. A página tenta reconectar a cada cinco segundos. Termux, Node e PM2 não são necessários.

O serviço Android em primeiro plano continua com a tela fechada, tenta recuperar após reboot/desbloqueio e salva o estado em arquivo privado atômico `demo-state.json`. Use **Parar central** para fechar as portas `8790` e `1883`, ou **Retomar central** para reabri-las. Forçar parada pelo sistema impede retomada até abrir o app novamente. Otimizações de bateria do fabricante e falta de rede podem interromper a coleta; mantenha o celular alimentado na bancada.

Amostras e eventos reais são separados dos fictícios. O histórico guarda até 60 amostras por ESP32 (máximo 32 dispositivos), e eventos são limitados a 500 na persistência. Temperatura e umidade acima dos limites, tensão fora da faixa e perda de sinal por mais de 15 segundos geram eventos locais; recuperação também é registrada. Valores nulos aparecem como `—` e não geram alerta para aquela grandeza. Alterar limiar só afeta novas amostras. SMS, e-mail e WhatsApp são prévia visual; nenhum aviso externo é enviado.

## Verificar no aparelho

```sh
am start -W -n br.eng.thsouza.winterhold/.MainActivity
curl -i http://127.0.0.1:8790/healthz
curl -i http://127.0.0.1:8790/api/v1/state
```

O workflow em `.github/workflows/android-demo.yml` executa build/testes e smoke tests em emuladores Android 11 e 15. Consulte [web/README.md](../web/README.md) para a prévia Node independente.
