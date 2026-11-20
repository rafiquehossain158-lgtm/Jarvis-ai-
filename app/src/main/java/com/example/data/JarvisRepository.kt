package com.example.data

import com.example.data.db.AlarmDao
import com.example.data.db.AlarmEntity
import com.example.data.db.ChatMessageDao
import com.example.data.db.ChatMessageEntity
import com.example.data.db.NoteDao
import com.example.data.db.NoteEntity
import com.example.data.db.ReminderDao
import com.example.data.db.ReminderEntity
import kotlinx.coroutines.flow.Flow

class JarvisRepository(
    private val chatMessageDao: ChatMessageDao,
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
    private val alarmDao: AlarmDao
) {
    val chatMessages: Flow<List<ChatMessageEntity>> = chatMessageDao.observeMessages()
    val notes: Flow<List<NoteEntity>> = noteDao.observeNotes()
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeReminders()
    val alarms: Flow<List<AlarmEntity>> = alarmDao.observeAlarms()

    suspend fun addChatMessage(message: ChatMessageEntity): Long =
        chatMessageDao.insertMessage(message)

    suspend fun clearChatHistory() = chatMessageDao.clearAllMessages()

    suspend fun addNote(title: String, content: String): Long =
        noteDao.insertNote(NoteEntity(title = title.trim(), content = content.trim()))

    suspend fun togglePinNote(note: NoteEntity) =
        noteDao.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))

    suspend fun deleteNote(id: Long) = noteDao.deleteNoteById(id)

    suspend fun addReminder(title: String, triggerAtMillis: Long): Long =
        reminderDao.insertReminder(ReminderEntity(title = title.trim(), triggerAtMillis = triggerAtMillis))

    suspend fun toggleReminderComplete(reminder: ReminderEntity) =
        reminderDao.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))

    suspend fun deleteReminder(id: Long) = reminderDao.deleteReminderById(id)

    suspend fun addAlarm(hour: Int, minute: Int, label: String): Long =
        alarmDao.insertAlarm(AlarmEntity(hour = hour, minute = minute, label = label.trim()))

    suspend fun toggleAlarm(alarm: AlarmEntity) =
        alarmDao.updateAlarm(alarm.copy(isEnabled = !alarm.isEnabled))

    suspend fun deleteAlarm(id: Long) = alarmDao.deleteAlarmById(id)

    suspend fun purgeAllData() {
        chatMessageDao.clearAllMessages()
        noteDao.clearAllNotes()
        reminderDao.clearAllReminders()
        alarmDao.clearAllAlarms()
    }
}
