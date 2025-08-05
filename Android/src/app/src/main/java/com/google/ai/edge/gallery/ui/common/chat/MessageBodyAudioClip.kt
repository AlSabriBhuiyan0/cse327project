

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MessageBodyAudioClip(message: ChatMessageAudioClip, modifier: Modifier = Modifier) {
  AudioPlaybackPanel(
    audioData = message.audioData,
    sampleRate = message.sampleRate,
    isRecording = false,
    modifier = Modifier.padding(end = 16.dp),
    onDarkBg = true,
  )
}
