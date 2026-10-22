package de.quaelgeist

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.IntentCompat
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ortserkennung ohne Google-Dienste, nach dem Vorbild der FH-Stempelkarte. Läuft nur, solange
 * eine Orts-Erinnerung wartet und ihr Zeitfenster gilt; mehrere Wege, der erste Hinweis zählt:
 * - Geofence je Ort über `LocationManager.addProximityAlert` (Framework).
 * - Standort-Updates vom besten Anbieter (Fused, Netzwerk, GPS) im kürzesten Prüfintervall.
 * - WLAN-Meldungen über `registerNetworkCallback(…, PendingIntent)`, Vergleich der SSID.
 * - Prüf-Alarm im Prüfintervall: frischer Standort, sonst eine neue Messung; dazu WLAN.
 * Ob daraus Ankommen/Verlassen wird, entscheidet [GeoLogic]. Alles landet im [GeoLog].
 */
object GeoWatch {

    const val ACTION_TICK = "de.quaelgeist.GEO_TICK"
    const val ACTION_PROXIMITY = "de.quaelgeist.GEO_PROXIMITY"
    const val ACTION_LOCATION = "de.quaelgeist.GEO_LOCATION"
    const val ACTION_WIFI = "de.quaelgeist.GEO_WIFI"
    const val EXTRA_PLACE = "place"

    private const val CURRENT_LOCATION_TIMEOUT_MS = 25_000L

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun intent(ctx: Context, action: String, path: String) =
        Intent(ctx, GeoReceiver::class.java).setAction(action)
            .setData(Uri.parse("quaelgeist://geo/$path"))

