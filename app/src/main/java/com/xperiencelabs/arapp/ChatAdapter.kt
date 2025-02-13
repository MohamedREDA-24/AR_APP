package com.xperiencelabs.arapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TEXT_SENT = 1
        private const val VIEW_TYPE_TEXT_REPLY = 2
        private const val VIEW_TYPE_IMAGE = 3
        private const val BASE_IMAGE_URL = "http://13.92.86.232/static/" // Use your server URL
    }

    // ViewHolder for text messages sent by the user
    inner class TextSentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.message_text)
    }

    // ViewHolder for text replies (bot responses)
    inner class TextReplyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.message_text)
    }

    // ViewHolder for image messages
    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chatImage: ImageView = itemView.findViewById(R.id.chat_image)
    }

    override fun getItemViewType(position: Int): Int {
        val chatMessage = messages[position]
        return if (chatMessage.imageUrl != null) {
            VIEW_TYPE_IMAGE
        } else {
            if (chatMessage.isReply) VIEW_TYPE_TEXT_REPLY else VIEW_TYPE_TEXT_SENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TEXT_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_sent, parent, false)
                TextSentViewHolder(view)
            }
            VIEW_TYPE_TEXT_REPLY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_received, parent, false)
                TextReplyViewHolder(view)
            }
            VIEW_TYPE_IMAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_image, parent, false)
                ImageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatMessage = messages[position]
        when (holder) {
            is TextSentViewHolder -> {
                holder.messageText.text = chatMessage.message
            }
            is TextReplyViewHolder -> {
                holder.messageText.text = chatMessage.message
            }
            is ImageViewHolder -> {
                // Construct full image URL from server response
                val imageUrl = BASE_IMAGE_URL + chatMessage.imageUrl

                Glide.with(holder.itemView.context)
                    .load(imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade()) // Smooth transition
//                    .placeholder(R.drawable.placeholder_image) // Optional: Add a placeholder
//                    .error(R.drawable.error_placeholder) // Optional: Add error fallback
                    .into(holder.chatImage)
            }
        }
    }

    override fun getItemCount(): Int = messages.size
}
