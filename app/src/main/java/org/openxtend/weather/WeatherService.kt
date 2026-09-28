package org.openxtend.weather

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.openxtend.ble.IdoBleManager
import java.util.concurrent.TimeUnit

class WeatherService(private val context: Context) {

    companion object {
        private const val TAG = "WeatherService"
        private const val PREFS_NAME = "openxtend_weather_prefs"
        private const val KEY_WEATHER_ENABLED = "weather_enabled"
        private const val KEY_LAST_TEMP = "last_temp"
        private const val KEY_LAST_CONDITION = "last_condition"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun isWeatherEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WEATHER_ENABLED, true)
    }

    fun setWeatherEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply()
    }

    fun getLastWeatherSummary(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val temp = prefs.getInt(KEY_LAST_TEMP, -999)
        val cond = prefs.getString(KEY_LAST_CONDITION, null)
        return if (temp != -999 && cond != null) "$temp°C, $cond" else "Not synced yet"
    }

    data class WeatherData(
        val currentTempC: Int,
        val minTempC: Int,
        val maxTempC: Int,
        val weatherType: Int, // 1: Sunny, 2: Cloudy, 3: Overcast, 4: Rain, 5: Snow, 6: Storm, 7: Fog
        val conditionName: String,
        val humidity: Int,
        val cityName: String = "Delhi",
        val day1Type: Int = weatherType,
        val day1Max: Int = maxTempC,
        val day1Min: Int = minTempC,
        val day2Type: Int = weatherType,
        val day2Max: Int = maxTempC,
        val day2Min: Int = minTempC,
        val day3Type: Int = weatherType,
        val day3Max: Int = maxTempC,
        val day3Min: Int = minTempC
    )

    suspend fun fetchWeather(): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            var lat = 28.6139
            var lon = 77.2090
            var city = "Delhi"

            try {
                val locRequest = Request.Builder()
                    .url("https://get.geojs.io/v1/ip/geo.json")
                    .build()
                val locResponse = client.newCall(locRequest).execute()
                if (locResponse.isSuccessful) {
                    val locJson = JSONObject(locResponse.body?.string() ?: "")
                    lat = locJson.optDouble("latitude", lat)
                    lon = locJson.optDouble("longitude", lon)
                    val fetchedCity = locJson.optString("city", "")
                    if (fetchedCity.isNotBlank()) {
                        city = fetchedCity
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "IP Geolocation lookup skipped, using default coordinates: ${e.message}")
            }

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto"
            val weatherRequest = Request.Builder().url(weatherUrl).build()
            val response = client.newCall(weatherRequest).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Weather API returned HTTP ${response.code}"))
            }

            val json = JSONObject(response.body?.string() ?: "")
            val current = json.getJSONObject("current")
            val daily = json.optJSONObject("daily")

            val currentTemp = current.getDouble("temperature_2m").toInt()
            val humidity = current.optInt("relative_humidity_2m", 50)
            val wmoCode = current.optInt("weather_code", 0)

            var maxTemp = currentTemp + 3
            var minTemp = currentTemp - 3
            var d1Max = maxTemp; var d1Min = minTemp; var d1Type = mapWmoCode(wmoCode).first
            var d2Max = maxTemp; var d2Min = minTemp; var d2Type = mapWmoCode(wmoCode).first
            var d3Max = maxTemp; var d3Min = minTemp; var d3Type = mapWmoCode(wmoCode).first

            if (daily != null) {
                val maxArr = daily.optJSONArray("temperature_2m_max")
                val minArr = daily.optJSONArray("temperature_2m_min")
                val codeArr = daily.optJSONArray("weather_code")

                if (maxArr != null && maxArr.length() > 0) maxTemp = maxArr.getDouble(0).toInt()
                if (minArr != null && minArr.length() > 0) minTemp = minArr.getDouble(0).toInt()

                if (maxArr != null && maxArr.length() > 1) d1Max = maxArr.getDouble(1).toInt()
                if (minArr != null && minArr.length() > 1) d1Min = minArr.getDouble(1).toInt()
                if (codeArr != null && codeArr.length() > 1) d1Type = mapWmoCode(codeArr.optInt(1, wmoCode)).first

                if (maxArr != null && maxArr.length() > 2) d2Max = maxArr.getDouble(2).toInt()
                if (minArr != null && minArr.length() > 2) d2Min = minArr.getDouble(2).toInt()
                if (codeArr != null && codeArr.length() > 2) d2Type = mapWmoCode(codeArr.optInt(2, wmoCode)).first

                if (maxArr != null && maxArr.length() > 3) d3Max = maxArr.getDouble(3).toInt()
                if (minArr != null && minArr.length() > 3) d3Min = minArr.getDouble(3).toInt()
                if (codeArr != null && codeArr.length() > 3) d3Type = mapWmoCode(codeArr.optInt(3, wmoCode)).first
            }

            val (type, name) = mapWmoCode(wmoCode)

            val data = WeatherData(
                currentTempC = currentTemp,
                minTempC = minTemp,
                maxTempC = maxTemp,
                weatherType = type,
                conditionName = name,
                humidity = humidity,
                cityName = city,
                day1Type = d1Type,
                day1Max = d1Max,
                day1Min = d1Min,
                day2Type = d2Type,
                day2Max = d2Max,
                day2Min = d2Min,
                day3Type = d3Type,
                day3Max = d3Max,
                day3Min = d3Min
            )

            // Cache
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_LAST_TEMP, currentTemp)
                .putString(KEY_LAST_CONDITION, "$city: $name")
                .apply()

            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather, using fallback values", e)
            val fallback = WeatherData(
                currentTempC = 26,
                minTempC = 20,
                maxTempC = 30,
                weatherType = 1,
                conditionName = "Sunny / Clear",
                humidity = 55,
                cityName = "Delhi"
            )
            Result.success(fallback)
        }
    }

    private fun mapWmoCode(code: Int): Pair<Int, String> {
        return when (code) {
            0 -> 1 to "Sunny / Clear"
            1, 2 -> 2 to "Partly Cloudy"
            3 -> 3 to "Overcast"
            45, 48 -> 7 to "Foggy"
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> 4 to "Rain"
            71, 73, 75, 77, 85, 86 -> 5 to "Snow"
            95, 96, 99 -> 6 to "Thunderstorm"
            else -> 1 to "Clear"
        }
    }

    suspend fun syncWeatherToWatch(bleManager: IdoBleManager): Result<String> {
        return withContext(Dispatchers.IO) {
            val result = fetchWeather()
            val weather = result.getOrNull()
            if (weather != null) {
                bleManager.pushWeather(
                    tempC = weather.currentTempC,
                    maxC = weather.maxTempC,
                    minC = weather.minTempC,
                    weatherType = weather.weatherType,
                    humidity = weather.humidity,
                    cityName = weather.cityName,
                    day1Type = weather.day1Type,
                    day1Max = weather.day1Max,
                    day1Min = weather.day1Min,
                    day2Type = weather.day2Type,
                    day2Max = weather.day2Max,
                    day2Min = weather.day2Min,
                    day3Type = weather.day3Type,
                    day3Max = weather.day3Max,
                    day3Min = weather.day3Min
                )
                Result.success("${weather.cityName}: ${weather.currentTempC}°C, ${weather.conditionName}")
            } else {
                Result.failure(Exception("Failed to fetch weather"))
            }
        }
    }
}
