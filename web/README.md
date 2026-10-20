# Winterhold Web

A interface web acompanha o APK Android: ele serve os arquivos e a API em `http://IP_DO_CELULAR:8790/`. O celular recebe MQTT, avalia eventos, persiste as amostras e mostra sua própria saúde. Na página é possível alternar demo/ESP, cadastrar vários ambientes, escolher nome e ícone, editar mínimos e máximos de temperatura/umidade/tensão e selecionar a sala exibida. A chave MQTT só aparece nos Ajustes nativos do APK. A web local usa HTTP sem login, então use uma rede confiável.

## Abrir pelo APK

Instale e abra o [APK](../dist/winterhold-demo-debug.apk). Em **Ajustes → Acesso web pelo aplicativo**, copie o endereço e abra em outro dispositivo da mesma rede. Em **Ajustes → Ambientes**, cadastre o mesmo `DEVICE_ID` configurado em cada firmware, ou edite um ESP reconhecido automaticamente. Não é necessário Termux, Node, PM2 ou backend no homelab. A página tenta se reconectar automaticamente. A API oferece `GET /api/v1/state` e `POST /api/v1/action`; configurações e eventos são compartilhados com o aplicativo.

## Prévia independente

Com Node.js 20 ou superior, execute `cd web && npm start` e abra `http://127.0.0.1:8787/`. `HOST=0.0.0.0 PORT=8787 npm start` libera a prévia para a rede local. Essa versão produz apenas dados fictícios no navegador e **não** se conecta ao APK ou ao ESP32. Use `npm test` para verificar o modelo da prévia. A saúde do celular na prévia é fictícia; na página servida pelo APK vem do Android.

SMS, e-mail e WhatsApp seguem como roteamento visual, sem envios. O histórico no APK é salvo em arquivo local; a prévia Node não é persistida. Veja [android/README.md](../android/README.md) e [backend/README.md](../backend/README.md).
