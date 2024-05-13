import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.google.firebase.firestore.GeoPoint
import java.util.Locale
import android.location.Geocoder
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.mydmucabapp_driver.databinding.ItemActiveRidesBinding

class AcceptedRidesAdapter(
    private var rideRequests: MutableList<RideRequest>,
    private val onDropOffPassenger: (RideRequest) -> Unit,
    private val onStartChatClick: (RideRequest) -> Unit

) : RecyclerView.Adapter<AcceptedRidesAdapter.AcceptedRideViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AcceptedRideViewHolder {
        val binding =
            ItemActiveRidesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AcceptedRideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AcceptedRideViewHolder, position: Int) {
        val rideRequest = rideRequests[position]
        holder.bind(rideRequest, onStartChatClick, onDropOffPassenger)
    }

    override fun getItemCount(): Int = rideRequests.size



    class AcceptedRideViewHolder(val binding: ItemActiveRidesBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            rideRequest: RideRequest,
            onStartChatClick: ((RideRequest) -> Unit)?,
            onDropOffPassenger: ((RideRequest) -> Unit)?
        ) {
            with(binding) {


                Glide.with(ProfilePicture.context).load(rideRequest.user.profileImageUrl)
                    .into(ProfilePicture)

                PassengerName.text = rideRequest.user.name.toUpperCase(Locale.getDefault())

                //  textViewAmount.text = ""
                StartLocation.text = getAddressFromLocation(rideRequest.trip.startLocation)
                EndLocation.text = getAddressFromLocation(rideRequest.trip.endLocation)
                textViewPhoneNumber.text = rideRequest.user.phone


                binding.layoutExpanded.visibility = View.GONE


                binding.layoutCollapsed.setOnClickListener {
                    val isCurrentlyExpanded = binding.layoutExpanded.visibility == View.VISIBLE
                    binding.layoutExpanded.visibility = if (isCurrentlyExpanded) View.GONE else View.VISIBLE
                }


               btnDropOffPassenger.setOnClickListener{
                   onDropOffPassenger?.invoke(rideRequest)
               }

                chatIcon.setOnClickListener {
                    onStartChatClick?.invoke(rideRequest)
                }


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
