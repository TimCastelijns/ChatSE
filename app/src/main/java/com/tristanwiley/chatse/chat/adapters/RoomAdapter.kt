package com.tristanwiley.chatse.chat.adapters

import android.app.AlertDialog
import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.squareup.okhttp.FormEncodingBuilder
import com.squareup.okhttp.Request
import com.tristanwiley.chatse.R
import com.tristanwiley.chatse.chat.ChatRoom
import com.tristanwiley.chatse.network.ClientManager
import org.jetbrains.anko.doAsync

/**
 * An adapter used to display the rooms in the left NavigationDrawer of the activity
 *
 * @param site: String that is the site (SO or SE)
 * @param list: MutableList of rooms
 * @param context: Application Context
 */
class RoomAdapter(val site: String, val list: MutableList<com.tristanwiley.chatse.chat.Room>, val context: Context) : RecyclerView.Adapter<com.tristanwiley.chatse.chat.adapters.RoomAdapter.ListRowHolder>() {

    override fun onBindViewHolder(viewHolder: com.tristanwiley.chatse.chat.adapters.RoomAdapter.ListRowHolder?, position: Int) {
        val room = list[position]
        val holder = viewHolder
        holder?.bindMessage(room)
    }

    override fun getItemCount() = list.size


    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): com.tristanwiley.chatse.chat.adapters.RoomAdapter.ListRowHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.drawer_list_item, parent, false)
        return com.tristanwiley.chatse.chat.adapters.RoomAdapter.ListRowHolder(context, view, site)
    }

    //Set the room to the RecyclerView
    class ListRowHolder(val mContext: Context, itemView: View, val site: String) : RecyclerView.ViewHolder(itemView) {
        val name = itemView.findViewById<TextView>(R.id.room_name)

        fun bindMessage(room: com.tristanwiley.chatse.chat.Room) {

            //Set the text to the itemView TextView
            name.text = room.name

            //OnClick, load the chat fragment
            itemView.setOnClickListener {
                val roomNum = room.roomID.toInt()
                (mContext as com.tristanwiley.chatse.chat.ChatActivity).loadChatFragment(ChatRoom(site, roomNum))
            }

            /*
             On Long click, create an AlertDialog to allow the user to remove/add from/to favorites
             or to leave the room
            */
            itemView.setOnLongClickListener {
                val favoriteToggleString: String

                //Determine if already a favorite and if I should remove or add
                if (room.isFavorite) {
                    favoriteToggleString = "Remove from Favorites"
                } else {
                    favoriteToggleString = "Add to Favorites"
                }

                //Create AlertDialog, set the title, message, and three buttons. One for each action
                AlertDialog.Builder(mContext)
                        .setTitle("Modify Room")
                        .setMessage("Would you like to modify room #${room.roomID}, ${room.name}?")
                        .setPositiveButton("Leave Room", { dialog, _ ->
                            leaveRoom(room.roomID, room.fkey)
                            dialog.dismiss()
                        })
                        .setNegativeButton(favoriteToggleString, { dialog, _ ->
                            toggleFavoriteRoom(room, room.fkey)
                            dialog.dismiss()
                        })
                        .setNeutralButton("Cancel", { dialog, _ ->
                            dialog.cancel()
                        })
                        .show()
                true
            }
        }

        /**
         * Function to make network call to leave room
         * @param roomID: ID of current room
         * @param fkey: Magic. F. Key.
         */
        fun leaveRoom(roomID: Long, fkey: String) {
            doAsync {
                val client = ClientManager.client

                val soRequestBody = FormEncodingBuilder()
                        .add("fkey", fkey)
                        .add("quiet", "true")
                        .build()
                val soChatPageRequest = Request.Builder()
                        .url(site + "/chats/leave/" + roomID)
                        .post(soRequestBody)
                        .build()
                client.newCall(soChatPageRequest).execute()
            }
        }

        /**
         * Call for add and removing from/to favorites is the same
         * @param room: Current Room object
         * @param fkey: ooooooooooh glorious fkey
         */
        fun toggleFavoriteRoom(room: com.tristanwiley.chatse.chat.Room, fkey: String) {
            room.isFavorite = !room.isFavorite

            doAsync {
                val client = ClientManager.client

                val soRequestBody = FormEncodingBuilder()
                        .add("fkey", fkey)
                        .add("roomId", room.roomID.toString())
                        .build()
                val soChatPageRequest = Request.Builder()
                        .url(site + "/rooms/favorite/")
                        .post(soRequestBody)
                        .build()
                client.newCall(soChatPageRequest).execute()
            }
        }

    }

}