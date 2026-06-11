package com.nordic.mediahub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AudiobookFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_audiobook, container, false)

        val audiobookList = view.findViewById<RecyclerView>(R.id.audiobook_list)

        val books = listOf(
            Audiobook("1", "三体", "刘慈欣", "已听 45%"),
            Audiobook("2", "活着", "余华", "已听 78%"),
            Audiobook("3", "解忧杂货店", "东野圭吾", "已听 12%"),
            Audiobook("4", "白夜行", "东野圭吾", "已听 90%")
        )

        audiobookList.layoutManager = LinearLayoutManager(requireContext())
        audiobookList.adapter = AudiobookAdapter(books)

        return view
    }
}
