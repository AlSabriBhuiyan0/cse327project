

package com.google.ai.edge.gallery.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.theme.customColors

@Composable
fun getTaskBgColor(task: Task): Color {
  val colorIndex: Int = task.index % MaterialTheme.customColors.taskBgColors.size
  return MaterialTheme.customColors.taskBgColors[colorIndex]
}

@Composable
fun getTaskIconColor(task: Task): Color {
  val colorIndex: Int = task.index % MaterialTheme.customColors.taskIconColors.size
  return MaterialTheme.customColors.taskIconColors[colorIndex]
}

@Composable
fun getTaskIconColor(index: Int): Color {
  val colorIndex: Int = index % MaterialTheme.customColors.taskIconColors.size
  return MaterialTheme.customColors.taskIconColors[colorIndex]
}
