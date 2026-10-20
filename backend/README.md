# Firmware ESP32

Firmware PlatformIO para ler um DHT22 e um modulo ZMPT101B e publicar as amostras por MQTT.

## Configuracao

Edite `include/config.h` com o SSID e a senha do Wi-Fi, o IP e a porta do broker MQTT no tablet e o identificador unico deste dispositivo. Se o broker exigir autenticacao, preencha `MQTT_USER` e `MQTT_PASSWORD`. Cada ESP32 pode usar o mesmo firmware e broker, desde que tenha um `DEVICE_ID` diferente. O topico e gerado como `winterhold/<device_id>/leituras`; o tablet pode assinar `winterhold/+/leituras` para receber todos os pontos.

Pinos padrao: DHT22 no GPIO 4 e saida analogica do ZMPT101B no GPIO 34. Confirme a pinagem da sua placa antes de ligar os sensores. O firmware publica a cada 1 segundo. O DHT22 e lido a cada 2 segundos, limite recomendado pelo sensor; por isso, a leitura de temperatura e umidade se repete em publicacoes alternadas.

A tensao RMS e calculada a cada publicacao, usando uma janela de amostragem de 200 ms. Os 50/60 Hz correspondem a frequencia da forma de onda da rede, nao a frequencia necessaria de publicacao: essa janela cobre varios ciclos. A atualizacao de 1 Hz permite acompanhar variacoes lentas, mas pode nao registrar transitórios mais curtos que um segundo.

O campo `ZMPT_CALIBRATION` deve ser ajustado antes de usar a tensao medida. Compare a leitura RMS do sensor com um instrumento de referencia em um ensaio supervisionado e use `tensao_referencia / tensao_rms_lida_pelo_adc` como fator inicial. Com o fator em zero, `tensao_rms_v` e publicado como `null`.

## Compilar e gravar

Com PlatformIO instalado:

```sh
pio run -d backend
pio run -d backend -t upload
pio device monitor -d backend
```

O JSON publicado usa `device_id`, `timestamp` UTC, `temperatura_c`, `umidade_pct` e `tensao_rms_v`. Leituras invalidas aparecem como `null`; sem sincronizacao de horario, `timestamp` tambem e `null`.

Para receber as mensagens, o tablet precisa executar ou acessar um broker MQTT local e assinar o topico configurado. Este firmware ainda nao tem fila local: leituras feitas enquanto Wi-Fi ou MQTT estiver desconectado nao sao reenviadas.

Antes de ligar o modulo a rede eletrica, valide isolamento, alimentacao, protecao e faixa do sinal no ADC do ESP32. O GPIO nao pode receber tensao acima do limite da placa.