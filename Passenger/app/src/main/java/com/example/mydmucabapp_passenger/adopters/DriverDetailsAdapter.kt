import android.location.Geocoder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mydmucabapp_passenger.databinding.ItemRideBinding
import com.example.mydmucabapp_passenger.model.DataClass.RideRequest
import com.google.firebase.firestore.GeoPoint
import java.util.Locale

class DriverDetailsAdapter(private var rides:  MutableList<RideRequest>, private var isRequested: Boolean = false, private var isAccepted: Boolean = false) : RecyclerView.Adapter<DriverDetailsAdapter.RideViewHolder>() {

    var onItemClick: ((RideRequest) -> Unit)? = null
    var onContinueClick: ((RideRequest) -> Unit)? = null



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val binding = ItemRideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RideViewHolder(binding, onItemClick, onContinueClick)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        val ride = rides[position]
        holder.bind(ride, isRequested, isAccepted)

    }



    override fun getItemCount(): Int = rides.size




    class RideViewHolder(
        private val binding: ItemRideBinding,
        private val onItemClick: ((RideRequest) -> Unit)?,
        private val onContinueClick: ((RideRequest) -> Unit)?,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ride: RideRequest, isRequested: Boolean, isAccepted: Boolean) {
            with(binding) {
                textViewDriverName.text = ride.user.name
                Glide.with(driverProfilePicture.context).load(ride.user.profileImageUrl).into(driverProfilePicture)

                val geocoder = Geocoder(itemView.context, Locale.getDefault())
                textViewDriverPhoneNumber.text = ride.user.phone
                textViewDriverStatLocation.text = getAddressFromLocation(geocoder, ride.trip.startLocation)
                textViewDriverEndLocation.text = getAddressFromLocation(geocoder, ride.trip.endLocation)
                textViewAvailableSeats.text = ride.trip.availableSeats.toString()

                if (isRequested){
                    binding.requestRide.visibility = View.GONE
                }else if (isAccepted){
                    binding.requestRide.visibility = View.GONE
                    binding.continueRide.visibility = View.VISIBLE
                }

                binding.requestRide.setOnClickListener {
                     onItemClick?.invoke(ride)
                }

                binding.continueRide.setOnClickListener {
                     onContinueClick?.invoke(ride)
                }

                binding.layoutExpanded.visibility = View.GONE

                binding.layoutCollapsed.setOnClickListener {
                    binding.layoutExpanded.visibility = if (binding.layoutExpanded.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
        }



        private fun getAddressFromLocation(geocoder: Geocoder, location: GeoPoint): String {
            return try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
            } catch (e: Exception) {
                "Unknown Location"
            }
        }
    }


    fun removeTrip(deletedTripId: String) {
        val index = rides.indexOfFirst { it.documentId == deletedTripId }
        if (index >= 0) {
            rides.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}
