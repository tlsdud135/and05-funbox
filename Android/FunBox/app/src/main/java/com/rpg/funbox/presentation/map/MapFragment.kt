package com.rpg.funbox.presentation.map

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.android.gms.location.Priority
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.InfoWindow
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.util.FusedLocationSource
import com.rpg.funbox.R
import com.rpg.funbox.databinding.FragmentMapBinding
import com.rpg.funbox.presentation.BaseFragment
import com.rpg.funbox.presentation.MapSocket.applyGame
import com.rpg.funbox.presentation.MapSocket.mSocket
import com.rpg.funbox.presentation.MapSocket.rejectGame
import io.socket.client.Socket
import com.rpg.funbox.presentation.game.GameActivity
import com.rpg.funbox.presentation.checkPermission
import com.rpg.funbox.presentation.fadeInOut
import com.rpg.funbox.presentation.login.AccessPermission
import com.rpg.funbox.presentation.login.AccessPermission.LOCATION_PERMISSION_REQUEST_CODE
import com.rpg.funbox.presentation.slideLeft
import io.socket.engineio.client.EngineIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

class MapFragment : BaseFragment<FragmentMapBinding>(R.layout.fragment_map), OnMapReadyCallback {

    private val viewModel: MapViewModel by activityViewModels()

    private lateinit var locationTimer: Timer
    private lateinit var naverMap: NaverMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationSource: FusedLocationSource

    private var isFabOpen = false

