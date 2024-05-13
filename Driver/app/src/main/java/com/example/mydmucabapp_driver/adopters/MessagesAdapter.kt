package com.example.mydmucabapp_driver.adopters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mydmucabapp_driver.databinding.ItemReceiverMessagesBinding
import com.example.mydmucabapp_driver.databinding.ItemSenderMessagesBinding
import com.example.mydmucabapp_driver.model.DataClass.ChatMessage

class MessagesAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) {
            VIEW_TYPE_SENDER
        } else {
            VIEW_TYPE_RECEIVER
        }
    }


    fun updateMessages(newMessages: MutableList<ChatMessage>) {
       messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENDER) {
            val binding = ItemSenderMessagesBinding.inflate(inflater, parent, false)
            SenderViewHolder(binding)
        } else {
            val binding = ItemReceiverMessagesBinding.inflate(inflater, parent, false)
            ReceiverViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SenderViewHolder) {
            holder.bind(message)
        } else if (holder is ReceiverViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    class SenderViewHolder(private val binding: ItemSenderMessagesBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.textViewMessageBody.text = message.message
            binding.textViewMessageTime.text = message.timestamp.toString()
        }
    }

    class ReceiverViewHolder(private val binding: ItemReceiverMessagesBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.textViewMessageBody.text = message.message
binding.textViewMessageTime.text = message.timestamp.toString()
        }
    }

    companion object {
        private const val VIEW_TYPE_SENDER = 1
        private const val VIEW_TYPE_RECEIVER = 2
    }
}
