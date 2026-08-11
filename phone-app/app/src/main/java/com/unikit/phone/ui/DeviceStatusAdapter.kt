package com.unikit.phone.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.unikit.phone.R
import com.unikit.phone.model.DeviceStatusInfo

class DeviceStatusAdapter : RecyclerView.Adapter<DeviceStatusAdapter.ViewHolder>() {

    private var items: List<DeviceStatusInfo> = emptyList()

    fun submitList(newItems: List<DeviceStatusInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_status, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val textName = itemView.findViewById<TextView>(R.id.textDeviceName)
        private val textMeta = itemView.findViewById<TextView>(R.id.textDeviceMeta)
        private val textState = itemView.findViewById<TextView>(R.id.textDeviceState)

        fun bind(device: DeviceStatusInfo) {
            textName.text = device.device
            textMeta.text = "last seen ${device.lastSeen ?: "--"}  ·  quality ${device.quality ?: "--"}" +
                if (device.simulated) "  ·  simulated" else ""
            textState.text = device.state
            val colorRes = if (device.state == "CONNECTED") R.color.status_connected else R.color.status_disconnected
            textState.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
        }
    }
}
