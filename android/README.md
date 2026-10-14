# Winterhold Android · protótipo visual

Aplicativo para **celular Android**, escrito em Kotlin com Jetpack Compose. O APK serve a interface web embutida e é a central do estado demo compartilhado. Esta fase valida navegação e fluxos antes da integração com ESP32 e canais externos. Leituras, eventos e perfis são fictícios e mantidos apenas em memória.

## Abrir e executar

1. Faça checkout da branch `APK` e abra a pasta `android/` como projeto no Android Studio.
2. Instale Android SDK 36 e use JDK 17 no Gradle.
3. Aguarde a sincronização e execute a configuração `app` em um emulador ou celular Android 8.0 (API 26) ou superior.
4. Pela linha de comando, a partir de `android/`: `./gradlew assembleDebug` (macOS/Linux) ou `gradlew.bat assembleDebug` (Windows). O APK de debug é criado em `app/build/outputs/apk/debug/app-debug.apk`.

O Gradle Wrapper está fixado em 8.13. O projeto usa Android Gradle Plugin 8.13.2, Kotlin 2.2.21 e Compose BOM 2026.02.01, compatível com o SDK 36 deste projeto. A primeira sincronização exige internet para obter o Gradle e as dependências. O build de debug da branch publica o APK como artefato do GitHub Actions quando passa.

## Telas e interações

| Tela | Visual e comportamento no demo |
| --- | --- |
| Início | Temperatura em destaque, curva recente, umidade, tensão, estado do ESP32/central e eventos recentes. |
| Histórico | Filtro por variável, gráfico e lista de amostras geradas localmente. |
| Alertas | Lista de eventos abertos e resolvidos; simulação manual por variável, resolução e prévia dos perfis/canais elegíveis. |
| Equipe | Perfis de manutenção, elétrica e gestão; ativação, inclusão e filtros por variável e canal. |
| Ajustes | Pausa e desconexão simuladas, limiares de exemplo, canais ativos para prévia e inventário das integrações futuras. |

### Roteiro rápido de demonstração

1. Abra **Início** e observe as leituras fictícias atualizadas a cada cinco segundos.
2. Em **Histórico**, alterne entre temperatura, umidade e tensão.
3. Em **Alertas**, crie um evento de tensão e toque em **Ver roteamento**: o perfil elétrico aparece apenas nos canais compatíveis.
4. Em **Equipe**, desative um perfil ou altere seus filtros e confira novamente o roteamento.
5. Em **Ajustes**, pause as leituras, simule perda de comunicação ou desative um canal. O estado aparece na tela inicial, e a perda de comunicação cria um evento fictício.
6. Em **Ajustes → Acesso web pelo aplicativo**, copie o endereço mostrado e abra em outro dispositivo da mesma rede ou Tailscale. Crie um alerta na web e observe-o no APK; uma alteração no APK aparece na web após a próxima atualização.

## Acesso web

Ao abrir o APK, ele inicia um servidor HTTP demo na porta `8790` e mostra os endereços com chave temporária em **Ajustes**. O browser carrega os arquivos web incluídos no APK e consulta `GET /api/v1/state`; ações são enviadas a `POST /api/v1/action`. Ambos usam o estado `DemoStore` do aplicativo. Não é necessário iniciar Node ou PM2 para essa modalidade. A prévia Node da pasta `web/` continua separada.

Use apenas redes privadas para esta fase HTTP. A chave muda quando o processo reinicia. O Android pode encerrar o processo em segundo plano, deixando a página indisponível; ainda não há serviço persistente, reinício após boot, HTTPS ou autenticação de produção.

## Limites desta fase

- Há somente o servidor HTTP demo da interface web. Não há comunicação MQTT com ESP32, SQLite, leitura de sensor ou comando do ar-condicionado.
- Não há permissões de SMS nem envio de SMS, e-mail ou WhatsApp. As chaves e o roteamento são apenas uma prévia visual.
- Dados demo e alterações não persistem após o processo do aplicativo terminar. A web mostra bateria, uso de memória, armazenamento e tempo ligado medidos pelo Android.
- Os limiares ajustados não avaliam o histórico nem disparam alertas automáticos; use o botão de simulação para testar o visual.
- A chave temporária protege apenas a API demo na rede privada; não é login nem proteção de produção. Evite cadastrar contatos pessoais no demo.

O contrato de dados preliminar e as decisões pendentes constam no [README principal](../README.md). O detalhamento das telas e das futuras fronteiras de integração está em [docs/PLANO_VISUAL_APK.md](../docs/PLANO_VISUAL_APK.md).
