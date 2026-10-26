#include <Arduino.h>
#include <DHT.h>
#include <PubSubClient.h>
#include <ESP8266WiFi.h>
#include <math.h>
#include <time.h>

#include "config.h"

namespace {
constexpr uint8_t TIPO_DHT = DHT22;
constexpr unsigned long JANELA_AMOSTRAGEM_TENSAO_MS = 200;
constexpr unsigned long INTERVALO_RECONEXAO_WIFI_MS = 10000;
constexpr unsigned long INTERVALO_RECONEXAO_MQTT_MS = 5000;

DHT sensorTemperaturaUmidade(DHT_PIN, TIPO_DHT);
WiFiClient clienteRede;
PubSubClient clienteMqtt(clienteRede);
char topicoMqtt[96];
float temperatura = NAN;
float umidade = NAN;
unsigned long ultimaPublicacao = 0;
unsigned long ultimaLeituraDht = 0;
unsigned long ultimaTentativaWifi = 0;
unsigned long ultimaTentativaMqtt = 0;

float lerTensaoRms() {
  const unsigned long inicioAmostragem = millis();
  double soma = 0.0;
  double somaQuadrados = 0.0;
  uint32_t quantidadeAmostras = 0;

  while (millis() - inicioAmostragem < JANELA_AMOSTRAGEM_TENSAO_MS) {
    // O ADC do ESP8266 retorna valores brutos de 10 bits. A conversao final
    // para a tensao da rede e feita pelo fator de calibracao do ZMPT101B.
    const float tensaoAmostra = static_cast<float>(analogRead(ZMPT_PIN));
    soma += tensaoAmostra;
    somaQuadrados += tensaoAmostra * tensaoAmostra;
    ++quantidadeAmostras;
    delay(1);
  }

  if (quantidadeAmostras == 0 || ZMPT_CALIBRATION <= 0.0f) {
    return NAN;
  }

  const double media = soma / quantidadeAmostras;
  const double variancia = (somaQuadrados / quantidadeAmostras) - (media * media);
  const float tensaoRmsSensor = sqrtf(static_cast<float>(fmax(variancia, 0.0)));
  return tensaoRmsSensor * ZMPT_CALIBRATION;
}

void conectarWifi() {
  if (WiFi.status() == WL_CONNECTED ||
      millis() - ultimaTentativaWifi < INTERVALO_RECONEXAO_WIFI_MS) {
    return;
  }

  ultimaTentativaWifi = millis();
  Serial.printf("Conectando ao Wi-Fi %s\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void conectarMqtt() {
  if (WiFi.status() != WL_CONNECTED || clienteMqtt.connected() ||
      millis() - ultimaTentativaMqtt < INTERVALO_RECONEXAO_MQTT_MS) {
    return;
  }

  ultimaTentativaMqtt = millis();
  const bool conectado = MQTT_USER[0] == '\0'
                             ? clienteMqtt.connect(DEVICE_ID)
                             : clienteMqtt.connect(DEVICE_ID, MQTT_USER, MQTT_PASSWORD);
  if (conectado) {
    Serial.println("Conectado ao broker MQTT");
  } else {
    Serial.printf("Falha MQTT, estado: %d\n", clienteMqtt.state());
  }
}

void publicarLeitura() {
  const float tensao = lerTensaoRms();

  char temperaturaJson[16] = "null";
  char umidadeJson[16] = "null";
  char tensaoJson[16] = "null";
  if (isfinite(temperatura)) {
    snprintf(temperaturaJson, sizeof(temperaturaJson), "%.2f", temperatura);
  }
  if (isfinite(umidade)) {
    snprintf(umidadeJson, sizeof(umidadeJson), "%.2f", umidade);
  }
  if (isfinite(tensao)) {
    snprintf(tensaoJson, sizeof(tensaoJson), "%.2f", tensao);
  }

  char horarioJson[25] = "null";
  const time_t agora = time(nullptr);
  if (agora > 1700000000) {
    struct tm horarioUtc;
    gmtime_r(&agora, &horarioUtc);
    strftime(horarioJson, sizeof(horarioJson), "\"%Y-%m-%dT%H:%M:%SZ\"", &horarioUtc);
  }

  char mensagemJson[256];
  snprintf(mensagemJson, sizeof(mensagemJson),
           "{\"device_id\":\"%s\",\"timestamp\":%s,"
           "\"temperatura_c\":%s,\"umidade_pct\":%s,\"tensao_rms_v\":%s}",
           DEVICE_ID, horarioJson, temperaturaJson, umidadeJson, tensaoJson);

  if (clienteMqtt.connected() && clienteMqtt.publish(topicoMqtt, mensagemJson)) {
    Serial.printf("Publicado em %s: %s\n", topicoMqtt, mensagemJson);
  } else {
    Serial.println("Leitura nao enviada: MQTT desconectado ou falha no publish");
  }
}
}  // namespace

void setup() {
  Serial.begin(115200);
  sensorTemperaturaUmidade.begin();
  WiFi.mode(WIFI_STA);
  clienteMqtt.setServer(MQTT_HOST, MQTT_PORT);
  snprintf(topicoMqtt, sizeof(topicoMqtt), "%s/%s/leituras", MQTT_TOPIC_PREFIX, DEVICE_ID);
  configTime(0, 0, "pool.ntp.org", "time.google.com");

  ultimaTentativaWifi = millis() - INTERVALO_RECONEXAO_WIFI_MS;
  ultimaLeituraDht = millis() - DHT_READ_INTERVAL_MS;
  ultimaPublicacao = millis() - PUBLISH_INTERVAL_MS;
  Serial.println("Winterhold: iniciando coleta de sensores");
}

void loop() {
  conectarWifi();
  conectarMqtt();
  clienteMqtt.loop();

  if (millis() - ultimaLeituraDht >= DHT_READ_INTERVAL_MS) {
    ultimaLeituraDht = millis();
    temperatura = sensorTemperaturaUmidade.readTemperature();
    umidade = sensorTemperaturaUmidade.readHumidity();
  }

  if (millis() - ultimaPublicacao >= PUBLISH_INTERVAL_MS) {
    ultimaPublicacao = millis();
    publicarLeitura();
  }

  delay(10);
}
