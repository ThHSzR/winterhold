#pragma once

constexpr char WIFI_SSID[] = "Jerry_IOT";
constexpr char WIFI_PASSWORD[] = "minhavida12";

constexpr char MQTT_HOST[] = "10.150.235.246";
constexpr uint16_t MQTT_PORT = 1883;
constexpr char MQTT_USER[] = "winterhold";
constexpr char MQTT_PASSWORD[] = "74977fbc93ce4560e4db817f8b7a46d91d73";

constexpr char DEVICE_ID[] = "sala-01";
constexpr char MQTT_TOPIC_PREFIX[] = "winterhold";

constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t ZMPT_PIN = A0;
constexpr float ZMPT_CALIBRATION = 0.0f;
constexpr unsigned long DHT_READ_INTERVAL_MS = 2000;
constexpr unsigned long PUBLISH_INTERVAL_MS = 1000;
