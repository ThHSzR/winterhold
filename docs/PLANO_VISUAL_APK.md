# Plano visual do aplicativo Winterhold

**Fase:** demo visual para celular Android. **Plataforma:** Kotlin + Jetpack Compose. **Branch:** `APK`.

## Princípios da interface

- Uma sala monitorada na V1, com espaço para futuras salas.
- A condição do ambiente deve ser entendida primeiro: valor atual, horário, estado de comunicação e alerta.
- O selo **MODO DEMO · Dados fictícios** aparece em todas as telas; nenhum controle promete envio real.
- Fundo azul profundo, painéis em azul petróleo, ciano para leitura/estado normal, âmbar para atenção e coral para falha.
- Navegação inferior com Início, Histórico, Alertas e Equipe; Ajustes acessível pelo topo.
- Conteúdo em cartões e lista rolável para telas de celular, com rótulos textuais além das cores.

## Fluxo e estados

| Situação | Resposta visual no demo | Evolução planejada |
| --- | --- | --- |
| Leitura normal | Painel apresenta 3 grandezas e horário da amostra. | Recepção periódica de mensagens MQTT do ESP32 pelo celular. |
| Sem comunicação | Selo `SEM SINAL` e evento na lista; gráfico mantém última amostra. | Timeout de amostras e reconexão. |
| Limite violado | Evento aberto com grandeza, severidade e detalhes. | Avaliação com histerese e deduplicação. |
| Evento resolvido | Cartão marcado como resolvido, preservado no histórico. | Persistência e trilha de auditoria. |
| Perfil por função | Variáveis e canais selecionáveis; prévia mostra quem seria elegível. | Destinatários reais e política de entrega. |
| Canal inativo | Perfil continua visível; canal é omitido da prévia. | Estado operacional e falhas de envio. |

## Integrações representadas

| Integração | Representação atual | Implementação futura |
| --- | --- | --- |
| ESP32 → Android | Cartão de conectividade e amostras fictícias. | Cliente MQTT no celular, autenticação, validação e controle de perda de sinal. |
| Histórico | Gráfico e lista em memória. | SQLite, retenção e consulta por período. |
| Temperatura e umidade | Leitura fictícia DHT22. | Amostras reais, calibração e validação de sensor. |
| Tensão | Leitura RMS fictícia. | Medição isolada, calibrada e supervisionada. |
| SMS | Chave de roteamento e prévia. | Conferir suporte de telefonia, permissões e entrega. |
| E-mail | Chave de roteamento e prévia. | Provedor autenticado e processamento em segundo plano. |
| WhatsApp | Chave de roteamento e prévia. | Link assistido ou integração oficial; link não envia automaticamente. |

## Dados de demonstração

O `DemoStore` cria 36 amostras históricas e três eventos iniciais. Com simulação ativa, uma nova amostra fictícia é gerada a cada cinco segundos. Os contatos de exemplo representam manutenção, elétrica e gestão. **Simular um alerta** cria um evento para temperatura, umidade ou tensão; **Ver roteamento** calcula a interseção entre variável do evento, perfil ativo, canais do perfil e canais habilitados. Nada é enviado.

## Próxima etapa após aprovar o visual

1. Formalizar o contrato JSON, identificação da sala/dispositivo e estados de erro.
2. Separar `DemoStore` de uma camada de repositório real e persistir as medições.
3. Implementar o cliente MQTT no celular e testes de perda/retorno de comunicação.
4. Validar limiares com a equipe e implementar regras, histerese e deduplicação.
5. Integrar e testar cada canal de aviso separadamente, com confirmação de entrega e segurança dos segredos.

O PDF original da equipe permanece em [Projeto_de_Sistema_Embarcado.pdf](Projeto_de_Sistema_Embarcado.pdf).
