package com.example.mydmucabapp_passenger.model.DataClass

data class Chat(
    val participantIds: List<String?>
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = 0L
)
