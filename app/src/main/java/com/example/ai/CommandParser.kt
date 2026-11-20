package com.example.ai

import java.util.Calendar

sealed interface JarvisAction {
    data class CreateNote(val title: String, val content: String) : JarvisAction
    data class CreateReminder(val title: String, val delayMinutes: Int) : JarvisAction
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : JarvisAction
    data object OpenCamera : JarvisAction
    data object OpenSettings : JarvisAction
    data class OpenMaps(val query: String) : JarvisAction
    data class LaunchApp(val appName: String) : JarvisAction
    data object BatteryStatus : JarvisAction
    data object DeviceStatus : JarvisAction
}

data class ParsedModelOutput(
    val cleanText: String,
    val action: JarvisAction?
)

object CommandParser {

    private val actionTagRegex = Regex("""\[ACTION:([^\]]+)\]""", RegexOption.IGNORE_CASE)

    /**
     * Parses deterministic device commands directly in English, Bengali, or Hindi so offline
     * Privacy Shield mode and instant hardware actions work without latency.
     */
    fun detectDirectLocalAction(input: String): JarvisAction? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Camera
        if (lower in setOf("open camera", "launch camera", "camera", "ক্যামেরা খোলো", "ক্যামেরা চালু করো", "ক্যামেরা", "कैमरा खोलो", "कैमरा चालू करो", "कैमरा") ||
            (lower.contains("camera") && lower.contains("open"))
        ) {
            return JarvisAction.OpenCamera
        }

        // 2. Settings
        if (lower in setOf("open settings", "settings", "সেটিংস খোলো", "সেটিংস", "सेटिंग्स खोलो", "सेटिंग्स") ||
            (lower.contains("settings") && (lower.contains("open") || lower.contains("launch")))
        ) {
            return JarvisAction.OpenSettings
        }

        // 3. Google Maps
        if (lower.contains("google maps") || lower.contains("open maps") ||
            lower.contains("ম্যাপস") || lower.contains("मैप्स") ||
            lower.startsWith("navigate to ") || lower.startsWith("directions to ")
        ) {
            val query = trimmed
                .replace(Regex("(?i)(open\\s+)?(google\\s+)?maps(\\s+(to|for))?"), "")
                .replace(Regex("(?i)(navigate|directions)\\s+to"), "")
                .replace("গুগল ম্যাপস খোলো", "")
                .replace("ম্যাপস খোলো", "")
                .replace("গুগল ম্যাপস", "")
                .replace("गूगल मैप्स खोलो", "")
                .replace("मैप्स खोलो", "")
                .replace("गूगल मैप्स", "")
                .trim()
            return JarvisAction.OpenMaps(query)
        }

