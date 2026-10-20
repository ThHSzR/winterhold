#pragma once

constexpr char WIFI_SSID[] = "NOME_DA_REDE";
constexpr char WIFI_PASSWORD[] = "SENHA_DA_REDE";

constexpr char MQTT_HOST[] = "192.168.1.100";
constexpr uint16_t MQTT_PORT = 1883;
constexpr char MQTT_USER[] = "";
constexpr char MQTT_PASSWORD[] = "";

constexpr char DEVICE_ID[] = "sala-01";
constexpr char MQTT_TOPIC_PREFIX[] = "winterhold";

constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t ZMPT_PIN = 34;
constexpr float ZMPT_CALIBRATION = 0.0f;
constexpr unsigned long DHT_READ_INTERVAL_MS = 2000;
constexpr unsigned long PUBLISH_INTERVAL_MS = 1000;