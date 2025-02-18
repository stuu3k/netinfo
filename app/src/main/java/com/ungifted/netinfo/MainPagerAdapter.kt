package com.ungifted.netinfo

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 5 // Total number of pages

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MainFragment.newInstance("HOME", "HOME", "HOME", "HOME")
            1 -> MainFragment.newInstance("CLOUDFLARE", "1.1.1.1", "1.0.0.1", "cloudflare.com")
            2 -> MainFragment.newInstance("GOOGLE", "8.8.8.8", "8.8.4.4", "google.com")
            3 -> MainFragment.newInstance("LOCAL", "Services", "CustomPing", "")
            4 -> MainFragment.newInstance("CUSTOM", "Continuous Ping", "", "")
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
} 