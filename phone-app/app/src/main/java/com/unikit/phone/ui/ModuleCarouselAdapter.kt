package com.unikit.phone.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.unikit.phone.R

/** One entry in ExamHome's swipeable module carousel. */
data class ModuleCardSpec(
    val icon: String,
    val label: String,
    val subtitle: String,
    val hue: Hue,
    val factory: () -> androidx.fragment.app.Fragment,
) {
    enum class Hue(val tintRes: Int, val tintBgRes: Int) {
        BLUE(R.color.hue_blue, R.color.hue_blue_tint),
        SKY(R.color.hue_sky, R.color.hue_sky_tint),
        GREEN(R.color.hue_green, R.color.hue_green_tint),
    }
}

/**
 * Full-width-per-item RecyclerView adapter -- paired with PagerSnapHelper
 * in ExamHomeFragment to get the same "one big card per swipe" feel as the
 * HTML preview, without adding a viewpager2 dependency this project's
 * offline Gradle cache doesn't already have (recyclerview does).
 */
class ModuleCarouselAdapter(
    private val items: List<ModuleCardSpec>,
    private val onOpen: (ModuleCardSpec) -> Unit,
) : RecyclerView.Adapter<ModuleCarouselAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_module_card, parent, false)
        view.layoutParams = ViewGroup.LayoutParams(parent.width, ViewGroup.LayoutParams.MATCH_PARENT)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val iconFrame = itemView.findViewById<FrameLayout>(R.id.moduleIconFrame)
        private val icon = itemView.findViewById<TextView>(R.id.moduleIcon)
        private val name = itemView.findViewById<TextView>(R.id.moduleName)
        private val subtitle = itemView.findViewById<TextView>(R.id.moduleSubtitle)
        private val badge = itemView.findViewById<TextView>(R.id.moduleViewBadge)

        fun bind(spec: ModuleCardSpec) {
            val context = itemView.context
            icon.text = spec.icon
            name.text = spec.label
            subtitle.text = spec.subtitle

            val tint = ContextCompat.getColor(context, spec.hue.tintRes)
            val tintBg = ContextCompat.getColor(context, spec.hue.tintBgRes)
            iconFrame.backgroundTintList = ColorStateList.valueOf(tintBg)
            icon.setTextColor(tint)
            badge.backgroundTintList = ColorStateList.valueOf(tintBg)
            badge.setTextColor(tint)
        }
    }
}
