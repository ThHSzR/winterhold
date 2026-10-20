# Winterhold Android

O APK Kotlin/Jetpack Compose é a central: recebe publicações MQTT do ESP32, avalia limiares, mantém histórico local e serve a interface web. O app inicia em modo real, aguardando sensores; dados de demonstração não são enviados.

## Instalar ou compilar

Baixe o [APK de debug da main](../dist/winterhold-demo-debug.apk), instale e abra em Android 8.0 (API 26) ou superior. Se uma instalação anterior tiver assinatura de debug diferente, desinstale-a antes; isso apaga o estado local. Para compilar, abra `android/` no Android Studio com JDK 17 e SDK 36, ou execute `./gradlew :app:testDebugUnitTest :app:assembleDebug`. O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Configurar o ESP32

1. Conecte celular e ESP32 à mesma rede Wi-Fi. Abra o aplicativo; conceda as solicitações de notificação e bateria necessárias para uso contínuo.
2. Em **Ajustes → ESP32 / MQTT**, confira que a porta `1883` está ativa, copie a chave MQTT do aparelho e anote o IP do celular em **Acesso web pelo aplicativo**.
3. No `backend/include/config.h`, preencha `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_HOST` com o IP do celular e `MQTT_PASSWORD` com a chave. Mantenha `MQTT_USER` como `winterhold`. Defina um `DEVICE_ID` único por ESP32. Não publique senhas ou chaves em commits.
4. Na aba **Ambientes → Adicionar ambiente**, cadastre o mesmo `DEVICE_ID` do firmware, nome e ícone da sala e as faixas mínima/máxima de temperatura, umidade e tensão. Repita para os demais ESPs. O cadastro também é criado automaticamente quando um ESP novo publica sua primeira amostra; personalize-o depois.
5. Compile/grave conforme [backend/README.md](../backend/README.md). Selecione o ambiente na tela inicial ou na aba Ambientes e use a estrela para fixá-lo no Início.

A central aceita MQTT 3.1.1 QoS 0, `CONNECT`, `PUBLISH`, `PINGREQ` e `DISCONNECT`, com até oito conexões simultâneas. Ela não é um broker MQTT completo e não implementa assinaturas. O tópico deve ser `winterhold/<DEVICE_ID>/leituras`, e o JSON deve conter `device_id` correspondente e os campos `temperatura_c`, `umidade_pct`, `tensao_rms_v` numéricos ou nulos. O horário de recebimento é usado no histórico.

## Web e estado

Acesse `http://IP_DO_CELULAR:8790/` em outro dispositivo da mesma rede local. A página consulta `GET /api/v1/state` e altera o estado compartilhado por `POST /api/v1/action`. A chave MQTT só aparece nos Ajustes nativos. A API exige a chave web mostrada no APK; a página solicita essa chave e a guarda apenas na sessão do navegador. O transporte HTTP local não cifra tráfego: utilize VPN/HTTPS para acesso externo. A página tenta reconectar a cada cinco segundos. Todos os canais partem diretamente do APK; não há serviço auxiliar em Termux.

O serviço Android em primeiro plano continua com a tela fechada, tenta recuperar após reboot/desbloqueio e salva o estado em arquivo privado atômico `demo-state.json`. Use **Parar central** para fechar as portas `8790` e `1883`, ou **Retomar central** para reabri-las. Forçar parada pelo sistema impede retomada até abrir o app novamente. Otimizações de bateria do fabricante e falta de rede podem interromper a coleta; mantenha o celular alimentado na bancada.

Amostras e eventos reais são separados dos fictícios. O histórico guarda até 60 amostras por ESP32 (máximo 32 ambientes), e eventos são limitados a 500 na persistência. Cada ambiente tem nome, ícone e uma faixa de mínimo/máximo independente para as três métricas. Leituras fora da faixa e perda de sinal por mais de 15 segundos geram eventos locais identificados pelo `DEVICE_ID`; recuperação também é registrada. Valores nulos aparecem como `—` e não geram alerta para aquela grandeza. Alterar um limite só afeta novas amostras daquele ambiente. Estados salvos da versão anterior são migrados sem perder leituras e ajustes. Alertas reais novos acionam notificações locais e canais habilitados quando há destinatários e credenciais. Eventos fictícios não são enviados; reservas persistidas evitam reenvio automático após falha ou reinício.

## Verificar no aparelho

```sh
am start -W -n br.eng.thsouza.winterhold/.MainActivity
curl -i http://127.0.0.1:8790/healthz
curl -i -H "X-Winterhold-Key: CHAVE_WEB_DO_APK" http://127.0.0.1:8790/api/v1/state
```

O workflow em `.github/workflows/android-demo.yml` executa build/testes e smoke tests em emuladores Android 11 e 15. Consulte [web/README.md](../web/README.md) para a prévia Node independente.

## Configurar notificações

1. Em **Alertas → Equipe**, cadastre os destinatários. Para SMS ou WhatsApp, informe número internacional E.164 (por exemplo, `+5511999999999`); para e-mail, informe endereço válido. Ajuste os canais e métricas de cada perfil. Perfis padrão não têm destinos reais.
2. Em **Ajustes → Canais**, habilite os canais. Para SMS, toque em **Permitir envio de SMS** e use um aparelho com SIM ativo e serviço de SMS. Custos da operadora podem se aplicar. O status `submitted` é o resultado do `SmsManager` no aparelho, não a comprovação de recebimento pelo destinatário.
3. Para testar o domínio `thsouza.eng.br`, configure no APK o servidor SMTP de sua conta, usuário, remetente do domínio e senha de aplicativo. O cliente usa TLS implícito na porta 465, valida o certificado do servidor e autentica com `AUTH LOGIN`. O segredo fica cifrado com Android Keystore; não entra no estado web, backup nem código. Não há credenciais pré-instaladas.
4. Para WhatsApp, crie/configure uma conta na **WhatsApp Business Platform (Meta Cloud API)**, obtenha o ID do número comercial e um token com a permissão `whatsapp_business_messaging`. Crie e obtenha aprovação para um modelo de utilidade, por exemplo `winterhold_alerta`, com **exatamente duas variáveis de texto no corpo**: título e detalhes. No APK em **Ajustes → WhatsApp**, informe o ID, nome do modelo, idioma (por exemplo `pt_BR`) e token. O APK envia diretamente por HTTPS para a Meta; não há processo Node, pareamento Baileys ou Termux. Mensagens iniciadas pela central fora da janela de atendimento exigem modelo aprovado; a Meta pode cobrar pelo envio. A resposta HTTP positiva indica aceitação da API, não entrega final ao destinatário. A confirmação final exigiria configurar um webhook, que ainda não está implementado. Consulte [a referência oficial da API](https://developers.facebook.com/documentation/business-messaging/whatsapp/reference/whatsapp-business-phone-number/message-api) e [regras dos modelos](https://developers.facebook.com/documentation/business-messaging/whatsapp/templates/overview).

As tentativas são registradas antes do envio; `failed` e `uncertain` exigem conferência manual. O status `submitted` para WhatsApp significa que a Meta aceitou a requisição, sem comprovar entrega. O sistema não repete envios automaticamente. O app mostra o estado por evento e destinatário em **Alertas → Ver entregas**; a web apresenta o mesmo registro. Apenas alertas reais criados depois de iniciar a central entram no fluxo de envio.
