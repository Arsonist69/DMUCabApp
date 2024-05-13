package com.example.mydmucabapp_passenger.adopters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mydmucabapp_passenger.databinding.ItemRideHistoryBinding
import com.example.mydmucabapp_passenger.model.DataClass.RideHistory

class RidesHistoryAdapter(val context: Context, var rides: List<RideHistory>): RecyclerView.Adapter<RidesHistoryAdapter.ViewHolder>() {

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
        fun bindItem(item: RideHistory){
            binding.txtDate.text = item.rideDate
            binding.txtPickup.text = "Pickup: ${item.pickupLocation}"
            binding.txtDropOff.text = "DropOff: ${item.dropOffLocation}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemRideHistoryBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun getItemCount(): Int {
        return rides.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItem(rides[position])
        holder.itemView.setOnClickListener {
            if (mListener != null){
                mListener!!.onClick(rides[position])
            }
        }
    }
}