package com.example.mydmucabapp_driver.adopters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mydmucabapp_driver.databinding.ItemRideHistoryBinding
import com.example.mydmucabapp_driver.model.DataClass.RideHistory

class RidesHistoryAdapter(val context: Context, var rides: List<RideHistory>, val isScheduled: Boolean = false): RecyclerView.Adapter<RidesHistoryAdapter.ViewHolder>() {

    private var mListener: OnClick? = null

    interface OnClick{
        fun onClick(item: RideHistory)
    }

    fun setOnClick(listener: OnClick){
        this.mListener = listener
    }

    fun setNewList(newList: List<RideHistory>){
        rides = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemRideHistoryBinding): RecyclerView.ViewHolder(binding.root){
        fun bindItem(item: RideHistory, isScheduled: Boolean){
            binding.txtDate.text = item.rideDate
            binding.txtStart.text = "Start Location: ${item.startLocation}"
            binding.txtEnd.text = "End Location: ${item.endLocation}"
            binding.txtPassengerCount.text = if (isScheduled){
                "Available Seats: ${item.passengersCount}"
            }else{
                "Total Passengers: ${item.passengersCount}"
            }
            binding.root.setOnClickListener {
                if (mListener != null){
                    mListener!!.onClick(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemRideHistoryBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun getItemCount(): Int {
        return rides.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItem(rides[position], isScheduled)
    }
}