

package com.google.ai.edge.gallery.ui.modelmanager

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
// import com.google.ai.edge.gallery.ui.preview.TASK_TEST1
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task

/** A screen to manage models. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManager(
  task: Task,
  viewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  onModelClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Set title based on the task.
  var title = "${task.type.label} model"
  if (task.models.size != 1) {
    title += "s"
  }
  // Model count.
  val modelCount by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.size
      } else {
        -1
      }
    }
  }

  // Navigate up when there are no models left.
  LaunchedEffect(modelCount) {
    if (modelCount == 0) {
      navigateUp()
    }
  }

  // Handle system's edge swipe.
  BackHandler { navigateUp() }

  Scaffold(
    modifier = modifier,
    topBar = {
      GalleryTopAppBar(
        title = title,
        leftAction = AppBarAction(actionType = AppBarActionType.NAVIGATE_UP, actionFn = navigateUp),
      )
    },
  ) { innerPadding ->
    ModelList(
      task = task,
      modelManagerViewModel = viewModel,
      contentPadding = innerPadding,
      onModelClicked = onModelClicked,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

// @Preview
// @Composable
// fun ModelManagerPreview() {
//   val context = LocalContext.current

//   GalleryTheme {
//     ModelManager(
//       viewModel = PreviewModelManagerViewModel(context = context),
//       onModelClicked = {},
//       task = TASK_TEST1,
//       navigateUp = {},
//     )
//   }
// }
