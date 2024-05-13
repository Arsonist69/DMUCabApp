package com.example.mydmucabapp_driver.fragment

import RideRequestsAdapter
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mydmucabapp_driver.controller.ActiveRideActivity
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.databinding.FragmentRideRequestsDialogBinding
import com.example.mydmucabapp_driver.helpers.SharedTripService
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.example.mydmucabapp_driver.model.DataHandling.TripsRepository
import com.example.mydmucabapp_driver.utils.FCMSend
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RideRequestsDialogFragment : DialogFragment() {
    private var _binding: FragmentRideRequestsDialogBinding? = null
    private val binding get() = _binding!!
    private val tripsRepository = TripsRepository()

    private lateinit var adapter: RideRequestsAdapter

    companion object {
        private const val RIDES_KEY = "rides"

        fun newInstance(rides: ArrayList<RideRequest>): RideRequestsDialogFragment {
            val args = Bundle()
            args.putSerializable(RIDES_KEY, rides)
            return RideRequestsDialogFragment().apply { arguments = args }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = false
        _binding = FragmentRideRequestsDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        val dialogWidth = (displayMetrics.widthPixels * 0.8).toInt()
        dialog?.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rides = arguments?.getSerializable(RIDES_KEY) as? ArrayList<RideRequest> ?: arrayListOf()
        setupAdapter(rides)
        updateLogoAndTextVisibility(rides.isEmpty())

        binding.btnCancel.setOnClickListener {
            showCancelConfirmationDialog()

        }
        binding.btnAccept.setOnClickListener{
            startRide()
        }






    }

    private fun setupAdapter(rides: ArrayList<RideRequest>) {
        adapter = RideRequestsAdapter(rides).also {
            it.onAcceptClick = { rideRequest ->
                acceptRideRequest(rideRequest)
            }
            it.onRejectClick = { rideRequest ->
                rejectRideRequest(rideRequest)
            }
        }
        binding.recyclerViewRideRequests.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewRideRequests.adapter = adapter

    }
    private fun handleCarAnimation(shouldAnimate: Boolean) {
        val logoAndTextLayout = binding.logoAndText
        if (shouldAnimate) {
            val carAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.repeat).apply {
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        binding.logoCar.startAnimation(this@apply)
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
            }
            binding.logoCar.startAnimation(carAnimation)
            logoAndTextLayout.visibility = View.VISIBLE
        } else {
            binding.logoCar.clearAnimation()
            logoAndTextLayout.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun acceptRideRequest(rideRequest: RideRequest) {
        val documentId = rideRequest.documentId

        if (documentId.isEmpty()) {
            showToast("Invalid ride request")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                tripsRepository.acceptRideRequest(documentId)
                SharedTripService.setAcceptedTripsDocumentIds(documentId)
                SharedTripService.addAcceptedTrip(rideRequest)
                rideRequest.isProcessed = true

                withContext(Dispatchers.Main) {
                    adapter.notifyDataSetChanged()
                    showToast("Ride request accepted successfully")
                }

                withContext(Dispatchers.IO){
                    decrementAvailableSeats()

                }
                FCMSend().pushNotification(
                    requireContext(),
                    "/topics/${rideRequest.user.userId}",
                    "Notification",
                    "Your ride request is accepted"
                ) {}
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Failed to accept ride request: ${exception.message}")
                }
            }
        }
    }

    private fun CoroutineScope.decrementAvailableSeats() {
        val postedTripDocumentId = SharedTripService.getPostedTripsDocumentId()

        launch {
            try {
                val availableSeats = tripsRepository.decrementAvailableSeats(postedTripDocumentId)
                withContext(Dispatchers.Main) {
                    if (availableSeats <= 0) {
                        showFullyBookedDialog()
                    }
                    Log.d("DriverDetailsDialogFragment", "Available seats updated successfully.")
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("DriverDetailsDialogFragment", "Failed to update available seats: ${exception.message}")
                }
            }
        }
    }


    private fun showFullyBookedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Ride Fully Booked")
            .setMessage("All seats have been booked. You can now start the trip.")
            .setPositiveButton("Start Trip") { dialog, which ->
               startRide()
            }
            .create()
            .show()
    }






    private fun startRide() {
        val acceptedRideRequestIds = SharedTripService.getAcceptedTripsDocumentIds()
        val postedTripDocumentId = SharedTripService.getPostedTripsDocumentId()
        val driverId = tripsRepository.getDriverId()

        if (postedTripDocumentId.isEmpty()) {
            Log.e("TripFlow", "No posted trip document ID found.")
            return
        }

        if (acceptedRideRequestIds.isEmpty()) {
            showToast("No ride request has been accepted. Cannot start the trip.")
            Log.e("TripFlow", "No ride request has been accepted.")
            return
        }


        lifecycleScope.launch {
            try {
                tripsRepository.updateAcceptedRideRequestsStatus(acceptedRideRequestIds, status = "active")
                val rideRequests = tripsRepository.fetchRideRequests(acceptedRideRequestIds)
                val pickupLocations = rideRequests.map { it.trip.startLocation }
                val dropOffLocations = rideRequests.map { it.trip.endLocation }
                val passengerIds = rideRequests.map { it.user.userId }
                val passengersCount = rideRequests.size
                val tripStartTime = Timestamp.now()

                val postedTripDetails = SharedTripService.getTripDetails()
                if (postedTripDetails != null) {
                    val driverStartLocation = postedTripDetails.startLocation
                    val tripPostedTime = postedTripDetails.scheduledTime

                    val rideData = mapOf(
                        "driverId" to driverId,
                        "passengerIds" to passengerIds,
                        "driverStartLocation" to driverStartLocation,
                        "pickupLocations" to pickupLocations,
                        "dropOffLocations" to dropOffLocations,
                        "tripStartTime" to tripStartTime,
                        "tripPostedTime" to tripPostedTime,
                        "passengersCount" to passengersCount,
                        "tripStatus" to "active"
                    )

                    withContext(Dispatchers.IO){
                        tripsRepository.addToRidesCollection(postedTripDocumentId, rideData)
                        tripsRepository.deletePostedTrip(postedTripDocumentId)
                    }

                    withContext(Dispatchers.Main) {
                        SharedTripService.clearPostedTripsDocumentId()
                        navigateToActiveRideActivity(tripStartTime.toString())
                        Log.d("TripFlow", "Ride has been successfully started and moved to 'rides' collection.")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("TripFlow", "Failed to retrieve posted trip details from local storage.")
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                     showToast("An error occured, unable to start ride at the moment")
                }
            }
        }
    }




    private fun rejectRideRequest(rideRequest: RideRequest) {
        val documentId = rideRequest.documentId
        if (documentId.isEmpty()) {
          showToast("Could not process your request. The ride has been cancelled by customer")
            return
        }

        tripsRepository.rejectRideRequest(
            documentId,
            onUpdateSuccess = {
                showToast("Request cancelled successfully")
                FCMSend().pushNotification(
                    requireContext(),
                    "/topics/${rideRequest.user.userId}",
                    "Notification",
                    "Your ride request is rejected"
                ) {}
                              },
            onUpdateFailure = { exception ->
                showToast("An error occurred, please try again later")
            }
        )
    }


    fun updateRideRequests(rideRequests: List<RideRequest>) {
        updateLogoAndTextVisibility(rideRequests.isEmpty())
        adapter.updateData(rideRequests)
    }
    private fun updateLogoAndTextVisibility(isEmpty: Boolean) {
        handleCarAnimation(isEmpty)
    }



    private fun handleCancelRequest() {
        val postedTripDocumentId = SharedTripService.getPostedTripsDocumentId()
        val requestedTripDocumentIds = SharedTripService.getRequestedTripsDocumentIds()



        tripsRepository.cancelRideAndResetRequests(
            postDocumentId = postedTripDocumentId,
            requestedDocumentIds = requestedTripDocumentIds,
            onSuccess = {
                showToast("Ride cancelled successfully")
                SharedTripService.clearPostedTripsDocumentId()
                SharedTripService.clearRequestedTripsDocumentIds()
                SharedTripService.clearAcceptedTrips()
                SharedTripService.clearAcceptedTripsDocumentIds()
                SharedTripService.clearLastKnownLocation()
                listener?.onRideCancellation()
                dismiss()
            },
            onFailure = { e ->
                showToast("An error occurred, please try again later")
            }
        )
    }

    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Request")
            .setMessage("Are you sure you want to cancel the ride request?")
            .setPositiveButton("Yes") { dialog, which ->
                handleCancelRequest()
            }
            .setNegativeButton("No", null)
            .show()
    }
    interface RideRequestDialogListener {
        fun onRideCancellation()
    }

    private var listener: RideRequestDialogListener? = null

    fun setRideRequestDialogListener(listener: RideRequestDialogListener) {
        this.listener = listener
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToActiveRideActivity(currentRideTime: String) {
        val intent = Intent(requireContext(), ActiveRideActivity::class.java)
        intent.putExtra("currentRideTime",currentRideTime)
        startActivity(intent)
        requireActivity().finish()
    }


}