        // 4. Battery & Device Info
        if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("बैटरी")) {
            return JarvisAction.BatteryStatus
        }
        if (lower.contains("device info") || lower.contains("system status") ||
            lower.contains("ডিভাইস তথ্য") || lower.contains("डिवाइस") || lower.contains("सिस्टम स्थिति")
        ) {
            return JarvisAction.DeviceStatus
        }

        // 5. Alarm: e.g., "Set alarm for 7:30 AM", "alarm 06:45", "সকাল ৭:৩০ এর অ্যালার্ম"
        if (lower.contains("alarm") || lower.contains("অ্যালার্ম") || lower.contains("अलार्म")) {
            val parsedTime = extractTime(lower)
            if (parsedTime != null) {
                return JarvisAction.SetAlarm(
                    hour = parsedTime.first,
                    minute = parsedTime.second,
                    label = "Jarvis Alarm"
                )
            }
        }

        // 6. Reminder: e.g., "Remind me to drink water in 15 min"
        if (lower.startsWith("remind") || lower.contains("reminder") ||
            lower.contains("রিমাইন্ডার") || lower.contains("মনে করিয়ে") ||
            lower.contains("रिमाइंडर") || lower.contains("याद दिला")
        ) {
            val minutes = extractMinutes(lower) ?: 15
            val title = trimmed
                .replace(Regex("(?i)create a?\\s*reminder(\\s+in\\s+\\d+\\s*(min|mins|minute|minutes|hr|hour|hours))?"), "")
                .replace(Regex("(?i)remind me(\\s+to)?"), "")
                .replace(Regex("(?i)\\bin\\s+\\d+\\s*(min|mins|minute|minutes|hr|hour|hours)"), "")
                .trim()
                .ifEmpty { "Jarvis Reminder" }
            return JarvisAction.CreateReminder(title = title, delayMinutes = minutes)
        }

        // 7. Note: e.g., "Take a note: buy milk", "Note: project ideas"
        if (lower.startsWith("note:") || lower.startsWith("take a note") || lower.startsWith("create a note") ||
            lower.startsWith("নোট:") || lower.startsWith("নোট লেখো") ||
            lower.startsWith("नोट:") || lower.startsWith("नोट लिखो")
        ) {
            val body = trimmed
                .replace(Regex("(?i)^(take a note|create a note|note)\\s*:?"), "")
                .replace(Regex("^(নোট লেখো|নোট)\\s*:?"), "")
                .replace(Regex("^(नोट लिखो|नोट)\\s*:?"), "")
                .trim()
            if (body.isNotEmpty()) {
                val title = body.split(" ").take(5).joinToString(" ")
                return JarvisAction.CreateNote(title = title, content = body)
            }
        }

        // 8. Generic app launch: "Open <AppName>" / "Launch <AppName>"
        val openRegex = Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val openMatch = openRegex.find(trimmed)
        if (openMatch != null) {
            val target = openMatch.groupValues[1].trim()
            if (target.isNotEmpty()) {
                return JarvisAction.LaunchApp(target)
            }
        }

        return null
    }

    /**
     * Extracts structured `[ACTION:...]` marker emitted by Gemini while returning clean display text.
     */
    fun parseGeminiActionDirective(rawResponse: String): ParsedModelOutput {
        val match = actionTagRegex.find(rawResponse) ?: return ParsedModelOutput(
            cleanText = rawResponse.trim(),
            action = null
        )

        val payload = match.groupValues[1].trim()
        val parts = payload.split("|").map { it.trim() }
        val action = when (parts.firstOrNull()?.uppercase()) {
            "NOTE" -> {
                val title = parts.getOrNull(1).orEmpty().ifBlank { "Jarvis Note" }
                val content = parts.getOrNull(2).orEmpty().ifBlank { title }
                JarvisAction.CreateNote(title, content)
            }
            "REMINDER" -> {
                val title = parts.getOrNull(1).orEmpty().ifBlank { "Jarvis Reminder" }
                val mins = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 10080) ?: 15
                JarvisAction.CreateReminder(title, mins)
            }
            "ALARM" -> {
                val hour = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
                val minute = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                val label = parts.getOrNull(3).orEmpty().ifBlank { "Jarvis Alarm" }
                JarvisAction.SetAlarm(hour, minute, label)
            }
            "CAMERA" -> JarvisAction.OpenCamera
            "SETTINGS" -> JarvisAction.OpenSettings
            "MAPS" -> JarvisAction.OpenMaps(parts.getOrNull(1).orEmpty())
            "APP" -> {
                val name = parts.getOrNull(1).orEmpty()
                if (name.isNotEmpty()) JarvisAction.LaunchApp(name) else null
            }
            "BATTERY" -> JarvisAction.BatteryStatus
            "DEVICE" -> JarvisAction.DeviceStatus
            else -> null
        }

        val cleaned = rawResponse.replace(actionTagRegex, "").trim()
        return ParsedModelOutput(cleanText = cleaned, action = action)
    }

    private fun extractTime(text: String): Pair<Int, Int>? {
        val normalized = convertIndicDigitsToAscii(text)
        val match = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""").find(normalized) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3].lowercase()
        if (meridiem == "pm" && hour in 1..11) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun extractMinutes(text: String): Int? {
        val normalized = convertIndicDigitsToAscii(text)
        val hourMatch = Regex("""(\d+)\s*(hr|hrs|hour|hours|ঘণ্টা|ঘন্টা|घंटा|घंटे)""").find(normalized)
        if (hourMatch != null) {
            val hrs = hourMatch.groupValues[1].toIntOrNull() ?: 1
            return (hrs * 60).coerceIn(1, 1440)
        }
        val minMatch = Regex("""(\d+)\s*(min|mins|minute|minutes|মিনিট|मिनट)""").find(normalized)
        return minMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 1440)
    }

    fun convertIndicDigitsToAscii(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val mapped = when (ch) {
                in '\u09E6'..'\u09EF' -> ('0'.code + (ch.code - '\u09E6'.code)).toChar() // Bengali digits
                in '\u0966'..'\u096F' -> ('0'.code + (ch.code - '\u0966'.code)).toChar() // Devanagari digits
                else -> ch
            }
            sb.append(mapped)
        }
        return sb.toString()
    }

    fun formatAlarmTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        return String.format("%d:%02d %s", h, minute, amPm)
    }
}
