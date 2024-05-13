package com.example.mydmucabapp_passenger.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.mydmucabapp_passenger.adopters.RidesHistoryAdapter
import com.example.mydmucabapp_passenger.controller.TripDetailsActivity
import com.example.mydmucabapp_passenger.databinding.FragmentScheduledTripsBinding
import com.example.mydmucabapp_passenger.helpers.GeoPointDecoder
import com.example.mydmucabapp_passenger.model.DataClass.RideHistory
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat

class ScheduledTripsFragment : Fragment(), RidesHistoryAdapter.OnClick {

    private lateinit var binding: FragmentScheduledTripsBinding
    private lateinit var mAdapter: RidesHistoryAdapter
    private var dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm:ss a z")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentScheduledTripsBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAdapter = RidesHistoryAdapter(requireContext(), mutableListOf())
        mAdapter.setOnClick(this)
        binding.ridesHistoryRecycler.adapter = mAdapter

        val pId = FirebaseAuth.getInstance().currentUser?.uid
        val ridesRef = FirebaseFirestore.getInstance().collection("scheduledPassengerTrips")
        ridesRef.get().addOnSuccessListener {
            if (!it.isEmpty && it.documents.isNotEmpty()){
                val newList = mutableListOf<RideHistory>()
                for (d in it.documents) {
                    val passengerId = d.get("passengerId").toString()
                    if (pId == passengerId) {
                        val id = d.get("id").toString()
                        val timestamp: Timestamp? = d.getTimestamp("scheduledTime")
                        var rDate = timestamp.toString()
                        if (timestamp != null){
                            rDate = dateFormat.format(timestamp.toDate())
                        }
                        val rStart: GeoPoint? = d.getGeoPoint("startLocation")
                        val rDropoff: GeoPoint? = d.getGeoPoint("endLocation")
                        newList.add(
                            RideHistory(
                                rDate,
                                GeoPointDecoder.getGeoLocationName(requireContext(),rStart),
                                GeoPointDecoder.getGeoLocationName(requireContext(),rDropoff),
                                id
                            )
                        )
                    }
                }
                if (newList.isNotEmpty()){
                    mAdapter.setNewList(newList)
                }else{
                    binding.txtError.visibility = View.VISIBLE
                }
            }else{
                binding.txtError.visibility = View.VISIBLE
            }
            binding.progressBar.visibility = View.GONE
        }.addOnFailureListener {
            binding.txtError.text = it.message.toString()
            binding.progressBar.visibility = View.GONE
            binding.txtError.visibility = View.VISIBLE
        }

    }

    override fun onClick(item: RideHistory) {
        Intent(requireContext(),TripDetailsActivity::class.java).also {
            it.putExtra("id",item.id)
            startActivity(it)
        }
    }

}