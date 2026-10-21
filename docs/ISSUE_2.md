# Issue #2 — central integrada

## Implementação

- O APK ARM64 inclui Node 24.18.0 e Baileys 6.7.24 com dependências npm fixadas em lockfile. O Node vem dos pacotes Android do projeto Termux, fixados por SHA-256 em `android/runtime-lock.json`. **O aplicativo Termux não é usado em execução.** As dependências ELF e suas referências de versão são relocalizadas, sem RUNPATH para outro aplicativo, e instaladas pelo Android no diretório nativo assinado do APK.
- O aplicativo extrai os módulos para seu diretório privado, inicia o processo com ambiente próprio, CAs e token aleatório, supervisiona sua recuperação e o encerra junto da central. Um watchdog encerra o filho se o processo Android morrer. Atualizar o APK prepara os módulos novamente, preservando `files/whatsapp-session`. Não há download/execução de binários no armazenamento gravável. A política Android está documentada em https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission.
- O Baileys tenta reconectar com espera progressiva, mantém a sessão privada e expõe estado/QR somente com token local. A central repassa estado/QR à web autenticada. O APK também renderiza o QR diretamente. Novo pareamento exige ação explícita e arquiva a sessão anterior.
- Texto é o único tipo de mensagem usado; nenhum addon npm nativo, FFmpeg ou Sharp é necessário. O processo escuta somente loopback na porta 8791. Outras ABIs recebem estado explícito de runtime indisponível e podem configurar a ponte HTTPS externa.
- Usuários/senhas substituem chaves na web. O APK é o único local de administração de contas e revogação de sessões. Senhas: PBKDF2-HMAC-SHA256, 210.000 iterações, salt aleatório individual. Sessões aleatórias duram oito horas, ficam na memória e são revogadas ao editar/desativar/excluir/redefinir senha. Há limitação de tentativas por origem e global.
- HTTPS 8792 usa chave EC P-256 não exportável do Android Keystore e certificado local com SHA-256 mostrado no APK. O usuário deve conferir a impressão antes de confiar. HTTP 8790 permite API/login somente por localhost (diagnóstico ADB); não confia em `X-Forwarded-*`. Cookies são HttpOnly, SameSite=Strict e Secure no HTTPS. POST exige cabeçalho próprio e valida Origin. Não existem rotas web de gestão de usuários.
- Na migração, contas antigas recebem login `usuarioN` e podem usar a chave anterior como senha por sete dias. O texto da chave é removido e convertido em hash. O proprietário pode redefinir a senha pelo APK a qualquer momento.
- Leituras fictícias automáticas/manuais usam a mesma avaliação por ambiente, variável e lado da faixa das leituras reais. Abrem ocorrência na violação, notificam normalização e não repetem enquanto a mesma condição persistir. Confirmar visualmente o evento não rearma o envio. Estado restaurado mantém a deduplicação. Mensagens externas são marcadas `[SIMULADO]`; o teste explícito usa `[TESTE SIMULADO]`.
- A ativação do demo exige confirmação de que mensagens reais poderão ser enviadas. O histórico de entrega mostra destino e estado, inclusive falta de configuração/permissão. Reserva antes da chamada externa evita reenvio automático; `submitted` é submissão, não confirmação de entrega ao destinatário.

## Compilação

1. Em `backend/whatsapp`: `npm ci --omit=optional --ignore-scripts` com Node 22+.
2. Instale `lief==0.17.6` no Python e execute `python android/prepare-runtime.py` na raiz.
3. Em `android`: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` (JDK 17/SDK 36).

O arquivo `android/debug.keystore` é uma chave **pública de desenvolvimento**, usada apenas para manter atualizações do APK de debug compatíveis entre compilações. Uma distribuição de produção deve usar assinatura privada própria. As bibliotecas e licenças de terceiros acompanham o pacote de runtime; o lockfile registra a origem das dependências (incluindo libsignal GPL-3.0). O pacote não usa instalação npm no telefone.

## Validação

- **14 testes unitários Android**: limites próprios dos cinco ambientes, atualização automática/manual, min/max, normalização, deduplicação, reconhecimento e reinício sem rearmar a mesma violação, persistência e MQTT.
- **8 testes web**: conexão por sessão, reconexão, ambientes, escape de conteúdo e servidor da prévia.
- **Instrumentação Android 11 e 15 em CI**: login, HTTPS com certificado fixado, logout, revogação, redefinição de senha, limitação de tentativas, ausência de administração web, MQTT e persistência. Teste de cinco ambientes passa pelo endpoint HTTP real da central e registra dez ou mais mensagens simuladas/normalizações num receptor HTTP local, sem enviar a pessoas reais. Canais sem credenciais/permissões registram bloqueios.
- **A30 físico (SM-A305GT, Android 11/API 30, ARM64)**: instalação, Node privado do APK chegando ao QR, HTTPS com verificação de impressão do certificado via ADB e pela LAN, bloqueio da API HTTP na LAN (403), testes de segurança/entrega, parada/retomada da central e recuperação após interrupção do processo passaram. Um reboot real iniciou a central e o Node pelo BootReceiver, sem reabrir a atividade; as credenciais web e o diretório privado da ponte permaneceram disponíveis. O modo voltou a real com demo pausado.
- APK de debug: **61.192.374 bytes (58,36 MiB)**, dez ELFs ARM64. Módulos extraídos ocupam aproximadamente 47,3 MiB, além das bibliotecas nativas e do APK. Nenhum addon npm nativo está presente. O pacote foi verificado como ZIP íntegro.

**Situação no aceite inicial:** o proprietário adiou o pareamento WhatsApp. O runtime e o fluxo até o QR foram verificados; os envios automáticos foram comprovados com receptor local. O aceite de recebimento real e recuperação da sessão permaneceu pendente.

**Diagnóstico posterior no A30:** a sessão WhatsApp foi observada conectada. Uma consulta `onWhatsApp` confirmou que o destino configurado com nono dígito corresponde a um identificador canônico sem esse dígito, enquanto o envio atual monta o JID diretamente. As tentativas estavam registradas como `submitted`, sem comprovação de recebimento; corrigir essa resolução permanece em acompanhamento. O servidor SMTP externo respondeu `535` na autenticação. O proprietário confirmou SMS em um A73 com chip. A versão 0.7.1 corrige os formulários web e não altera esses caminhos de entrega.

## Limites

- A integração WhatsApp é não oficial; mudanças do protocolo podem exigir nova versão do APK e novo pareamento.
- O APK com runtime é ARM64 e tem tamanho maior que a versão anterior. Não há alegação de compatibilidade com todas as ABIs/versões Android.
- O Android pode interromper a central por parada forçada, falta de recursos, restrições de bateria ou rede. O serviço usa operação em primeiro plano, bloqueios de CPU/Wi-Fi e retomada após boot/desbloqueio. Parada forçada exige reabertura manual.
- Estados `failed`/`uncertain` exigem conferência antes de qualquer tentativa manual. Não há repetição automática de mensagens de estado incerto.
