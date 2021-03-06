package one.mixin.android.ui.conversation.adapter

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.item_contact_normal.view.*
import one.mixin.android.R
import one.mixin.android.vo.User

class FriendAdapter : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    var friends: List<User>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var listener: FriendListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FriendViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_contact_normal,
            parent, false))

    override fun getItemCount() = friends?.size ?: 0

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        if (friends == null || friends!!.isEmpty()) return

        val u = friends!![position]
        holder.bind(u, listener)
    }

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: User, listener: FriendListener?) {
            itemView.normal.text = item.fullName
            itemView.avatar.setInfo(item.fullName, item.avatarUrl, item.userId)
            itemView.setOnClickListener {
                listener?.onFriendClick(item)
            }
            itemView.bot_iv.visibility = if (item.appId != null) View.VISIBLE else View.GONE
            itemView.verified_iv.visibility = if (item.isVerified == true) View.VISIBLE else View.GONE
        }
    }

    interface FriendListener {
        fun onFriendClick(user: User)
    }
}