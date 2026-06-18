package com.nordic.mediahub.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiCopyEncodingTest {
    @Test
    fun mainUiSourceDoesNotContainCommonMojibakeMarkers() {
        val sourceRoot = File("src/main/java/com/nordic/mediahub")
        assertTrue("Expected Android source root to exist: ${sourceRoot.absolutePath}", sourceRoot.exists())

        val markers = listOf(
            "鈾",
            "鈻",
            "鈪",
            "鈱",
            "闊",
            "鏈",
            "瑙",
            "姝",
            "鎾",
            "绛",
            "鍔",
            "杩",
            "鏆",
            "瀵",
            "閿",
            "辫",
            "嗛",
            "戙€"
        )

        val offenders = sourceRoot.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    (file.invariantSeparatorsPath.contains("/ui/") || file.name == "MainActivity.kt")
            }
            .flatMap { file ->
                val text = file.readText(Charsets.UTF_8)
                markers
                    .filter { marker -> text.contains(marker) }
                    .map { marker -> "${file.relativeTo(sourceRoot)} contains $marker" }
            }
            .toList()

        assertTrue(
            "Found likely mojibake in UI source:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }
}
