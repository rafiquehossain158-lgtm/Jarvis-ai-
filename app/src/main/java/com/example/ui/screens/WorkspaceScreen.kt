package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ai.CommandParser
import com.example.data.db.AlarmEntity
import com.example.data.db.NoteEntity
import com.example.data.db.ReminderEntity
import com.example.i18n.JarvisStrings
import com.example.ui.components.HudPanel
import com.example.ui.theme.LocalJarvisHudColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class WorkspaceTab { NOTES, REMINDERS, ALARMS }

@Composable
fun WorkspaceScreen(
    strings: JarvisStrings,
    notes: List<NoteEntity>,
    reminders: List<ReminderEntity>,
    alarms: List<AlarmEntity>,
    onCreateNote: (String, String) -> Unit,
    onTogglePinNote: (NoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onCreateReminder: (String, Int) -> Unit,
    onToggleReminder: (ReminderEntity) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onCreateAlarm: (Int, Int, String, Boolean) -> Unit,
    onToggleAlarm: (AlarmEntity) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by rememberSaveable { mutableStateOf(WorkspaceTab.NOTES) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    val hudColors = LocalJarvisHudColors.current
    val glow = hudColors.glowPrimary

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Segmented Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, glow.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    WorkspaceTab.NOTES to strings.notesTab,
                    WorkspaceTab.REMINDERS to strings.remindersTab,
                    WorkspaceTab.ALARMS to strings.alarmsTab
                )
                tabs.forEach { (tab, label) ->
                    val selected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) glow else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { activeTab = tab }
                            .padding(vertical = 10.dp)
                            .testTag("workspace_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTab) {
                    WorkspaceTab.NOTES -> {
                        if (notes.isEmpty()) {
                            item {
                                EmptyWorkspaceCard(
                                    icon = Icons.Default.NoteAlt,
                                    title = strings.emptyNotesTitle,
                                    subtitle = strings.emptyNotesSubtitle
                                )
                            }
                        } else {
                            items(notes, key = { it.id }) { note ->
                                HudPanel(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = note.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = glow,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(onClick = { onTogglePinNote(note) }) {
                                                Icon(
                                                    imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = "Pin note",
                                                    tint = if (note.isPinned) glow else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { onDeleteNote(note.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = strings.deleteLabel,
                                                    tint = hudColors.statusCritical
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = note.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    WorkspaceTab.REMINDERS -> {
                        if (reminders.isEmpty()) {
                            item {
                                EmptyWorkspaceCard(
                                    icon = Icons.Default.NotificationsActive,
                                    title = strings.emptyRemindersTitle,
                                    subtitle = strings.emptyRemindersSubtitle
                                )
                            }
                        } else {
                            items(reminders, key = { it.id }) { reminder ->
                                val timeText = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                    .format(Date(reminder.triggerAtMillis))
                                HudPanel(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { onToggleReminder(reminder) }) {
                                            Icon(
                                                imageVector = if (reminder.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                                contentDescription = "Toggle reminder",
                                                tint = if (reminder.isCompleted) hudColors.statusNominal else glow
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = reminder.title,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                                ),
                                                color = if (reminder.isCompleted) hudColors.textMuted else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = timeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = glow
                                            )
                                        }
                                        IconButton(onClick = { onDeleteReminder(reminder.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = strings.deleteLabel,
                                                tint = hudColors.statusCritical
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    WorkspaceTab.ALARMS -> {
                        if (alarms.isEmpty()) {
                            item {
                                EmptyWorkspaceCard(
                                    icon = Icons.Default.Alarm,
                                    title = strings.emptyAlarmsTitle,
                                    subtitle = strings.emptyAlarmsSubtitle
                                )
                            }
                        } else {
                            items(alarms, key = { it.id }) { alarm ->
                                HudPanel(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = CommandParser.formatAlarmTime(alarm.hour, alarm.minute),
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = if (alarm.isEnabled) glow else hudColors.textMuted
                                            )
                                            Text(
                                                text = alarm.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = alarm.isEnabled,
                                                onCheckedChange = { onToggleAlarm(alarm) }
                                            )
                                            IconButton(onClick = { onDeleteAlarm(alarm.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = strings.deleteLabel,
                                                    tint = hudColors.statusCritical
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = glow,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("workspace_add_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = when (activeTab) {
                    WorkspaceTab.NOTES -> strings.addNote
                    WorkspaceTab.REMINDERS -> strings.addReminder
                    WorkspaceTab.ALARMS -> strings.addAlarm
                }
            )
        }
    }

    if (showCreateDialog) {
        when (activeTab) {
            WorkspaceTab.NOTES -> CreateNoteDialog(
                strings = strings,
                onDismiss = { showCreateDialog = false },
                onSave = { title, body ->
                    onCreateNote(title, body)
                    showCreateDialog = false
                }
            )
            WorkspaceTab.REMINDERS -> CreateReminderDialog(
                strings = strings,
                onDismiss = { showCreateDialog = false },
                onSave = { title, mins ->
                    onCreateReminder(title, mins)
                    showCreateDialog = false
                }
            )
            WorkspaceTab.ALARMS -> CreateAlarmDialog(
                strings = strings,
                onDismiss = { showCreateDialog = false },
                onSave = { h, m, label, syncClock ->
                    onCreateAlarm(h, m, label, syncClock)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
private fun EmptyWorkspaceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val glow = LocalJarvisHudColors.current.glowPrimary
    HudPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glow,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateNoteDialog(
    strings: JarvisStrings,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addNote) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.titleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("note_title_input")
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(strings.detailsLabel) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("note_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, body) },
                enabled = title.isNotBlank() || body.isNotBlank(),
                modifier = Modifier.testTag("save_note_button")
            ) {
                Text(strings.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelLabel) }
        }
    )
}

@Composable
private fun CreateReminderDialog(
    strings: JarvisStrings,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(15) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addReminder) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.titleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("reminder_title_input")
                )
                Text(
                    text = "Trigger in: $minutes min",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { preset ->
                        TextButton(onClick = { minutes = preset }) {
                            Text("${preset}m")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, minutes) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_reminder_button")
            ) {
                Text(strings.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelLabel) }
        }
    )
}

@Composable
private fun CreateAlarmDialog(
    strings: JarvisStrings,
    onDismiss: () -> Unit,
    onSave: (Int, Int, String, Boolean) -> Unit
) {
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(30) }
    var label by remember { mutableStateOf("Jarvis Morning Briefing") }
    var syncSystemClock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addAlarm) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = CommandParser.formatAlarmTime(hour, minute),
                    style = MaterialTheme.typography.displaySmall,
                    color = LocalJarvisHudColors.current.glowPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { hour = (hour + 23) % 24 }) { Text("-1h") }
                    TextButton(onClick = { hour = (hour + 1) % 24 }) { Text("+1h") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { minute = (minute + 55) % 60 }) { Text("-5m") }
                    TextButton(onClick = { minute = (minute + 5) % 60 }) { Text("+5m") }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(strings.titleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { syncSystemClock = !syncSystemClock }
                ) {
                    Checkbox(
                        checked = syncSystemClock,
                        onCheckedChange = { syncSystemClock = it }
                    )
                    Text(
                        text = strings.syncClockLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(hour, minute, label, syncSystemClock) },
                modifier = Modifier.testTag("save_alarm_button")
            ) {
                Text(strings.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelLabel) }
        }
    )
}
