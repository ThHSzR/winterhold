# Dependências e licenças de terceiros

A licença MIT na raiz cobre o código original do Winterhold. Bibliotecas, módulos, executáveis, certificados e ferramentas de terceiros mantêm suas próprias licenças e avisos. A licença do projeto não reclassifica todo o conteúdo do APK como MIT.

## Runtime WhatsApp incluído no APK ARM64

| Componente | Origem / referência |
| --- | --- |
| Node.js e bibliotecas Android utilizadas | Os pacotes, versões, URLs e SHA-256 estão em [android/runtime-lock.json](android/runtime-lock.json). |
| Receitas e patches dos pacotes Android | [termux/termux-packages](https://github.com/termux/termux-packages). As receitas dos pacotes do lock identificam fontes e patches; o aplicativo Termux não é necessário em execução. |
| Node.js upstream | [nodejs/node](https://github.com/nodejs/node), incluindo suas licenças e dependências. |
| Baileys 6.7.24 | [WhiskeySockets/Baileys](https://github.com/WhiskeySockets/Baileys), MIT. |
| libsignal | [WhiskeySockets/libsignal-node](https://github.com/WhiskeySockets/libsignal-node), GPL-3.0; o commit de origem efetivamente resolvido consta no lockfile npm. |
| Pino 9.14.0 | [pinojs/pino](https://github.com/pinojs/pino), MIT. |
| QRCode 1.5.4 | [soldair/node-qrcode](https://github.com/soldair/node-qrcode), MIT. |

O [package-lock.json](backend/whatsapp/package-lock.json) registra a árvore npm e as origens resolvidas. Os próprios pacotes contêm seus textos de licença. O build inclui os módulos em `bridge.zip` e copia os avisos encontrados nos pacotes nativos para `licenses/` dentro desse arquivo.

O script [prepare-runtime.py](android/prepare-runtime.py), o lock de pacotes e o lock npm registram como o runtime é preparado, incluindo os ajustes de nomes ELF necessários ao Android. Preserve as licenças e cumpra as condições dos respectivos componentes ao redistribuir um APK ou uma versão derivada, especialmente quando houver componentes sob GPL. A MIT do Winterhold não elimina essas condições.

## Android e firmware

As dependências Android são declaradas em [android/app/build.gradle.kts](android/app/build.gradle.kts), incluindo AndroidX e Jetpack Compose, com suas licenças próprias.

As bibliotecas de firmware estão em [backend/platformio.ini](backend/platformio.ini): DHT sensor library, Adafruit Unified Sensor e PubSubClient. Os frameworks Arduino/Espressif e bibliotecas utilizadas permanecem sujeitos aos avisos e licenças de suas distribuições.

Para reproduzir uma distribuição, use os manifests e locks do commit marcado na release, preservando os arquivos de licença dos pacotes efetivamente usados. Tamanho, origem do build e hash do APK são registrados nas notas da release.
