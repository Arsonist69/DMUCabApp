import android.location.Geocoder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.mydmucabapp_driver.helpers.SharedTripService
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mydmucabapp_driver.databinding.ItemRideRequestBinding
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.google.firebase.firestore.GeoPoint
import java.util.Locale

class RideRequestsAdapter(private var rideRequests: List<RideRequest>) :
    RecyclerView.Adapter<RideRequestsAdapter.RideRequestViewHolder>() {

    var onAcceptClick: ((RideRequest) -> Unit)? = null
    var onRejectClick: ((RideRequest) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideRequestViewHolder {
        val binding = ItemRideRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RideRequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RideRequestViewHolder, position: Int) {
        val rideRequest = rideRequests[position]
        holder.bind(rideRequest, onAcceptClick, onRejectClick)
        holder.binding.acceptRide.isEnabled = !rideRequest.isProcessed
        holder.binding.rejectRide.isEnabled = !rideRequest.isProcessed
    }

    override fun getItemCount(): Int = rideRequests.size

    fun updateData(newRideRequests: List<RideRequest>) {
        this.rideRequests = newRideRequests
        notifyDataSetChanged()
    }

    class RideRequestViewHolder(val binding: ItemRideRequestBinding) : RecyclerView.ViewHolder(binding.root) {


        fun bind(
            rideRequest: RideRequest,
            onAcceptClick: ((RideRequest) -> Unit)?,
            onRejectClick: ((RideRequest) -> Unit)?
        ) {
            with(binding) {
                Glide.with(ProfilePicture.context).load(rideRequest.user.profileImageUrl).into(ProfilePicture)

                textViewPhoneNumber.text = rideRequest.user.phone
                textViewPassengerName.text = rideRequest.user.name
                textViewPickUpLocation.text = getAddressFromLocation(rideRequest.trip.startLocation)
                textViewDropOffLocation.text = getAddressFromLocation(rideRequest.trip.endLocation)

                binding.layoutExpanded.visibility = View.GONE


                binding.layoutCollapsed.setOnClickListener {
                    val isCurrentlyExpanded = binding.layoutExpanded.visibility == View.VISIBLE
                    binding.layoutExpanded.visibility = if (isCurrentlyExpanded) View.GONE else View.VISIBLE
                }

                acceptRide.setOnClickListener { onAcceptClick?.invoke(rideRequest) }
                rejectRide.setOnClickListener { onRejectClick?.invoke(rideRequest) }
            }
        }

        private fun getAddressFromLocation(location: GeoPoint): String {
            val geocoder = Geocoder(itemView.context, Locale.getDefault())
            return try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
            } catch (e: Exception) {
                "Unknown Location"
            }
        }
    }
}
