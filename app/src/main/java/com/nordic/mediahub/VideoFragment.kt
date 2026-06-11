package com.nordic.mediahub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VideoFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video, container, false)

        val videoList = view.findViewById<RecyclerView>(R.id.video_list)

        val videos = listOf(
            Video("1", "肖申克的救赎", "剧情 / 犯罪", "2:22:00"),
            Video("2", "阿甘正传", "剧情 / 爱情", "2:22:00"),
            Video("3", "星际穿越", "科幻 / 冒险", "2:49:00"),
            Video("4", "盗梦空间", "科幻 / 悬疑", "2:28:00"),
            Video("5", "泰坦尼克号", "爱情 / 灾难", "3:14:00"),
            Video("6", "教父", "剧情 / 犯罪", "2:55:00")
        )

        videoList.layoutManager = GridLayoutManager(requireContext(), 2)
        videoList.adapter = VideoAdapter(videos)

        return view
    }
}