    /** Veränderbar: Das System hängt Extras an (Eintritt, Standort, Netzwerk). */
    private fun mutablePi(ctx: Context, action: String, path: String, extra: Long? = null) =
        PendingIntent.getBroadcast(
            ctx, 0, intent(ctx, action, path).apply { extra?.let { putExtra(EXTRA_PLACE, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )

    private fun proximityPi(ctx: Context, placeId: Long) =
        mutablePi(ctx, ACTION_PROXIMITY, "prox/$placeId", placeId)

    private fun locationPi(ctx: Context) = mutablePi(ctx, ACTION_LOCATION, "location")
    private fun wifiPi(ctx: Context) = mutablePi(ctx, ACTION_WIFI, "wifi")

    private fun tickPi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, intent(ctx, ACTION_TICK, "tick"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun hasFineLocation(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(ctx: Context) =
        Build.VERSION.SDK_INT < 29 ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Bester verfügbarer Standortanbieter, Fused vor Netzwerk vor GPS. */
    fun bestProvider(lm: LocationManager): String? {
        val all = lm.allProviders
        return listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in all && runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    /** Wartende Orts-Erinnerungen (unabhängig vom Zeitfenster). */
    private fun armed(ctx: Context) =
        ReminderStore(ctx).all().filter { !it.done && it.isPlace && it.placeArmed }

    /**
     * Meldet die Überwachung passend zu den wartenden Erinnerungen an oder ab und plant den
     * nächsten Prüf-Alarm (Prüfintervall oder nächste Fenstergrenze). Orte, die neu oder nicht
     * mehr überwacht werden, gelten wieder als UNKNOWN – ein Wechsel außerhalb der Überwachung
     * soll nicht nachträglich auslösen.
     */
    @SuppressLint("MissingPermission")
    fun replan(ctx: Context) {
        val now = System.currentTimeMillis()
        val armed = armed(ctx)
        val placeStore = PlaceStore(ctx)
        val places = placeStore.all().associateBy { it.id }
        val watching = armed.filter { GeoLogic.inWindow(it, now, zone) && it.placeId in places }
        val active = watching.map { it.placeId }.toSet()
        val before = placeStore.watched
        val lm = ctx.getSystemService(LocationManager::class.java)
        val cm = ctx.getSystemService(ConnectivityManager::class.java)

        (active - before).forEach { placeStore.setPresence(it, Presence.UNKNOWN) }
        (before - active).forEach {
            placeStore.setPresence(it, Presence.UNKNOWN)
            runCatching { lm.removeProximityAlert(proximityPi(ctx, it)) }
        }
        placeStore.watched = active

        val fine = hasFineLocation(ctx)
        runCatching { lm.removeUpdates(locationPi(ctx)) }
        runCatching { cm.unregisterNetworkCallback(wifiPi(ctx)) }
        var provider: String? = null
        val interval = watching.minOfOrNull { GeoLogic.intervalMin(it) }
        if (active.isNotEmpty() && fine && interval != null) {
            active.forEach { id ->
                val p = places.getValue(id)
                runCatching {
                    lm.removeProximityAlert(proximityPi(ctx, id))
                    lm.addProximityAlert(p.lat, p.lon, p.radiusM.toFloat(), -1, proximityPi(ctx, id))
                }.onFailure { GeoLog.add(ctx, "Geofence ${p.name}: FEHLER ${it.javaClass.simpleName}") }
            }
            provider = bestProvider(lm)
            provider?.let { prov ->
                runCatching {
                    lm.requestLocationUpdates(prov, interval * 60_000L, 0f, locationPi(ctx))
                }.onFailure { GeoLog.add(ctx, "Standort-Updates: FEHLER ${it.javaClass.simpleName}") }
            }
            if (active.any { places.getValue(it).ssids.isNotEmpty() }) {
                runCatching {
                    cm.registerNetworkCallback(
                        NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                        wifiPi(ctx),
                    )
                }.onFailure { GeoLog.add(ctx, "WLAN-Meldung: FEHLER ${it.javaClass.simpleName}") }
            }
        }

        if (active != before) {
            GeoLog.add(
                ctx,
                if (active.isEmpty()) {
                    "Überwachung aus" + if (armed.isNotEmpty()) " (außerhalb der Zeitfenster)" else ""
                } else {
                    "Überwachung: " + active.joinToString { places.getValue(it).name } +
                        " · alle $interval Min · über ${provider ?: "KEINEN Standortanbieter"}" +
                        (if (!fine) " · OHNE Standortrecht" else "") +
                        (if (!hasBackgroundLocation(ctx)) " · OHNE Hintergrund-Standort" else "")
                },
            )
        }

        // Nächster Prüf-Alarm: im Prüfintervall, solange überwacht wird, sonst zur nächsten
        // Fenstergrenze einer wartenden Erinnerung.
        val next = listOfNotNull(
            interval?.let { now + it * 60_000L },
            armed.mapNotNull { GeoLogic.nextBoundary(it, now, zone) }.minOrNull(),
        ).minOrNull()
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (next == null) {
            am.cancel(tickPi(ctx))
        } else if (Scheduler.canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, tickPi(ctx))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, tickPi(ctx))
        }
    }

    /** Nach Änderungen in der Oberfläche: neu planen und gleich einmal prüfen. */
    fun refresh(ctx: Context) {
        replan(ctx)
        checkNow(ctx, "Prüfung") {}
    }

    /**
     * WLAN und Standort jetzt prüfen. Ohne Standort, der höchstens [maxAgeMs] alt ist (Standard:
     * das Prüfintervall), wird neu gemessen (asynchron); [done] wird genau einmal aufgerufen
     * (für `goAsync` im Receiver).
     */
    @SuppressLint("MissingPermission")
    fun checkNow(ctx: Context, source: String, maxAgeMs: Long? = null, done: () -> Unit) {
        val watched = PlaceStore(ctx).watched
        if (watched.isEmpty() || !hasFineLocation(ctx)) return done()
        val ssid = currentSsid(ctx)
        val maxAge = maxAgeMs
            ?: armed(ctx).minOfOrNull { GeoLogic.intervalMin(it) }?.times(60_000L) ?: 0L
        val fresh = lastFreshLocation(ctx, maxAge)
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = bestProvider(lm)
        if (fresh != null || provider == null || Build.VERSION.SDK_INT < 30) {
            evaluate(ctx, fresh, ssid, source)
            return done()
        }
        val finished = AtomicBoolean(false)
        val signal = CancellationSignal()
        fun finish(loc: Location?) {
            if (!finished.compareAndSet(false, true)) return
            runCatching { evaluate(ctx, loc, ssid, source) }
            done()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (!finished.get()) GeoLog.add(ctx, "$source: keine neue Messung in 25 s")
            signal.cancel()
            finish(null)
        }, CURRENT_LOCATION_TIMEOUT_MS)
        runCatching {
            lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { finish(it) }
        }.onFailure { finish(null) }
    }

    /** Messung und WLAN für alle überwachten Orte auswerten. */
    fun evaluate(ctx: Context, loc: Location?, ssid: String?, source: String) {
        val store = PlaceStore(ctx)
        val watched = store.watched
        val places = store.all().filter { it.id in watched }
        if (places.isEmpty()) return
        val parts = mutableListOf<String>()
        places.forEach { p ->
            val onWifi = Ssid.matches(ssid, p.ssids)
            val dist = loc?.let { distance(it, p) }
            parts += p.name + ": " + listOfNotNull(
                dist?.let { "${it.toInt()} m" },
                if (onWifi) "im WLAN" else null,
            ).ifEmpty { listOf("kein Messwert") }.joinToString(", ")
            GeoLogic.classify(dist, loc?.accuracy, p.radiusM, onWifi)?.let { update(ctx, p, it, source) }
        }
        val accuracy = loc?.let { " (±${it.accuracy.toInt()} m, ${it.provider})" }.orEmpty()
        GeoLog.add(ctx, "$source$accuracy: ${parts.joinToString(" · ")}")
    }

    /**
     * Neuer Zustand für einen Ort. Ein echter Wechsel löst die passenden wartenden
     * Erinnerungen dieses Orts aus (nur im Zeitfenster).
     */
    fun update(ctx: Context, place: Place, new: Presence, why: String) {
        val store = PlaceStore(ctx)
        val old = store.presence(place.id)
        if (old == new) return
        store.setPresence(place.id, new)
        GeoLog.add(ctx, "${place.name}: ${label(old)} → ${label(new)} ($why)")
        val trigger = GeoLogic.triggerFor(old, new) ?: return
        val now = System.currentTimeMillis()
        val due = ReminderStore(ctx).all().filter {
            it.placeId == place.id && it.placeTrigger == trigger && GeoLogic.isWatching(it, now, zone)
        }
        due.forEach {
            GeoLog.add(ctx, "Ausgelöst: „${it.title}“")
            Actions.firePlace(ctx, it)
        }
        if (due.isNotEmpty()) replan(ctx)
    }

    fun label(p: Presence) = when (p) {
        Presence.INSIDE -> "da"
        Presence.OUTSIDE -> "weg"
        Presence.UNKNOWN -> "unbekannt"
    }

    fun distance(loc: Location, p: Place): Float {
        val d = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lon, d)
        return d[0]
    }

    /**
     * Neueste Messung aller Anbieter, höchstens [maxAgeMs] alt. Bewusst die neueste, nicht die
     * genaueste: Eine ältere GPS-Messung von vor dem Weggehen würde sonst „noch da“ melden.
     */
    @SuppressLint("MissingPermission")
    fun lastFreshLocation(ctx: Context, maxAgeMs: Long): Location? {
        if (!hasFineLocation(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        val now = System.currentTimeMillis()
        return lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time in 0..maxAgeMs }
            .maxByOrNull { it.time }
    }

    /** Für „Aktuellen Standort übernehmen“: lieber GPS (fester Ort), Rückruf im Hauptthread. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun currentLocation(ctx: Context, done: (Location?) -> Unit) {
        lastFreshLocation(ctx, 30_000L)?.let { return done(it) }
        if (!hasFineLocation(ctx)) return done(null)
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = LocationManager.GPS_PROVIDER
            .takeIf { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: bestProvider(lm) ?: return done(null)
        if (Build.VERSION.SDK_INT >= 30) {
            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { done(it) }
        } else {
            lm.requestSingleUpdate(provider, { done(it) }, Looper.getMainLooper())
        }
    }

    /** Letzter bekannter Standort als Bezugspunkt für kurze Plus Codes (beliebig alt). */
    @SuppressLint("MissingPermission")
    fun anyLastLocation(ctx: Context): Location? {
        if (!hasFineLocation(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        return lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * Aktuelle SSID oder null. Ab Android 12 ist sie in `NetworkCapabilities` oft geschwärzt,
     * dann über den (veralteten) `WifiManager` – beides braucht das Standort-Recht.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun currentSsid(ctx: Context, network: Network? = null): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= 29) {
            for (n in listOfNotNull(network, cm.activeNetwork)) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                Ssid.normalize((caps.transportInfo as? WifiInfo)?.ssid)?.let { return it }
            }
        }
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return Ssid.normalize(runCatching { wm.connectionInfo?.ssid }.getOrNull())
    }
}

/** Geofence, Standort-Update, WLAN-Meldung und Prüf-Alarm der Ortserkennung. */
class GeoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val done = { runCatching { pending.finish() }; Unit }
        runCatching {
            when (intent.action) {
                GeoWatch.ACTION_TICK -> {
                    GeoWatch.replan(ctx)
                    GeoWatch.checkNow(ctx, "Prüf-Alarm", done = done)
                    return
                }
                GeoWatch.ACTION_PROXIMITY -> {
                    val id = intent.getLongExtra(GeoWatch.EXTRA_PLACE, 0)
                    val place = PlaceStore(ctx).get(id)
                    val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
                    if (place != null && id in PlaceStore(ctx).watched) {
                        if (entering) {
                            GeoWatch.update(ctx, place, Presence.INSIDE, "Geofence")
                        } else {
                            // Verlassen nur mit Abstand über den Radius hinaus: selbst nachmessen.
                            GeoLog.add(ctx, "Geofence ${place.name}: verlassen, prüfe nach")
                            // Höchstens 1 Min alte Messung, sonst neu messen.
                            GeoWatch.checkNow(ctx, "Geofence", 60_000L, done)
                            return
                        }
                    }
                }
                GeoWatch.ACTION_LOCATION -> {
                    IntentCompat.getParcelableExtra(
                        intent, LocationManager.KEY_LOCATION_CHANGED, Location::class.java,
                    )?.let { GeoWatch.evaluate(ctx, it, GeoWatch.currentSsid(ctx), "Standort-Update") }
                }
                GeoWatch.ACTION_WIFI -> {
                    val net = IntentCompat.getParcelableExtra(
                        intent, ConnectivityManager.EXTRA_NETWORK, Network::class.java,
                    )
                    val ssid = GeoWatch.currentSsid(ctx, net)
                    val store = PlaceStore(ctx)
                    val watched = store.watched
                    GeoLog.add(ctx, "WLAN-Meldung: " + (ssid?.let { "„$it“" } ?: "Name nicht lesbar"))
                    store.all().filter { it.id in watched && Ssid.matches(ssid, it.ssids) }
                        .forEach { GeoWatch.update(ctx, it, Presence.INSIDE, "WLAN „$ssid“") }
                }
            }
        }.onFailure { GeoLog.add(ctx, "Fehler: ${it.javaClass.simpleName}") }
        done()
    }
}
