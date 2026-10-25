# Firmware ESP32

Firmware PlatformIO para ler um DHT22 e um modulo ZMPT101B e publicar as amostras por MQTT.

## Configuracao

Edite `include/config.h` com o SSID e a senha do Wi-Fi, o **IP do celular Android** em `MQTT_HOST`, a porta `1883`, a chave copiada em **Ajustes → ESP32 / MQTT** no campo `MQTT_PASSWORD` e o identificador unico deste dispositivo. Mantenha `MQTT_USER` como `winterhold`. Cada ESP32 pode usar a mesma central, desde que tenha um `DEVICE_ID` diferente. O topico e gerado como `winterhold/<device_id>/leituras`; o APK recebe as publicacoes diretamente como endpoint MQTT 3.1.1 QoS 0. Nao inclua senhas ou chaves reais em commits.

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

O APK é a central e escuta MQTT diretamente na porta 1883, sem broker externo ou Termux. Desative **Ajustes → Usar leituras fictícias** para ver os ESP32 no modo real, no APK e na web `https://IP_DO_CELULAR:8792/`. Entre com usuário e senha cadastrados no celular e confira a impressão do certificado. A central sempre usa a hora de recebimento do Android, independentemente do `timestamp` enviado pelo firmware; valores nulos não geram alerta para aquela medida. Este firmware ainda não tem fila local: leituras feitas enquanto Wi-Fi ou MQTT estiver desconectado não são reenviadas.

Veja o [manual completo](../README.md) para regras de validação, ambientes, alertas, acesso web e diagnóstico.

Antes de ligar o modulo a rede eletrica, valide isolamento, alimentacao, protecao e faixa do sinal no ADC do ESP32. O GPIO nao pode receber tensao acima do limite da placa.
