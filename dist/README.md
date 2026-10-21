# APK Winterhold

[Baixar APK da release v0.7.1](https://github.com/ThHSzR/winterhold/releases/tag/v0.7.1) · [Arquivo direto na main](winterhold-demo-debug.apk)

Versão Android **0.7.1**, código **8**, com runtime **ARM64**. Corrige a perda de preenchimento nos formulários web durante a atualização automática dos dados, preservando valores, senha, foco e posição do cursor.

- Código usado na compilação: `97f4846585b70ed73f159ee24758443a3a4ec960`.
- [Build e testes GitHub Actions](https://github.com/ThHSzR/winterhold/actions/runs/39529924239).
- Tamanho: 61,195,394 bytes (58.36 MiB).
- SHA-256: `421a7e13fc12cc4610874139914f35392168d388a397d2c46ddcc22dd713168e`.
- Aplicativo de debug com a chave pública de desenvolvimento do repositório. Para produção, use assinatura privada própria.

## Validação desta versão

- Oito testes web e checagem de sintaxe.
- Verificação de DOM com atualizações repetidas, senha/cursor/foco, estado visível atualizado, falha e sucesso de gravação e leitura demo.
- Build e testes unitários Android, instrumentação e ciclo de vida em emuladores API 30 e 35.
- Atualização instalada no Galaxy A30, Android 11; versão/código conferidos e assets web corrigidos verificados pela central em execução.

Depois de instalar, recarregue a página web. A atualização do processo pode exigir novo login. Atualizar com a mesma assinatura preserva os dados; desinstalar ou limpar dados os remove.

## Recursos e pontos em acompanhamento

Inclui central MQTT, HTTPS com usuário/senha, demonstração e runtime Node/Baileys privado, sem exigir o aplicativo Termux. A sessão WhatsApp foi observada conectada no A30, mas o recebimento real e a resolução do identificador brasileiro do destinatário continuam em acompanhamento. O SMTP respondeu 535 na autenticação e precisa da configuração/credencial apropriada do provedor. SMS foi confirmado pelo proprietário em um A73 com chip.

O código original está sob [MIT](../LICENSE). Os componentes incluídos no APK mantêm suas [licenças de terceiros](../THIRD_PARTY_NOTICES.md). Consulte o [manual completo](../README.md) e o [registro da issue #2](../docs/ISSUE_2.md).
