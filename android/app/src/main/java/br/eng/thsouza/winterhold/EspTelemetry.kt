package br.eng.thsouza.winterhold

import org.json.JSONObject

/** MQTT topic/payload contract shared with backend/src/main.cpp. Receipt time is authoritative. */
internal object EspTelemetry {
    private val topicPattern = Regex("winterhold/([A-Za-z0-9_-]{1,40})/leituras")

    fun decode(topic: String, payload: ByteArray, receivedAt: Long = System.currentTimeMillis()): Reading {
        require(payload.size in 1..1024) { "Tamanho da amostra inválido" }
        val device = topicPattern.matchEntire(topic)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Tópico desconhecido")
        val json = JSONObject(payload.toString(Charsets.UTF_8))
        require(json.getString("device_id") == device) { "Identificador não corresponde ao tópico" }
        fun value(key: String, range: ClosedFloatingPointRange<Float>): Float? {
            require(json.has(key)) { "$key ausente" }
            if (json.isNull(key)) return null
            val raw = json.get(key)
            require(raw is Number) { "$key não é numérico" }
            val number = raw.toFloat()
            require(number.isFinite() && number in range) { "$key fora da faixa" }
            return number
        }
        val temperature = value("temperatura_c", -40f..85f)
        val humidity = value("umidade_pct", 0f..100f)
        val voltage = value("tensao_rms_v", 0f..300f)
        require(temperature != null || humidity != null || voltage != null) { "Amostra sem medidas válidas" }
        return Reading(receivedAt, temperature, humidity, voltage, device)
    }
}
