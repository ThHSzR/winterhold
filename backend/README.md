# Firmware ESP8266

Firmware PlatformIO para **ESP8266/NodeMCU v2**: lê um DHT22 e um módulo ZMPT101B e publica as amostras por MQTT. O projeto conceitual menciona ESP32, mas o código atual importa `ESP8266WiFi.h` e usa o ambiente `nodemcuv2` no [PlatformIO](platformio.ini). Um ESP32 precisa de um port do firmware e de nova validação de pinos/ADC.

## Configuracao

Edite `include/config.h` localmente antes de compilar: preencha `WIFI_SSID` e `WIFI_PASSWORD`, o **IP do celular Android** em `MQTT_HOST`, a chave copiada em **Ajustes → ESP32 / MQTT** no campo `MQTT_PASSWORD` e um `DEVICE_ID` único. Mantenha `MQTT_PORT = 1883` e `MQTT_USER = winterhold`. Os valores versionados são apenas exemplos e não conectam a uma rede. Cada ESP8266 pode usar a mesma central com um `DEVICE_ID` diferente. O tópico é `winterhold/<DEVICE_ID>/leituras`; o APK recebe as publicações diretamente como endpoint MQTT 3.1.1 QoS 0. Não inclua senhas ou chaves reais em commits. Credenciais publicadas anteriormente permanecem no histórico Git: troque a senha do Wi-Fi e trate a chave MQTT como exposta. A versão atual não oferece rotação da chave MQTT na interface; a mudança exige planejamento para a central e os dispositivos.

Pinos padrao para NodeMCU v2: DHT22 no GPIO 4 (D2) e saida analogica do ZMPT101B no unico ADC, A0. Confirme a pinagem e a faixa aceita por A0 na sua placa antes de ligar os sensores. O firmware publica a cada 1 segundo. O DHT22 e lido a cada 2 segundos, limite recomendado pelo sensor; por isso, a leitura de temperatura e umidade se repete em publicacoes alternadas.

A tensao RMS e calculada a cada publicacao, usando uma janela de amostragem de 200 ms. Os 50/60 Hz correspondem a frequencia da forma de onda da rede, nao a frequencia necessaria de publicacao: essa janela cobre varios ciclos. A atualizacao de 1 Hz permite acompanhar variacoes lentas, mas pode nao registrar transitórios mais curtos que um segundo.

O campo `ZMPT_CALIBRATION` deve ser ajustado antes de usar a tensao medida. Compare a leitura RMS bruta do ADC com um instrumento de referencia em um ensaio supervisionado e use `tensao_referencia / rms_bruto_do_adc` como fator inicial. Com o fator em zero, `tensao_rms_v` e publicado como `null`.

## Compilar e gravar

Com PlatformIO instalado:

```sh
pio run -d backend
pio run -d backend -t upload
pio device monitor -d backend
```

No Linux, a NodeMCU conectada deve aparecer como `/dev/ttyUSB0` (ou outro numero). Se `pio device list` nao mostrar `/dev/ttyUSB*`, confira o cabo de dados e a conexao USB; nao use `/dev/ttyS0`, que normalmente e uma porta serial interna. Instale tambem as regras udev recomendadas pelo PlatformIO se houver erro de permissao.

O JSON publicado usa `device_id`, `timestamp` UTC, `temperatura_c`, `umidade_pct` e `tensao_rms_v`. Leituras invalidas aparecem como `null`; sem sincronizacao de horario, `timestamp` tambem e `null`.

O APK é a central e escuta MQTT diretamente na porta 1883, sem broker externo ou Termux. Desative **Ajustes → Usar leituras fictícias** para ver as leituras dos ESP8266 no modo real, no APK e na web `https://IP_DO_CELULAR:8792/`. Entre com usuário e senha cadastrados no celular e confira a impressão do certificado. A central sempre usa a hora de recebimento do Android, independentemente do `timestamp` enviado pelo firmware; valores nulos não geram alerta para aquela medida. Este firmware ainda não tem fila local: leituras feitas enquanto Wi-Fi ou MQTT estiver desconectado não são reenviadas.

Veja o [manual completo](../README.md) para regras de validação, ambientes, alertas, acesso web e diagnóstico.

Antes de ligar o modulo a rede eletrica, valide isolamento, alimentacao, protecao e faixa do sinal no ADC do ESP8266. A entrada A0 nao pode receber tensao acima do limite da placa.
