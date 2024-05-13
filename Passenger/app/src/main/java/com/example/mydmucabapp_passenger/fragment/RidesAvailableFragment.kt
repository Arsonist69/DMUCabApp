import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mydmucabapp_passenger.controller.RideActive
import com.example.mydmucabapp_passenger.databinding.FragmentDriverDetailsDialogBinding
import com.example.mydmucabapp_passenger.helpers.SharedTripService
import com.example.mydmucabapp_passenger.model.DataClass.RideRequest
import com.example.mydmucabapp_passenger.model.DataHandling.TripsRepository
import com.example.mydmucabapp_passenger.model.DataHandling.UserRepository
import com.example.mydmucabapp_passenger.utils.FCMSend


class DriverDetailsDialogFragment : DialogFragment() {
    private var _binding: FragmentDriverDetailsDialogBinding? = null
    private val binding get() = _binding!!
    private val user = UserRepository();
    private val trips = TripsRepository();
    private var isStatusDialogShown = false

    companion object {
        private const val RIDES_KEY = "rides"

        fun newInstance(rides: ArrayList<RideRequest>): DriverDetailsDialogFragment {
            val args = Bundle()
            args.putSerializable(RIDES_KEY, rides)
            val fragment = DriverDetailsDialogFragment().apply {
                arguments = args
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = false
        _binding = FragmentDriverDetailsDialogBinding.inflate(inflater, container, false)
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


        val rides =
            arguments?.getSerializable(RIDES_KEY) as? ArrayList<RideRequest> ?: arrayListOf()

        val adapter =
            DriverDetailsAdapter(rides)



        trips.listenForTripChanges(object : TripsRepository.TripDataChangeListener {
            override fun onTripAdded(trip: RideRequest) {
            }

            override fun onTripRemoved(tripId: String) {
                adapter.removeTrip(tripId)
            }

            override fun onTripModified(trip: RideRequest) {
            }

            override fun onError(e: Exception) {
                Log.e("TAG", "Error listening for trip changes", e)
            }
        })


        adapter.onItemClick = { ride ->

           SharedTripService.setRideDetails(ride)

            val driverId = ride.trip.driverId
            Log.d("driverId", "driverId = $driverId ")
            val existingDocumentId = SharedTripService.getDocumentId()
            val tripDetails = SharedTripService.getTripDetails()?.copy(
                driverId = driverId,
                requestStatus = "requested"
            )

             SharedTripService.setDriverId(driverId)

            tripDetails?.let { trip ->
                if (existingDocumentId.isEmpty()) {
                    trips.storeImmediateTripsInFirestore(trip,
                        onSuccess = { documentId ->
                            SharedTripService.setDocumentId(documentId)
                            setupRideRequestStatusListener(documentId)
                            FCMSend().pushNotification(
                                requireContext(),
                                "/topics/${trip.driverId}",
                                "Notification",
                                "A passenger requested you for a seat in your ride."
                            ) {}
                        },
                        onFailure = { exception ->
                            Toast.makeText(
                                requireContext(),
                                "Failed to send ride request: ${exception.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    trips.updateRideRequest(existingDocumentId, trip,
                        onSuccess = {
                            setupRideRequestStatusListener(existingDocumentId)

                        },
                        onFailure = { exception ->
                            Toast.makeText(
                                requireContext(),
                                "Failed to update ride request: ${exception.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            } ?: run {
                Toast.makeText(
                    requireContext(),
                    "Error retrieving trip details.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }



        binding.btnCancel.setOnClickListener {
            showCancelConfirmationDialog()

        }

        binding.recyclerViewDrivers.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewDrivers.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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


    private fun handleCancelRequest() {
        val currentTripDocumentId = SharedTripService.getDocumentId()

        if (currentTripDocumentId.isEmpty()) {
            dismiss()
            return
        }

        trips.deleteRideRequest(currentTripDocumentId, onSuccess = {
            Toast.makeText(context, "Ride request canceled.", Toast.LENGTH_SHORT).show()
            SharedTripService.clearDocumentId()
            SharedTripService.clearTripDetails()
            SharedTripService.clearDriverId()
            dismiss()
        },
            onFailure = { e ->
                Toast.makeText(
                    context,
                    "Failed to cancel ride request: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            })
    }

    private fun setupRideRequestStatusListener(documentId: String) {
        trips.listenForRideRequestStatus(documentId, object : TripsRepository.RideRequestStatusListener {
            override fun onStatusChange(status: String) {
                if (!isStatusDialogShown) {
                    showStatusDialog(status)
                }
            }

            override fun onError(e: Exception) {
                Log.e("DriverDetailsDialogFragment", "Error listening for status change: ", e)
            }
        })
    }

    private fun showStatusDialog(status: String) {
        if (!isAdded) {
            Log.e("DialogError", "Fragment not attached to an activity.")
            return
        }

        val message: String
        val shouldNavigate: Boolean

        when (status) {
            "accepted" -> {
                message = "Your ride request has been accepted. Your driver is on the way!"
                SharedTripService.clearTripDetails()
                shouldNavigate = true
            }
            "pending" -> {
                message = "Your ride request has been rejected. Please try requesting another ride."
                SharedTripService.clearRideDetails()
                SharedTripService.clearDriverId()
                shouldNavigate = false
            }
            else -> {
                message = "Status update: $status"
                shouldNavigate = false
            }
        }

        val context = requireContext() ?: return
        AlertDialog.Builder(context)
            .setTitle("Ride Request Update")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                if (shouldNavigate) {
                    val intent = Intent(context, RideActive::class.java)
                    startActivity(intent)
                    activity?.finish()
                }
            }
            .setOnDismissListener {
                isStatusDialogShown = false
            }
            .create()
            .show()

        isStatusDialogShown = true
    }


}

