//package com.nearnet
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.TextView
//import androidx.recyclerview.widget.RecyclerView
//import com.nearnet.sessionlayer.R
//import com.nearnet.sessionlayer.data.model.Message
//
//class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
//
//    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val txtMessage: TextView = view.findViewById(R.id.txtMessage)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
//        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
//        return MessageViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
//        val msg = messages[position]
//        holder.txtMessage.text = "${msg.username}: ${msg.message}"
//    }
//
//    override fun getItemCount(): Int = messages.size
//}
