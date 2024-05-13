package com.example.mydmucabapp_driver.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.adopters.RidesHistoryAdapter
import com.example.mydmucabapp_driver.databinding.FragmentRidesHistoryBinding
import com.example.mydmucabapp_driver.helpers.GeoPointDecoder
import com.example.mydmucabapp_driver.model.DataClass.RideHistory
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat
import java.util.Calendar

class RidesHistoryFragment : Fragment() {

    private lateinit var binding: FragmentRidesHistoryBinding
    private lateinit var mAdapter: RidesHistoryAdapter
    private var dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm:ss a z")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentRidesHistoryBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAdapter = RidesHistoryAdapter(requireContext(), mutableListOf())
        binding.ridesHistoryRecycler.adapter = mAdapter

        val dId = FirebaseAuth.getInstance().currentUser?.uid
        val ridesRef = FirebaseFirestore.getInstance().collection("rides")
        ridesRef.get().addOnSuccessListener {
            if (!it.isEmpty && it.documents.isNotEmpty()){
                val newList = mutableListOf<RideHistory>()
                for (d in it.documents) {
                    val driverId = d.get("driverId").toString()
                    val timestamp: Timestamp? = d.getTimestamp("tripStartTime")
                    var rDate = timestamp.toString()
                    if (timestamp != null){
                        rDate = dateFormat.format(timestamp.toDate())
                    }
                    val rStart: GeoPoint? = d.getGeoPoint("driverStartLocation")
                    val rEnd: GeoPoint? = d.getGeoPoint("driverEndLocation")
                    val rPassengersCount = d.get("passengersCount").toString()
                    val rStatus = d.get("tripStatus").toString()
                    if (dId == driverId && rStatus == "completed") {
                        newList.add(RideHistory(rDate,GeoPointDecoder.getGeoLocationName(requireContext(),rStart),GeoPointDecoder.getGeoLocationName(requireContext(),rEnd),rPassengersCount))
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

}