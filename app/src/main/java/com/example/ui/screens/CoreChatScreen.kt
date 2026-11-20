package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.db.ChatMessageEntity
import com.example.data.prefs.JarvisSettings
import com.example.i18n.JarvisStrings
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.ChatMessageCard
import com.example.ui.components.CoreVisualMode
import com.example.ui.components.HudPanel
import com.example.ui.components.MiniSpeakingWaveBadge
import com.example.ui.components.VoiceWaveformMicButton
import com.example.ui.theme.LocalJarvisHudColors
import com.example.viewmodel.AiUiState

@Composable
fun CoreChatScreen(
    strings: JarvisStrings,
    settings: JarvisSettings,
    messages: List<ChatMessageEntity>,
    aiUiState: AiUiState,
    isListening: Boolean,
    isSpeaking: Boolean,
    rmsLevel: Float,
    partialTranscript: String,
    voiceError: String?,
    onSendMessage: (String) -> Unit,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onDismissVoiceError: () -> Unit,
    onDismissAiError: () -> Unit,
    onCopiedNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val hudColors = LocalJarvisHudColors.current
    val glow = hudColors.glowPrimary

    val coreMode = when {
        isListening -> CoreVisualMode.LISTENING
        aiUiState is AiUiState.Thinking || aiUiState is AiUiState.Streaming -> CoreVisualMode.THINKING
        isSpeaking -> CoreVisualMode.SPEAKING
        else -> CoreVisualMode.IDLE
    }

    val coreStatusLabel = when (coreMode) {
        CoreVisualMode.LISTENING -> strings.coreListening
        CoreVisualMode.THINKING -> strings.coreThinking
        CoreVisualMode.SPEAKING -> strings.coreSpeaking
        CoreVisualMode.IDLE -> if (settings.localOnlyPrivacyMode) strings.coreOfflinePrivacy else strings.coreOnline
    }

    LaunchedEffect(messages.size, aiUiState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            // Privacy Shield notice banner
            AnimatedVisibility(visible = settings.localOnlyPrivacyMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(hudColors.statusWarning.copy(alpha = 0.14f))
                        .border(1.dp, hudColors.statusWarning.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = hudColors.statusWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.privacyActiveBanner,
                        style = MaterialTheme.typography.labelSmall,
                        color = hudColors.statusWarning
                    )
                }
            }

            // Animated Arc Reactor Core Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.width(48.dp))

                ArcReactorCore(
                    mode = coreMode,
                    rmsLevel = rmsLevel,
                    statusLabel = coreStatusLabel,
                    highIntensity = settings.highIntensityAnimations,
                    coreSize = if (messages.isEmpty()) 164.dp else 112.dp,
                    modifier = Modifier.clickable(onClick = onMicClick)
                )

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.width(48.dp)
                ) {
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = strings.clearChat,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Speaking / Listening / Error banners
            AnimatedVisibility(visible = isSpeaking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MiniSpeakingWaveBadge(
                        label = "${strings.coreSpeaking} • ${strings.stopSpeaking}",
                        onStop = onStopSpeaking
                    )
                }
            }

            AnimatedVisibility(visible = isListening) {
                HudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    accentColor = glow
                ) {
                    Text(
                        text = partialTranscript.ifBlank { strings.micListeningPrompt },
                        style = MaterialTheme.typography.bodyMedium,
                        color = glow
                    )
                }
            }

            AnimatedVisibility(visible = !voiceError.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(hudColors.statusCritical.copy(alpha = 0.14f))
                        .border(1.dp, hudColors.statusCritical.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = hudColors.statusCritical,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = voiceError.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = hudColors.statusCritical
                        )
                    }
                    TextButton(onClick = onDismissVoiceError) {
                        Text("OK", color = hudColors.statusCritical)
                    }
                }
            }

            // Chat Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("chat_message_list"),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        HudPanel(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = glow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "J.A.R.V.I.S. NEURAL LINK",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = glow
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.welcomeMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatMessageCard(
                        message = msg,
                        strings = strings,
                        onSpeak = onSpeakMessage,
                        onCopy = { text ->
                            clipboard.setText(AnnotatedString(text))
                            onCopiedNotice("Copied to clipboard")
                        }
                    )
                }

                if (aiUiState is AiUiState.Thinking) {
                    item {
                        HudPanel(modifier = Modifier.fillMaxWidth(0.75f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = glow
                                )
                                Text(
                                    text = "${strings.coreThinking}...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = glow
                                )
                            }
                        }
                    }
                }

                if (aiUiState is AiUiState.Streaming && aiUiState.text.isNotBlank()) {
                    item {
                        HudPanel(modifier = Modifier.fillMaxWidth(0.90f)) {
                            Text(
                                text = "J.A.R.V.I.S.",
                                style = MaterialTheme.typography.labelSmall,
                                color = glow
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiUiState.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (aiUiState is AiUiState.Error) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(hudColors.statusCritical.copy(alpha = 0.14f))
                                .border(1.dp, hudColors.statusCritical.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = aiUiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = hudColors.statusCritical,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissAiError) {
                                Text("Dismiss", color = hudColors.statusCritical)
                            }
                        }
                    }
                }
            }

            // Quick Command Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(strings.quickPrompts) { prompt ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, glow.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .clickable { onSendMessage(prompt) }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Input Dock + Mic Waveform
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = strings.inputHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = hudColors.textMuted
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = glow,
                        unfocusedBorderColor = glow.copy(alpha = 0.35f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                        cursorColor = glow
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier.testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = strings.sendLabel,
                                tint = if (inputText.isNotBlank()) glow else hudColors.textMuted
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                VoiceWaveformMicButton(
                    isListening = isListening,
                    rmsLevel = rmsLevel,
                    onClick = onMicClick
                )
            }
        }
    }
}
