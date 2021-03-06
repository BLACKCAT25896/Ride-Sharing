package joy.kamrujjaman.uberclone.ui.home

import android.Manifest
import android.Manifest.permission
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import joy.kamrujjaman.uberclone.Common
import joy.kamrujjaman.uberclone.R

class HomeFragment : Fragment(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //Online system
    private lateinit var onlineRef:DatabaseReference
    private lateinit var currentUserRef: DatabaseReference
    private lateinit var driversLocationRef:DatabaseReference
    private lateinit var geoFire:GeoFire

    private val onlineValueEventListener = object :ValueEventListener{
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()){
                currentUserRef.onDisconnect().removeValue()
            }

        }

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView()!!, error.message, Snackbar.LENGTH_SHORT).show()
        }

    }



    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        geoFire.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
        onlineRef.removeEventListener(onlineValueEventListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        registerOnlineSystem()
    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProviders.of(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        init()

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }

    private fun init() {

        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")
        driversLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE)
        currentUserRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE).child(
            FirebaseAuth.getInstance().currentUser!!.uid
        )
        geoFire = GeoFire(driversLocationRef)
        registerOnlineSystem()


        locationRequest = LocationRequest()
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setFastestInterval(3000)
        locationRequest.interval = 5000
        locationRequest.setSmallestDisplacement(10f)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)


                    val newPos = LatLng(locationResult!!.lastLocation.latitude, locationResult!!.lastLocation.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                //update location
                geoFire.setLocation(
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    GeoLocation(locationResult.lastLocation.latitude,locationResult.lastLocation.longitude)

                ){key: String?, error: DatabaseError? ->
                    if (error!=null)
                        Snackbar.make(mapFragment.requireView(),error.message,Snackbar.LENGTH_LONG).show()
                    else
                        Snackbar.make(mapFragment.requireView(),"You are Online",Snackbar.LENGTH_LONG).show()
                }


            }

        }
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context!!)
        if (ActivityCompat.checkSelfPermission(
                context!!,
                permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context!!,
                permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.myLooper()
            )
            return
        }

    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!


        Dexter.withContext(context)
            .withPermission(permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener{
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled =true
                    mMap.setOnMyLocationClickListener {
                        if (ActivityCompat.checkSelfPermission(
                                context!!,
                                permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                context!!,
                                permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {

                            fusedLocationProviderClient.lastLocation
                                .addOnFailureListener{e->
                                    Toast.makeText(context,e.message,Toast.LENGTH_SHORT).show()

                                }.addOnSuccessListener { location ->
                                    val userLatLng = LatLng(location.latitude, location.longitude)
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f))
                                }
                            true
                        }


                        val locationButton = (mapFragment.view!!
                            .findViewById<View>("1".toInt())
                            .parent as View).findViewById<View>("2".toInt())
                        val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                        params.addRule(RelativeLayout.ALIGN_TOP,0)
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,RelativeLayout.TRUE)
                        params.bottomMargin = 50

                        }


                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(context,"Permission"+ p0!!.permissionName+ "was Denied",Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                    TODO("Not yet implemented")
                }

            }).check();

        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    context,
                    R.raw.uber_maps_style
                )
            )
            if (!success)
                Log.e("map", "Style Parsing Error")
        } catch (e: Resources.NotFoundException) {
            Log.e("map", e.message!!)
        }


    }
}