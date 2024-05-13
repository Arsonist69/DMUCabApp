package com.example.mydmucabapp_passenger.model.DataHandling

import android.util.Log
import com.example.mydmucabapp_passenger.model.DataClass.Chat
import com.example.mydmucabapp_passenger.model.DataClass.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MessageHandler {

    private val db = FirebaseFirestore.getInstance()

    fun startChat(participantIds: List<String?>, callback: (chatId: String) -> Unit) {
        val chat = Chat(participantIds)

        db.collection("Chats").add(chat).addOnSuccessListener { documentReference ->
            callback(documentReference.id)
        }
    }

    fun checkForExistingChat(participantIds: List<String?>, callback: (chatId: String?) -> Unit) {

        db.collection("Chats")
            .whereArrayContainsAny("participantIds", participantIds)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    callback(null)
                } else {
                    val chatId = documents.documents[0].id
                    callback(chatId)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("checkForExistingChat", "Error checking for existing chat: ", exception)
                callback(null)
            }
    }



    fun sendMessage(chatId: String, message: ChatMessage, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("Chats").document(chatId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun fetchChatMessages(chatId: String, callback: (MutableList<ChatMessage>) -> Unit) {
        db.collection("Chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ActiveRideActivity", "Fetch chat messages error: $e")
                    return@addSnapshotListener
                }

                val messages = mutableListOf<ChatMessage>()
                snapshot?.documents?.forEach { document ->
                    Log.d("ActiveRideActivity", "Document fetched: ${document.data}")
                    document.toObject(ChatMessage::class.java)?.let { message ->
                        messages.add(message)
                    }
                }

                Log.d("ActiveRideActivity", "Total messages fetched: ${messages.size}")
                callback(messages)
            }
    }




}
