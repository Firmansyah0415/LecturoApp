package com.lecturo.lecturo.ui.task

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class TasksPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        // Hantar hanya status 'isCompleted', bukan senarai data
        return when (position) {
            0 -> TasksFragment.newInstance(isCompleted = false)
            1 -> TasksFragment.newInstance(isCompleted = true)
            else -> throw IllegalStateException("Posisi tidak sah: $position")
        }
    }
}
