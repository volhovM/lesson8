package me.volhovm.mweather

import java.util.Locale

import android.app.IntentService
import android.content.Intent
import android.os.{Bundle, Handler, ResultReceiver}
import android.util.Log

object WeatherLoadService {
  val CITY = "city"
  val SERVICE_NAME = "WeatherLoadService"
  val STATUS_RUNNING = 0
  val STATUS_FINISHED = 1
  val STATUS_ERROR = 2

  val SERVICE_MODE = "service_mode"
  val CITY_MODE = 0
  val COORD_MODE = 1
  val GLOBAL_REFRESH_MODE = 2

  val LATITUDE = "latitude"
  val LONGITUDE = "longitude"
  val OLD_CITY = "old_name"
  val NEW_CITY = "new_city"
  val FRAGMENT_ID = "fragment_id"
  val RECEIVER = "receiver"
}

class WeatherLoadService extends IntentService("WeatherLoadService") {
  override def onHandleIntent(intent: Intent): Unit = {
    import me.volhovm.mweather.WeatherLoadService._
    Log.d(SERVICE_NAME, "started service")
    val receiver: ResultReceiver = intent.getParcelableExtra(RECEIVER)
    val mode = intent.getIntExtra(SERVICE_MODE, -1)
    receiver.send(STATUS_RUNNING, Bundle.EMPTY)
    try {
      mode match {
        case CITY_MODE =>
          val city: String = intent.getStringExtra(CITY)
          if (city == null | city.length < 1) throw new IllegalArgumentException("Wrong city name in intent or null")
          val w = Global.getCurrentApi.getForecastForCity(city, Locale.getDefault.getLanguage, 15000)
          if (w.length > 0) {
            getContentResolver.delete(WeatherProvider.MAIN_CONTENT_URI, DatabaseHelper.WEATHER_CITY + "='" + w(0).city + "'", null)
            w.foreach((a: Weather) => getContentResolver.insert(WeatherProvider.MAIN_CONTENT_URI, a.getValues))
            val bundle: Bundle = new Bundle()
            bundle.putString(OLD_CITY, city)
            bundle.putString(NEW_CITY, w(0).city)
            bundle.putInt(SERVICE_MODE, CITY_MODE)
            bundle.putInt(FRAGMENT_ID, intent.getIntExtra(FRAGMENT_ID, -1))
            receiver.send(STATUS_FINISHED, bundle)
          }
        case COORD_MODE =>
          val latitude = intent.getDoubleExtra(LATITUDE, 0)
          val longitude = intent.getDoubleExtra(LONGITUDE, 0)
          val w = Global.getCurrentApi.getForecastForCoordinates(latitude, longitude, Locale.getDefault.getLanguage, 15000)
          if (w.length > 0) {
            getContentResolver.delete(WeatherProvider.MAIN_CONTENT_URI, DatabaseHelper.WEATHER_CITY + "='" + w(0).city + "'", null)
            w.foreach((a: Weather) => getContentResolver.insert(WeatherProvider.MAIN_CONTENT_URI, a.getValues))
            val bundle: Bundle = new Bundle()
            bundle.putInt(SERVICE_MODE, COORD_MODE)
            bundle.putString(NEW_CITY, w(0).city)
            receiver.send(STATUS_FINISHED, bundle)
          }
      }
    } catch {
      case a: Throwable =>
        Log.e("WeaterLoadService", "Exception while loading weather: " + a.toString)
        val bundle: Bundle = new Bundle()
        bundle.putString(Intent.EXTRA_TEXT, a.toString)
        receiver.send(STATUS_ERROR, bundle)
    }
  }
}

trait Receiver {
  def onReceiveResult(resCode: Int, resData: Bundle): Unit
}

class WeatherLoadReceiver(handler: Handler) extends ResultReceiver(handler) {
  private var mReceiver: Receiver = null
  def setReceiver(r: Receiver) = mReceiver = r
  override def onReceiveResult(resCode: Int, resData: Bundle) = if (mReceiver != null) mReceiver.onReceiveResult(resCode, resData)
}
