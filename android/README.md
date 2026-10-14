# Winterhold Android · protótipo visual

Aplicativo para **celular Android**, escrito em Kotlin com Jetpack Compose. O APK serve a interface web embutida e é a central do estado demo compartilhado. A central funciona em um serviço Android, com persistência local dos dados fictícios. Esta fase acadêmica usa somente rede local, sem MQTT, nuvem ou equipamentos reais.

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
6. Em **Ajustes → Acesso web pelo aplicativo**, copie o endereço mostrado e abra em outro dispositivo da mesma rede local. Crie um alerta na web e observe-o no APK; uma alteração no APK aparece na web após a próxima atualização.

## Acesso web

Ao abrir o APK, a `MainActivity` inicia o `CentralService`. O serviço inicia a central, restaura o estado demo e serve HTTP em `0.0.0.0:8790`. Acesse diretamente `http://IP_DO_CELULAR:8790/`, sem chave ou login. Em **Ajustes** e na notificação da central aparecem os endereços disponíveis; eles são atualizados quando a rede muda. Se o IP mudar, abra o novo endereço no navegador. Uma reserva DHCP no roteador pode manter o endereço estável.

O navegador carrega os arquivos web incluídos no APK e consulta `GET /api/v1/state`; ações são enviadas a `POST /api/v1/action`. Ambos usam o `DemoStore`. A página tenta reconectar a cada cinco segundos após perder acesso. Não é necessário iniciar Termux, Node ou PM2. A prévia Node da pasta `web/` continua separada. Use a rede local do projeto; não há túnel, HTTPS ou autenticação nesta entrega.

## Execução contínua e recuperação

- A instalação e primeira abertura exigem a preparação inicial do Android. Após a primeira abertura, fechar a tela ou remover o app dos recentes não para a central (`stopWithTask=false`). O serviço tem notificação permanente e `START_STICKY`, permitindo ao sistema recriá-lo quando possível.
- O serviço mantém CPU e Wi-Fi ativos para a demo com tela apagada. Isso consome bateria; para a bancada, mantenha o aparelho alimentado. Os bloqueios não eliminam as restrições de Doze ou da Samsung. Se houver interrupções, use **Ajustes → Configurações de bateria** e permita operação sem restrições para o Winterhold na preparação inicial.
- `BOOT_COMPLETED` retoma a central após reiniciar e desbloquear o dispositivo. `MY_PACKAGE_REPLACED` retoma após atualização do APK. Nenhuma tela é aberta automaticamente. Android 14+ usa serviço `specialUse`, declarado para a central HTTP local acadêmica; não usa o tipo `dataSync`, que tem limites diferentes.
- **Parar central**, em Ajustes ou na notificação, fecha o servidor e desativa a retomada automática. **Retomar central** ou abrir o aplicativo novamente reativa ambos. Pausar as *leituras demo* não desliga o servidor web.
- **Forçar parada** nos ajustes do Android impede recuperação automática até nova abertura do app. Serviço em primeiro plano não é garantia de disponibilidade contra desligamento, falta de rede, restrições do fabricante ou ação explícita do usuário.
- Leituras (últimas 60), eventos (até 500 na recuperação), contatos, limiares, canais e estados da simulação são salvos no arquivo privado `demo-state.json`, usando escrita atômica. Alterações feitas pelo APK ou pela API são enfileiradas para gravação, sem banco SQLite. O estado salvo é restaurado ao recriar o processo. Não são geradas amostras retroativas durante a indisponibilidade.

## Validação

`./gradlew :app:testDebugUnitTest :app:assembleDebug` verifica recuperação do estado, limites inválidos e retenção. `./gradlew :app:connectedDebugAndroidTest` verifica HTTP sem chave, ações compartilhadas, persistência e fechamento da tela. O workflow executa também recuperação após matar o processo e após reboot em emuladores Android 11 e 15.

Após instalar e abrir no A30:

```sh
pm path br.eng.thsouza.winterhold
am start -W -n br.eng.thsouza.winterhold/.MainActivity
curl -i http://127.0.0.1:8790/healthz
curl -i http://127.0.0.1:8790/api/v1/state
```

No navegador de outro computador da rede, abra `http://IP_DO_A30:8790/`. Confira com a tela do app fechada e apagada; altere um limiar, reinicie/desbloqueie o celular e confira a retomada e o valor salvo. No Termux sem privilégios, `pm path` pode falhar ao consultar o serviço de pacotes; a instalação inicial ainda precisa do instalador Android ou de um ADB já autorizado. Não reinicie o homelab para testar sem planejar a interrupção dos outros serviços.

## Limites desta fase

- Há somente o servidor HTTP demo da interface web. Não há comunicação MQTT com ESP32, SQLite, leitura de sensor ou comando do ar-condicionado.
- Não há permissões de SMS nem envio de SMS, e-mail ou WhatsApp. As chaves e o roteamento são apenas uma prévia visual.
- Dados demo persistem localmente; desinstalar ou limpar os dados do aplicativo os remove. A web mostra bateria, uso de memória, armazenamento e tempo ligado medidos pelo Android.
- Os limiares ajustados não avaliam o histórico nem disparam alertas automáticos; use o botão de simulação para testar o visual.
- A API acadêmica local não exige autenticação. Contatos e roteamento continuam fictícios, sem envio externo.

O contrato de dados preliminar e as decisões pendentes constam no [README principal](../README.md). O detalhamento das telas e das futuras fronteiras de integração está em [docs/PLANO_VISUAL_APK.md](../docs/PLANO_VISUAL_APK.md).
