package com.example.communityserviceapp.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.FloatingSearchView.OnSearchListener
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.MapFragmentBinding
import com.example.communityserviceapp.util.TextDrawable
import com.example.communityserviceapp.util.checkIfMayNavigate
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.maps.android.SphericalUtil
import com.google.maps.android.clustering.ClusterManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import kotlin.math.floor

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {

    private val mapViewModel: MapViewModel by viewModels()
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private lateinit var clusterManager: ClusterManager<Recipient>
    private lateinit var binding: MapFragmentBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var mapView: MapView
    private var locationPermissionGranted = false
    private var failedToGetLastKnownLocation = false
    private var hasGPSLocationPermissionBeenAsked = false
    private var sortedDistanceBetweenCurrentLocationMapper = mutableMapOf<String, Double>()
    private val recipientPositionMapper = mutableMapOf<LatLng, String>()
    private var distanceBetweenCurrentLocationMapper = mutableMapOf<String, Double>()
    private var currentSelectedMarkerPosition: LatLng? = null
    private var currentDeviceMarker: Marker? = null
    private var locationCallback: LocationCallback? = null
    private var selectedRecipient: Recipient? = null
    private var timer: Timer? = null
    private var toast: Toast? = null

    companion object {
        const val MAPVIEW_BUNDLE_KEY = "mapBundle"
        const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2001
        const val PERMISSIONS_REQUEST_ENABLE_GPS = 2002
        const val ERROR_DIALOG_REQUEST = 2003
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MapFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = binding.mapView
        initGoogleMap(savedInstanceState)
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume() // Reload map
        if (checkMapServices()) {
            // check location permission again
            if (!locationPermissionGranted) {
                requestLocationPermission()
            } else {
                // This statement execute when user navigate back to this fragment
                // from recipient page and such that location permission is enabled
                setupFusedLocation()
                if (currentDeviceMarker != null) {
                    addCurrentDeviceMarker(
                        currentDeviceMarker!!.position.latitude,
                        currentDeviceMarker!!.position.longitude
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupFusedLocation() {
        // Note: onLocationResult() callback is only called when GPS is enabled
        // There is a bug such that onLocationResult() callback does not work even with GPS enabled.
        // To replicate:
        // 1) Disable gps then open Map, and open location setting via the alert dialog.
        // 2) Turn location on, click back and turn off location quickly
        // 3) Wait for a few minute (Alert dialog will show but just ignore it)
        // 4) Then turn on your location after few minute. Now GPS is enabled but onLocationResult()
        // callback did not invoke.
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // if location is found, check if previously successfully get last known location
                for (location in locationResult.locations) {
                    if (failedToGetLastKnownLocation) {
                        showToast("Current location found")
                        addCurrentDeviceMarker(location.latitude, location.longitude)
                        failedToGetLastKnownLocation =
                            false // disable it as we only want to zoom in once only
                    } else {
                        addCurrentDeviceMarker(location.latitude, location.longitude)
                    }
                }
            }
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 30000 // Request location update every 30 sec
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val locationSettingsRequest =
            LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
        val client = LocationServices.getSettingsClient(requireContext())
        val locationSettingsResponseTask = client.checkLocationSettings(locationSettingsRequest)

        locationSettingsResponseTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fusedLocationProviderClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                autoCheckMapServices()
            } else {
                // User might turn off location permission quickly (like ninja) after turning it on
                // before the request is processed so if response failed then we check map services again
                checkMapServices()
            }
        }
    }

    // Check map services every 30 second
    private fun autoCheckMapServices() {
        timer = Timer()
        timer!!.schedule(
            object : TimerTask() {
                override fun run() {
                    if (isAdded) {
                        requireActivity().runOnUiThread { Handler().post { checkMapServices() } }
                    }
                }
            },
            0, 30000
        )
    }

    private fun initGoogleMap(savedInstanceState: Bundle?) {
        // MapView requires that the Bundle you pass contain only MapView SDK objects or sub-Bundles
        var mapViewBundle: Bundle? = null
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY)
        }
        mapView.onCreate(mapViewBundle)
        mapView.getMapAsync(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        var mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY)
        if (mapViewBundle == null) {
            mapViewBundle = Bundle()
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle)
        }
        mapView.onSaveInstanceState(mapViewBundle)
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        locationCallback?.let { fusedLocationProviderClient?.removeLocationUpdates(it) }
    }

    override fun onDestroy() {
        toast?.cancel()
        timer = null
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun checkMapServices(): Boolean = isServicesOK() && isMapsEnabled()

    private fun isServicesOK(): Boolean {
        Timber.d("isServicesOK: checking google services version")
        val available =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext())
        when {
            available == ConnectionResult.SUCCESS -> {
                Timber.d("isServicesOK: Google Play Services is working")
                return true
            }
            GoogleApiAvailability.getInstance().isUserResolvableError(available) -> {
                // An error occurred but we can resolve it
                Timber.d("isServicesOK: an error occured but we can fix it")
                val dialog = GoogleApiAvailability.getInstance()
                    .getErrorDialog(requireActivity(), available, ERROR_DIALOG_REQUEST)
                dialog.show()
            }
            else -> {
                showToast("You can't make map requests")
            }
        }
        return false
    }

    // Check for GPS and Network providers are enabled or not
    private fun isMapsEnabled(): Boolean {
        val manager =
            requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var gpsEnabled = false
        var networkEnabled = false
        try {
            gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        try {
            networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        if (!gpsEnabled) {
            showNoGpsAlertDialog()
            return false
        } else if (!networkEnabled) {
            showToast("Network provider not found or disabled")
            return false
        }
        return true
    }

    // Request app's location permission, so that we can get the location of the device.
    // The result of the permission request is handled by a callback, onRequestPermissionsResult.
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            getDeviceLastLocation()
            setupFusedLocation() // Setup fused location only when permission is granted
            mapView.onResume() // Reload map
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                // User click deny before
                showLocationPermissionNeededAlertDialog()
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                )
            }
        }
    }

    private fun showNoGpsAlertDialog() {
        if (!hasGPSLocationPermissionBeenAsked && isAdded) {
            Timber.d("GPS permission asked")
            hasGPSLocationPermissionBeenAsked = true
            MaterialAlertDialogBuilder(requireContext(), R.style.DefaultAlertDialogTheme)
                .setTitle("Permission needed")
                .setMessage(getString(R.string.gps_permission_required_message))
                .setCancelable(false)
                .setPositiveButton("Yes") { dialog: DialogInterface?, id: Int ->
                    val enableGpsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivityForResult(enableGpsIntent, PERMISSIONS_REQUEST_ENABLE_GPS)
                }
                .setNegativeButton("Cancel") { dialog: DialogInterface, id: Int ->
                    NavHostFragment.findNavController(this).navigateUp()
                    dialog.dismiss()
                }
                .create().show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PERMISSIONS_REQUEST_ENABLE_GPS && resultCode != Activity.RESULT_OK) {
            hasGPSLocationPermissionBeenAsked = false // set false if user did not turn on GPS
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLastLocation() {
        val locationTask = fusedLocationProviderClient?.lastLocation
        locationTask?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val currentDeviceLastKnownLocation = task.result
                if (currentDeviceLastKnownLocation != null) {
                    showToast("Current location found")
                    val latitude = currentDeviceLastKnownLocation.latitude
                    val longitude = currentDeviceLastKnownLocation.longitude
                    addCurrentDeviceMarker(latitude, longitude)
                } else {
                    Timber.e("Current device last known location was null")
                    failedToGetLastKnownLocation = true
                }
            } else {
                // Note: Even when failed to get last known location, fused location api will obtain the
                // latest location since we setup fused location as well in getLocationPermission() method
                Timber.e("Failed to get current device last known location")
                failedToGetLastKnownLocation = true
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        setupClusterer()
        subscribeUI()
        setupListener()
        googleMap.uiSettings.isZoomControlsEnabled = true // enable zoom controls
        repositionGoogleMapCompass()
        checkIfCurrentSelectedMarkerPositionIsEmpty()
    }

    private fun repositionGoogleMapCompass() {
        try {
            val parent =
                mapView.findViewWithTag<View>("GoogleMapMyLocationButton").parent as ViewGroup
            parent.post {
                try {
                    // convert dp margin into pixels 
                    val displayMetrics = resources.displayMetrics
                    val rghtMarginPixel = mapViewModel.getMarginPixel(20f, displayMetrics)
                    val leftMarginPixel = mapViewModel.getMarginPixel(13f, displayMetrics)
                    // Get the map Compass view
                    // Note: Index 2 is Zoom In and Out
                    val mapCompass = parent.getChildAt(4)
                    // create layoutParams, giving it our wanted width and height(important, by default the width is "match parent")
                    val rlp = RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                    )
                    // position on bottom left
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0)
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    // give compass margin
                    rlp.setMargins(leftMarginPixel, 0, rghtMarginPixel, 75)
                    mapCompass.layoutParams = rlp
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun setDefaultCameraPosition(shouldAnimate: Boolean = false) {
        // Overall map view window: 0.15 * 0.15 = 0.0225
        val mapBoundary = LatLngBounds(
            LatLng(3.2072430785216834 - .15, 101.52844357680757 - .15),
            LatLng(3.250855389255246 + .15, 101.74244338181607 + .15)
        )
        if (!shouldAnimate) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mapBoundary, 0))
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(mapBoundary, 0), 820, null)
        }
    }

    private fun subscribeUI() {
        mapViewModel.allRecipient.observe(
            viewLifecycleOwner,
            { recipientList ->
                if (recipientList != null && recipientList.isNotEmpty()) {
                    clusterManager.clearItems() // clear cluster manager as the list changes
                    clusterManager.addItems(recipientList)
                    clusterManager.cluster()
                    // Get id and location of each recipient (use for determining closest recipient later on)
                    for (recipient in recipientList) {
                        recipientPositionMapper[recipient.position] = recipient.recipientName
                    }
                }
            }
        )

        mapViewModel.searchResult.observe(
            viewLifecycleOwner,
            { searchResult ->
                if (searchResult != null && searchResult.isNotEmpty()) {
                    binding.floatingSearchView.swapSuggestions(searchResult)
                } else {
                    binding.floatingSearchView.clearSuggestions()
                }
            }
        )
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun setupClusterer() {
        // Initialize the manager with the context and the map.
        // (Activity extends context, so we can pass 'this' in the constructor.)
        clusterManager = ClusterManager(requireContext(), googleMap)
        // Point the map's listeners at the listeners implemented by the clustermanager.
        googleMap.setOnCameraIdleListener(clusterManager)
        googleMap.setOnMarkerClickListener(clusterManager)
    }

    private fun setupListener() {
        binding.apply {
            currentLocationZoomIn.setOnClickListener {
                if (currentSelectedMarkerPosition != null) {
                    zoomInOnCluster(currentSelectedMarkerPosition!!, 17f, 600)
                } else {
                    zoomInOnCurrentDeviceLocation()
                }
            }

            resetCameraChip.setOnClickListener { setDefaultCameraPosition(true) }

            // Note: a bug in filling up the sliding panel such that the view isn't always
            // fully inflated properly when you move the map view fast then click on a cluster item
            // and pull up the sliding panel
            // To reproduce: search for a recipient using the searchview, then quickly click on a
            // cluster item. Pull up the sliding panel. Repeat the step a few time to get better idea.
            // The main problem: Probably has to be with the way google map or slidingUpPanel inflate view
            clusterManager.setOnClusterItemClickListener { item ->
                selectedRecipient = item
                actionChip.visibility = View.GONE

                title.text = item.recipientName
                recipientAddress.text = item.recipientAddress
                recipientAddress.visibility = View.VISIBLE

                mapFooter.isClickable = true
                currentSelectedMarkerPosition = LatLng(item.latitude, item.longitude)
                false
            }

            googleMap.setOnInfoWindowCloseListener {
                mapFooter.isClickable = false
                currentSelectedMarkerPosition = null
                recipientAddress.visibility = View.GONE
                title.text = getString(R.string.bottom_sheet_title_for_map_fragment)
                actionChip.visibility = View.VISIBLE
            }

            clusterManager.setOnClusterClickListener { cluster ->
                zoomInOnCluster(cluster.position)
                true
            }

            mapFooter.setOnClickListener {
                selectedRecipient?.let { navigateToRecipientBottomSheet(it) }
            }

            clusterManager.setOnClusterItemInfoWindowClickListener { recipientItem ->
                val recipientID = recipientItem.recipientID
                if (recipientID != "My Location") {
                    navigateToRecipientBottomSheet(recipientItem)
                }
            }

            // Set left icon for each suggestion via suggestion callback
            floatingSearchView.setOnBindSuggestionCallback { suggestionView: View?,
                leftIcon: ImageView,
                textView: TextView?,
                item: SearchSuggestion,
                itemPosition: Int ->
                val recipientItem = item as Recipient
                leftIcon.setImageDrawable(TextDrawable(recipientItem.recipientState))
            }

            floatingSearchView.setOnQueryChangeListener { oldQuery: String?, newQuery: String ->
                mapViewModel.setSearchKeyword(newQuery)
            }

            floatingSearchView.setOnFocusChangeListener { view: View, hasFocus: Boolean ->
                if (hasFocus) {
                    showToast("refreshing")
                    mapViewModel.refreshSearch()
                }
            }

            floatingSearchView.setOnFocusChangeListener(object :
                    FloatingSearchView.OnFocusChangeListener {
                    override fun onFocus() {
                        mapViewModel.refreshSearch()
                    }

                    override fun onFocusCleared() {
                        // Do nothing
                    }
                })

            floatingSearchView.setOnSearchListener(object : OnSearchListener {
                override fun onSuggestionClicked(searchSuggestion: SearchSuggestion) {
                    lifecycleScope.launch {
                        val recipient = withContext(Dispatchers.IO) {
                            mapViewModel.getRecipientItemByName(searchSuggestion.body)
                        }
                        floatingSearchView.clearSearchFocus()
                        floatingSearchView.clearQuery()
                        zoomInOnCluster(recipient.position, 18f, 820)
                    }
                }

                override fun onSearchAction(currentQuery: String) {}
            })

            sortRecipientByDistanceChip.setOnClickListener {
                if (currentDeviceMarker != null) {
                    // Check if the distance between current location and all recipient has been calculated
                    // OR
                    // Check if the recipient list changes
                    val allRecipientCountSize = mapViewModel.getAllRecipientCountSize()
                    if (distanceBetweenCurrentLocationMapper.size != allRecipientCountSize) {
                        calculateDistanceBetweenCurrentDeviceLocationAndAllRecipient()
                    }

                    // Get all value from map and put into arraylist
                    val arrayList = arrayListOf<String?>()
                    val df2 = DecimalFormat("#.##")
                    df2.roundingMode = RoundingMode.UP
                    for ((key, value) in sortedDistanceBetweenCurrentLocationMapper) {
                        val distanceInKilometer = value / 1000
                        arrayList.add("\n$key\n${df2.format(distanceInKilometer)} km\n")
                    }
                    // covert arraylist to array and create alert dialog
                    val itemArray = arrayList.toTypedArray()
                    showRecipientSortedByDistanceAlertDialog(itemArray)
                } else {
                    showToast(getString(R.string.unable_to_get_current_location_error))
                }
            }
        }
    }

    private fun addCurrentDeviceMarker(latitude: Double, longitude: Double) {
        if (currentDeviceMarker != null) {
            currentDeviceMarker!!.remove() // Remove previous current device marker (if have)
        }
        val customMarkerIcon =
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        val latLng = LatLng(latitude, longitude)
        currentDeviceMarker = googleMap.addMarker(
            MarkerOptions().position(latLng).icon(customMarkerIcon).title("My Location")
        )
    }

    private fun zoomInOnCurrentDeviceLocation() {
        if (currentDeviceMarker != null) {
            zoomInOnCluster(currentDeviceMarker!!.position, 12f, 820)
        } else {
            showToast(getString(R.string.unable_to_get_current_location_error))
        }
    }

    private fun zoomInOnCluster(position: LatLng, zoomLevel: Float? = null, duration: Int? = null) {
        val animateDuration = when (duration) {
            null -> 450
            else -> duration
        }
        val cameraPosition = getCameraPosition(position, zoomLevel)
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)
        googleMap.animateCamera(cameraUpdate, animateDuration, null)
    }

    private fun getCameraPosition(position: LatLng, zoomLevel: Float?): CameraPosition {
        val zoom = when (zoomLevel) {
            null -> floor((googleMap.cameraPosition.zoom + 1).toDouble()).toFloat()
            else -> zoomLevel
        }
        return CameraPosition.Builder().target(position).zoom(zoom).build()
    }

    private fun calculateDistanceBetweenCurrentDeviceLocationAndAllRecipient() {
        for ((key, value) in recipientPositionMapper) {
            val distanceFromCurrentLocationToRecipient =
                SphericalUtil.computeDistanceBetween(currentDeviceMarker!!.position, key)
            // put the value into another mapper (for calculating of the whole list of closest recipient)
            distanceBetweenCurrentLocationMapper[value] = distanceFromCurrentLocationToRecipient
            // Create a sorted map by the distance (closest to farthest)
            sortedDistanceBetweenCurrentLocationMapper =
                mapViewModel.sortMapByValue(distanceBetweenCurrentLocationMapper)
        }
    }

    private fun checkIfCurrentSelectedMarkerPositionIsEmpty() {
        // currentSelectedMarkerPosition is only not null when user navigate back to this fragment from a recipient page
        if (currentSelectedMarkerPosition != null) {
            val cameraPosition = getCameraPosition(currentSelectedMarkerPosition!!, 15f)
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            // Set to null so "zoom in on current location" will zoom in on device location
            currentSelectedMarkerPosition = null
        } else {
            setDefaultCameraPosition()
        }
    }

    private fun showRecipientSortedByDistanceAlertDialog(itemArray: Array<String?>) {
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDimAmountMapAlertDialogTheme)
            .setTitle("Recipient Sorted By Distance")
            .setItems(itemArray) { dialog: DialogInterface?, itemPosition: Int ->
                val selectedItemText = itemArray[itemPosition] as String
                // Get the position of second occurrence of "\n"
                val positionOfSecondOccurrenceOfNewLine =
                    selectedItemText.indexOf("\n", 1)

                if (positionOfSecondOccurrenceOfNewLine != -1) {
                    // get itemText which is the recipient name
                    val recipientName = selectedItemText.substring(
                        1,
                        positionOfSecondOccurrenceOfNewLine
                    )
                    // search for recipient's position from all recipient position mapper
                    val recipientLatLngPosition =
                        mapViewModel.getKeysByValue(recipientPositionMapper, recipientName)
                    // Get the first value (recipient position) from the Set
                    for (latLng in recipientLatLngPosition) {
                        zoomInOnCluster(latLng, 15f, 450)
                        break
                    }
                }
            }
            .setPositiveButton("Close") { dialogInterface: DialogInterface, i: Int -> dialogInterface.dismiss() }
            .setNeutralButton("Reverse Order") { dialogInterface: DialogInterface, i: Int ->
                val reversedArray = mapViewModel.reverseStringArray(itemArray, itemArray.size)
                dialogInterface.dismiss()
                showRecipientSortedByDistanceAlertDialog(reversedArray)
            }
            .create().show()
    }

    private fun showLocationPermissionNeededAlertDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DefaultAlertDialogTheme)
            .setTitle("Permission needed")
            .setMessage(getString(R.string.app_permission_location_permission_required_message))
            .setCancelable(false)
            .setPositiveButton("Open Setting") { dialog: DialogInterface?, id: Int ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + requireActivity().packageName)
                )
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog: DialogInterface?, id: Int ->
                findNavController().navigateUp()
            }
            .create().show()
    }

    private fun navigateToRecipientBottomSheet(recipient: Recipient) {
        checkIfMayNavigate {
            val action = MapFragmentDirections.actionMapFragmentToRecipientBottomSheet(recipient)
            findNavController().navigate(action)
        }
    }

    private fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast?.show()
    }
}
