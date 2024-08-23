package com.burak.kebapp

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_META_DATA
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.telecom.Call
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.burak.kebapp.databinding.ActivityMapsBinding
import com.google.android.material.snackbar.Snackbar
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var locationListener: LocationListener
    private lateinit var locationManager: LocationManager
    lateinit var kebabcikonum : LatLng
    lateinit var kebabciisim : String
    val apiKey = application.packageManager
        .getApplicationInfo(application.packageName, GET_META_DATA)
        .metaData["com.google.android.geo.API_KEY"]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        registerLauncher()
    }

    fun closestKebapci(userlan: Double, userlong: Double) {
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$userlan,$userlong&radius=5000&type=restaurant&keyword=kebap&key=$apiKey"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MapsActivity, "Bir hata oluştu", Toast.LENGTH_SHORT).show()
                }
                println(e.localizedMessage)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.body?.string()?.let {
                    println(it)
                    val returnList = parseJson(it)
                    runOnUiThread {
                        if (returnList.isEmpty()) {
                            Toast.makeText(this@MapsActivity, "5 km yakında kebapçı yok", Toast.LENGTH_LONG).show()
                        } else {
                            kebabciisim = returnList[0]
                            kebabcikonum = LatLng(returnList[1].toDouble(), returnList[2].toDouble())
                            mMap.addMarker(MarkerOptions().position(kebabcikonum).title(returnList[0]))
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(kebabcikonum, 14f))
                        }
                    }
                }
            }
        })
    }

    fun parseJson(jsonString: String): List<String> {
        val jsonObject = JSONObject(jsonString)
        val resultsArray = jsonObject.getJSONArray("results")
        val returnList = mutableListOf<String>()

        if (resultsArray.length() > 0) {
            val placeObject = resultsArray.getJSONObject(0)
            val name = placeObject.getString("name")
            val geometry = placeObject.getJSONObject("geometry")
            val location = geometry.getJSONObject("location")
            val latitude = location.getDouble("lat")
            val longitude = location.getDouble("lng")
            returnList.add(name)
            returnList.add(latitude.toString())
            returnList.add(longitude.toString())
        }

        return returnList
    }




    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager

        //kullanıcının anlık konumunu alma ve orayı gösterme.
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // mMap.clear()
                val kullanicikonum = LatLng(location.latitude, location.longitude)
                mMap.addMarker(MarkerOptions().position(kullanicikonum).title("kullanıcının konumu"))
                try {
                    println(kullanicikonum)
                    var enyakinkebabci = closestKebapci(location.latitude, location.longitude)
                    println(enyakinkebabci)
                    mMap.addMarker(MarkerOptions().position(kebabcikonum).title(kebabciisim))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(kebabcikonum, 14f))
                }catch (e : Exception){
                    Toast.makeText(this@MapsActivity, "catch çalıştı", Toast.LENGTH_SHORT).show()
                    println(e.localizedMessage)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(kullanicikonum, 14f))
                }

            }

        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Snackbar.make(binding.root, "izin lazım", Snackbar.LENGTH_INDEFINITE)
                    .setAction("izin ver.") {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }.show()
            } else {
                //izin isteyeceğiz
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            //izin verilmiş
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 15f, locationListener)
            //gpsden veri alamıyosam şuan
            val sonBilinenKonum = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (sonBilinenKonum != null) {
                val sonBilinenLangLong = LatLng(sonBilinenKonum.latitude, sonBilinenKonum.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLng(sonBilinenLangLong))
            }

        }
    }

    private fun registerLauncher() {
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
                if (result) {
                    if (ContextCompat.checkSelfPermission(this@MapsActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 15f, locationListener)
                        //gpsden veri alamıyosam şuan
                        val sonBilinenKonum = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        if (sonBilinenKonum != null) {
                            val sonBilinenLangLong = LatLng(sonBilinenKonum.latitude, sonBilinenKonum.longitude)
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(sonBilinenLangLong))
                        }
                    }
                } else {
                    Toast.makeText(this, "İzin verilmedi.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