    private val requestMultiPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    fusedLocationClient =
                        LocationServices.getFusedLocationProviderClient(requireActivity())
                }

                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    fusedLocationClient =
                        LocationServices.getFusedLocationProviderClient(requireActivity())
                }

                else -> {
                    requireActivity().finish()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
        requestMultiPermissions.launch(AccessPermission.locationPermissionList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.vm = viewModel

        collectLatestFlow(viewModel.mapUiEvent) { handleUiEvent(it) }

        binding.floatingActionButton.setOnClickListener {
            toggleFab()
        }

        if (requireActivity().checkPermission(AccessPermission.locationPermissionList)) {
            viewModel.setLocationPermitted()
        }

        initMapView()
        submitUserLocation()
    }

    override fun onStart() {
        super.onStart()

        viewModel.buttonGone()
        isFabOpen = false
        viewModel.users.value?.forEach { user ->
            user.isInfoOpen = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        locationTimer.cancel()
    }

    @UiThread
    override fun onMapReady(map: NaverMap) {
        val infoWindow = InfoWindow()
        this.naverMap = map.apply {
            locationSource = this@MapFragment.locationSource
            locationTrackingMode = LocationTrackingMode.Face
            uiSettings.isLocationButtonEnabled = true

            minZoom = 5.0
            maxZoom = 20.0
            uiSettings.isZoomControlEnabled = true

            extent = LatLngBounds(LatLng(31.43, 122.37), LatLng(44.35, 132.0))
            addOnLocationChangeListener { location ->
                val cameraUpdate =
                    CameraUpdate.scrollTo(LatLng(location.latitude, location.longitude))
                naverMap.moveCamera(cameraUpdate)
                viewModel.setXY(location.latitude, location.longitude)
            }
            viewModel.users.value?.let { users ->
                users.forEach { user ->
                    viewModel.userDetail.value?.let { userDetail ->
                        if ((user.id == userDetail.id) && (user.isInfoOpen)) {
                            infoWindow.open(this)
                        }
                    }
                }
            }
        }

        naverMap.locationOverlay.apply {
            isVisible = true
            iconHeight = 120
            iconWidth = 120
            icon = OverlayImage.fromResource(R.drawable.navi_icon)
        }
        val hasMsg = InfoWindow()
        hasMsg.adapter = object : InfoWindow.DefaultTextAdapter(requireContext()) {
            override fun getText(infoWindow: InfoWindow): CharSequence {
                return "● ● ●"
            }
        }

        map.setOnMapClickListener { _, _ ->
            viewModel.buttonGone()
            viewModel.users.value?.forEach {
                it.isInfoOpen = false
                it.mapPin?.infoWindow?.close()
                Timber.d("@111111")
                if (it.isMsg) {
                    it.mapPin?.let { mapPin -> hasMsg.open(mapPin) }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.users.collect {
                it.map { user ->
                    var adapter =
                        MapProfileAdapter(requireContext(), viewModel.userDetail.value, null)
                    user.mapPin = Marker().apply {
                        position = user.loc
                        iconTintColor = Color.YELLOW
                        captionText = user.name.toString()
                        captionTextSize = 20F
                        if (user.isMsg) {
                            hasMsg.open(this)
                        }
                        setOnClickListener { _ ->
                            viewModel.userDetailApi(user.id)
                            infoWindow.adapter = adapter
                            runBlocking {
                                val test = viewModel.userDetail.value?.profile
                                val image: Bitmap = try {
                                    withContext(Dispatchers.IO) {
                                        Glide.with(requireContext())
                                            .asBitmap()
                                            .load(test)
                                            .apply(RequestOptions().override(100, 100))
                                            .submit()
                                            .get()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.IO) {
                                        Glide.with(requireContext())
                                            .asBitmap()
                                            .load(R.drawable.close_24)
                                            .apply(RequestOptions().override(100, 100))
                                            .submit()
                                            .get()
                                    }
                                }

                                adapter =
                                    MapProfileAdapter(
                                        requireContext(),
                                        viewModel.userDetail.value,
                                        image
                                    )
                            }


                            if (!this.hasInfoWindow() || this.infoWindow == hasMsg) {
                                viewModel.buttonVisible()
                                viewModel.users.value?.forEach { user ->
                                    if (user.isMsg) {
                                        user.mapPin?.let { mapPin -> hasMsg.open(mapPin) }
                                    }
                                }
                                user.isInfoOpen = true
                                infoWindow.open(this)
                                Timber.d("@@@@@@")
                            } else {
                                viewModel.buttonGone()
                                user.isInfoOpen = false
                                this.infoWindow?.close()
                                Timber.d("!!!!!!!")
                                if (user.isMsg) {
                                    hasMsg.open(this)
                                }
                            }
                            true
                        }

                        if (user.isInfoOpen) {
                            requireActivity().runOnUiThread {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    this.infoWindow?.close()
                                    infoWindow.adapter = adapter
                                    infoWindow.open(this)
                                }, 500)
                            }
                        }
                    }
                    user.mapPin?.map = naverMap
                }.toList()
            }
        }
    }

    private fun initMapView() {
        binding.map.getFragment<MapFragment>().getMapAsync(this)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
    }

    private fun toggleFab() {
        if (isFabOpen) {
            ObjectAnimator.ofFloat(binding.floatingSetting, "translationY", 0f).apply { start() }
            ObjectAnimator.ofFloat(binding.floatingWrite, "translationY", 0f).apply { start() }
            binding.floatingActionButton.setImageResource(R.drawable.add_24)
        } else {
            ObjectAnimator.ofFloat(binding.floatingSetting, "translationY", -200f).apply { start() }
            ObjectAnimator.ofFloat(binding.floatingWrite, "translationY", -400f).apply { start() }
            binding.floatingActionButton.setImageResource(R.drawable.close_24)
        }

        isFabOpen = !isFabOpen
    }

    private fun submitUserLocation() {
        if (requireActivity().checkPermission(AccessPermission.locationPermissionList)) {
            Timer().scheduleAtFixedRate(0, 3000) {
                lifecycleScope.launch {
                    val drawPinDeferred = async {
                        binding.map.getFragment<MapFragment>()
                            .getMapAsync(this@MapFragment)
                    }
                    drawPinDeferred.await()
                    val location = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).await()
                    val makePinDeferred = async {
                        viewModel.setUsersLocations(location.latitude, location.longitude)
                    }
                    makePinDeferred.await()
                }
            }
        }
    }

    private fun handleUiEvent(event: MapUiEvent) = when (event) {
        is MapUiEvent.LocationPermitted -> {
            initMapView()
            submitUserLocation()
        }

        is MapUiEvent.MessageOpen -> {
            findNavController().navigate(R.id.action_mapFragment_to_messageDialog)
            //MessageDialog().show(parentFragmentManager, "messageDialog")
        }

        is MapUiEvent.ToGame -> {
            val intent = Intent(context, GameActivity::class.java)
            intent.putExtra("StartGame", false)
            intent.putExtra("RoomId", applyGameServerData.roomId)
            intent.putExtra("OtherUserId", applyGameServerData.userId.toInt())
            startActivity(intent, requireActivity().fadeInOut())
        }

        is MapUiEvent.GameStart -> {
            val intent = Intent(context, GameActivity::class.java)
            intent.putExtra("StartGame", true)
            intent.putExtra("OtherUserId", viewModel.userDetail.value?.id)
            viewModel.userDetail.value?.let { applyGame(it.id) }
            startActivity(intent, requireActivity().slideLeft())
        }

        is MapUiEvent.RejectGame -> {
            viewModel.applyGameFromServerData.value?.roomId?.let { rejectGame(it) }
        }

        is MapUiEvent.GetGame -> {
            //findNavController().navigate(R.id.action_mapFragment_to_getGameDialog)
            GetGameDialog().show(parentFragmentManager, "getGame")
        }

        is MapUiEvent.ToSetting -> {
            findNavController().navigate(R.id.action_mapFragment_to_settingFragment)
        }

        is MapUiEvent.Toggle -> {
            toggleFab()
        }

        is MapUiEvent.NetworkErrorEvent -> {
            showSnackBar(R.string.network_error_message)
        }

        else -> {}
    }
}