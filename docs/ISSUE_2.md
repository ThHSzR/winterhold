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

Os resultados finais e as limitações da validação física são registrados ao concluir os testes. A instrumentação usa receptor HTTP local para comprovar o fluxo de mensagens sem enviar a pessoas reais. O pareamento da conta WhatsApp e a confirmação de uma entrega externa exigem a participação do proprietário da conta.

## Limites

- A integração WhatsApp é não oficial; mudanças do protocolo podem exigir nova versão do APK e novo pareamento.
- O APK com runtime é ARM64 e tem tamanho maior que a versão anterior. Não há alegação de compatibilidade com todas as ABIs/versões Android.
- O Android pode interromper a central por parada forçada, falta de recursos, restrições de bateria ou rede. O serviço usa operação em primeiro plano, bloqueios de CPU/Wi-Fi e retomada após boot/desbloqueio. Parada forçada exige reabertura manual.
- Estados `failed`/`uncertain` exigem conferência antes de qualquer tentativa manual. Não há repetição automática de mensagens de estado incerto.

