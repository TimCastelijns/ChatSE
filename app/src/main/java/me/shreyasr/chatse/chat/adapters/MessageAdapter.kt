package me.shreyasr.chatse.chat.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.koushikdutta.ion.Ion
import com.orhanobut.dialogplus.DialogPlus
import com.orhanobut.dialogplus.ListHolder
import com.squareup.okhttp.FormEncodingBuilder
import com.squareup.okhttp.Request
import kotlinx.android.synthetic.main.list_item_message.view.*
import me.shreyasr.chatse.R
import me.shreyasr.chatse.chat.ChatRoom
import me.shreyasr.chatse.event.EventList
import me.shreyasr.chatse.event.presenter.message.MessageEvent
import me.shreyasr.chatse.network.Client
import me.shreyasr.chatse.network.ClientManager
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MessageAdapter(val mContext: Context, val events: EventList, val chatFkey: String?, val room: ChatRoom?, var messages: ArrayList<MessageEvent> = ArrayList()) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    override fun onBindViewHolder(viewHolder: MessageViewHolder?, pos: Int) {
        val message = messages[pos]
        val holder = viewHolder as MessageViewHolder
        holder.bindMessage(message)
    }

    fun update() {
        messages.clear()
        messages.addAll(events.messagePresenter.getEventsList())
        this.notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.list_item_message, parent, false)
        return MessageViewHolder(mContext, view, chatFkey, room)
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(val mContext: Context, itemView: View, val chatFkey: String?, val room: ChatRoom?) : RecyclerView.ViewHolder(itemView) {
        val timestampFormat = SimpleDateFormat("hh:mm aa", Locale.getDefault())
        val messageView = itemView.findViewById(R.id.message_content) as TextView
        val userNameView = itemView.findViewById(R.id.message_user_name) as TextView
        val messageTimestamp = itemView.findViewById(R.id.message_timestamp) as TextView
        val editIndicator = itemView.findViewById(R.id.message_edit_indicator) as ImageView
        val starIndicator = itemView.findViewById(R.id.message_star_indicator) as ImageView
        val starCount = itemView.findViewById(R.id.message_star_count) as TextView
        val oneboxImage = itemView.findViewById(R.id.message_image) as ImageView

        fun bindMessage(message: MessageEvent) {
            oneboxImage.visibility = View.GONE
            starIndicator.visibility = View.GONE
            if (message.message_starred) {
                starIndicator.visibility = View.VISIBLE
                starCount.visibility = View.VISIBLE
                starCount.text = message.message_stars.toString()
            }
            if (message.isDeleted) {
                messageView.setTextColor(ContextCompat.getColor(itemView.context, R.color.deleted))
                messageView.text = itemView.context.getString(R.string.removed)
            } else {
                if (!message.onebox) {
                    messageView.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_text))
                    messageView.text = message.content
                    //If Android version is 24 and above use the updated version, otherwise use the deprecated version
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        messageView.text = Html.fromHtml(message.content, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        @Suppress("DEPRECATION")
                        messageView.text = Html.fromHtml(message.content)
                    }
                } else {
                    when (message.onebox_type) {
                        "image" -> {
                            oneboxImage.visibility = View.VISIBLE

                            Ion.with(itemView.context)
                                    .load(message.onebox_content)
                                    .intoImageView(itemView.message_image)
                            messageView.text = ""
                        }
                        "youtube" -> {
                            itemView.setOnClickListener {
                                mContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.onebox_extra)))
                            }
                            oneboxImage.visibility = View.VISIBLE

                            Ion.with(itemView.context)
                                    .load(message.onebox_content)
                                    .intoImageView(itemView.message_image)
                            messageView.text = message.content
                        }
                        else -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                messageView.text = Html.fromHtml(message.content, Html.FROM_HTML_MODE_LEGACY)
                            } else {
                                @Suppress("DEPRECATION")
                                messageView.text = Html.fromHtml(message.content)
                            }
                        }
                    }
                }
            }

            itemView.setOnLongClickListener {
                val dialogMessages = mutableListOf<String>()
                val curUserId: Int
                val isUserMessage: Boolean

                if (room?.site == Client.SITE_STACK_OVERFLOW) {
                    curUserId = mContext.defaultSharedPreferences.getInt("SOID", -1)
                } else {
                    curUserId = mContext.defaultSharedPreferences.getInt("SEID", -1)
                }
                isUserMessage = curUserId == message.userId.toInt()
                if (curUserId != -1) {
                    if (isUserMessage) {
                        dialogMessages.add(mContext.getString(R.string.edit_message))
                        dialogMessages.add(mContext.getString(R.string.delete_message))
                    } else {
                        dialogMessages.add(mContext.getString(R.string.star_message))
                    }
                }
                val dialog = DialogPlus.newDialog(mContext)
                        .setContentHolder(ListHolder())
                        .setGravity(Gravity.CENTER)
                        .setAdapter(ModifyMessageAdapter(dialogMessages, mContext))
                        .setOnItemClickListener { plusDialog, item, _, position ->
                            if (isUserMessage) {
                                when (position) {
                                    0 -> {
                                        showEditDialog(message, mContext, plusDialog)
                                    }
                                    1 -> {
                                        deleteMessage(message.messageId, chatFkey)
                                        plusDialog.dismiss()
                                    }
                                }
                            } else {
                                when (position) {
                                    0 -> {
                                        starMessage(message.messageId, chatFkey)
                                        plusDialog.dismiss()
                                    }
                                }
                            }
                        }
                        .setPadding(50, 50, 50, 50)
                        .create()

                dialog.show()
                true
            }

            userNameView.text = message.userName
            messageTimestamp.text = timestampFormat.format(Date(message.timestamp * 1000))
            editIndicator.visibility = if (message.isEdited) View.VISIBLE else View.INVISIBLE
        }

        fun starMessage(messageId: Int, chatFkey: String?) {
            val client = ClientManager.client
            doAsync {
                val soLoginRequestBody = FormEncodingBuilder()
                        .add("fkey", chatFkey)
                        .build()
                val soChatPageRequest = Request.Builder()
                        .url("https://chat.stackoverflow.com/messages/$messageId/star")
                        .post(soLoginRequestBody)
                        .build()
                client.newCall(soChatPageRequest).execute()
            }
        }

        fun deleteMessage(messageId: Int, chatFkey: String?) {
            val client = ClientManager.client
            doAsync {
                val soLoginRequestBody = FormEncodingBuilder()
                        .add("fkey", chatFkey)
                        .build()
                val soChatPageRequest = Request.Builder()
                        .url("https://chat.stackoverflow.com/messages/$messageId/delete")
                        .post(soLoginRequestBody)
                        .build()
                client.newCall(soChatPageRequest).execute()
            }
        }

        fun showEditDialog(message: MessageEvent, mContext: Context, plusDialog: DialogPlus) {
            val builder = AlertDialog.Builder(mContext)
            builder.setTitle("Edit message")

            val l = LinearLayout(mContext)
            l.setPadding(14, 14, 14, 14)
            val input = EditText(mContext)
            input.setText(message.content, TextView.BufferType.EDITABLE)
            input.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            l.addView(input)
            builder.setView(l)

            builder.setPositiveButton("OK", { dialog, _ ->
                editMessage(input.text.toString(), message.messageId, chatFkey)
                dialog.dismiss()
                plusDialog.dismiss()
            })
            builder.setNegativeButton("Cancel", { dialog, _ ->
                dialog.cancel()
            })

            builder.show()

        }

        fun editMessage(editText: String, messageId: Int, chatFkey: String?) {
            val client = ClientManager.client
            doAsync {
                val soLoginRequestBody = FormEncodingBuilder()
                        .add("text", editText)
                        .add("fkey", chatFkey)
                        .build()
                val soChatPageRequest = Request.Builder()
                        .url("https://chat.stackoverflow.com/messages/$messageId/")
                        .post(soLoginRequestBody)
                        .build()
                client.newCall(soChatPageRequest).execute()
            }
        }
    }
}