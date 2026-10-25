# WhatsApp — Baileys integrado e ponte externa opcional

O APK ARM64 da versão `0.7-integrated-central` inclui Node 24.18.0 e Baileys 6.7.24. O aplicativo prepara os módulos privados, inicia e supervisiona o processo e mostra o QR no APK e na web autenticada. **Termux, PM2 e um servidor separado não são requisitos para esse APK.**

O runtime foi verificado no Galaxy A30 e a sessão foi posteriormente observada conectada. O recebimento real continua em validação: foi diagnosticada uma diferença entre o número brasileiro com nono dígito e o identificador canônico retornado pelo WhatsApp, ainda não resolvida pelo envio atual. Veja o [registro de validação](../../docs/ISSUE_2.md).

## Uso integrado no Android

1. Instale o APK ARM64 completo e abra a central.
2. Em Ajustes → WhatsApp, confira o estado do runtime e da conexão.
3. No telefone da conta remetente, abra a área de aparelhos conectados e leia o QR mostrado pelo APK ou pela web.
4. Aguarde `connected`.
5. Configure destinos e filtros em Equipe e habilite o canal WhatsApp.
6. Faça um teste explícito e confira o recebimento no destino.

A URL inicial é `http://127.0.0.1:8791`, com token aleatório guardado pelo APK. Não é necessário informar manualmente um token Meta. O processo escuta apenas no loopback do aparelho. A web consulta conexão/QR pelo proxy autenticado da central, sem receber o token da ponte.

Módulos ficam em `files/bridge-runtime`; autenticação e reservas ficam em `files/whatsapp-session`. Atualizar módulos não remove esse diretório de sessão. **Novo pareamento** encerra a conexão e arquiva a autenticação anterior antes de solicitar outro QR. Limpar dados ou desinstalar remove o armazenamento privado.

Em uma ABI sem runtime preparado, use a alternativa externa com um APK compatível. O artefato ARM64 não possui garantia de instalação em outras arquiteturas.

## Hospedagem externa opcional

O servidor deste diretório também pode executar em outro host. A central Android continua recebendo MQTT, avaliando limites e servindo o painel; a ponte externa recebe apenas mensagens de saída.

Use Node 22+ para acompanhar o ambiente de preparação utilizado pelo projeto. Instale as dependências fixadas no lockfile:

```sh
npm ci --omit=optional --ignore-scripts
```

Configure o ambiente do processo:

| Variável | Finalidade / padrão |
| --- | --- |
| `WINTERHOLD_BRIDGE_TOKEN` | Obrigatória; ao menos 32 caracteres aleatórios. |
| `WINTERHOLD_BRIDGE_HOST` | Endereço de escuta, padrão `127.0.0.1`. |
| `WINTERHOLD_BRIDGE_PORT` | Porta TCP, padrão `8791`. |
| `WINTERHOLD_BRIDGE_DATA` | Diretório privado persistente, padrão `./private-session`. |

Inicie com `npm start` nesse ambiente. Preserve os arquivos de sessão e não os envie ao Git. O diretório pode conter autenticação WhatsApp e IDs de tentativas.

Para acesso do APK a outro host, disponibilize a ponte por HTTPS, com certificado válido, e restrinja o acesso na infraestrutura. O servidor Node implementa HTTP; a terminação HTTPS externa deve ser configurada no ambiente de hospedagem. O host padrão de loopback não fica acessível diretamente por outra máquina.

Em Ajustes → WhatsApp, configure a URL base HTTPS e o mesmo token. A URL não deve conter `/send`, query, fragmento ou usuário embutido. Token vazio no formulário mantém o anterior. Um supervisor de processo pode ser usado nessa hospedagem; o ciclo de vida do runtime integrado já pertence ao APK.

## API privada

Todas as rotas exigem `Authorization: Bearer TOKEN_DA_PONTE`.

| Método | Rota | Comportamento |
| --- | --- | --- |
| GET | `/status` | Retorna `connection` e QR em data URL PNG, quando disponível. |
| POST | `/relink` | Solicita novo pareamento; retorna `202` com `pairing_requested`. |
| POST | `/send` | Recebe ID único, destinatário e texto. |

Corpo de envio ilustrativo:

```json
{
  "id": "evento:contato:WHATSAPP",
  "to": "+5511999999999",
  "message": "Winterhold | [TESTE SIMULADO] Mensagem de bancada"
}
```

O ID aceita 1–180 caracteres de letras/números/underscore, `:` e `-`; o telefone usa E.164; a mensagem tem de 1 a 1500 caracteres. O corpo acumulado é limitado a 8192 caracteres. Há apenas envio de texto para destinatário individual.

- `202 submitted`: a chamada de submissão foi concluída.
- `202 already_reserved`: o ID já consta do registro; isso não comprova entrega de uma tentativa anterior.
- `401`: token inválido.
- `400`: campos inválidos.
- `503`: sessão desconectada ou falha incerta de envio.

Os IDs são reservados em disco antes do envio. Não há repetição automática de uma tentativa incerta. Confira o destino antes de um novo teste para evitar duplicidade.

## Reconexão e limites

O estado pode ser `starting`, `pairing`, `connected`, `disconnected`, `reconnecting` ou `logged_out`. Fechamentos recuperáveis usam espera progressiva de reconexão; `logged_out` requer novo pareamento. O runtime iniciado não significa que a conta esteja vinculada.

Baileys é uma integração não oficial. Mudanças do protocolo ou restrições da conta podem exigir atualização e novo pareamento. `submitted` não comprova entrega ou leitura final.

Consulte o [manual principal](../../README.md) para regras de alertas, identificação de mensagens simuladas, configuração de destinatários, segurança e diagnóstico por ADB.
