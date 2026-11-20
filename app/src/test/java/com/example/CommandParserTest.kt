package com.example

import com.example.ai.CommandParser
import com.example.ai.JarvisAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun `detects english camera, settings, and maps commands`() {
        assertEquals(JarvisAction.OpenCamera, CommandParser.detectDirectLocalAction("Open Camera"))
        assertEquals(JarvisAction.OpenSettings, CommandParser.detectDirectLocalAction("Open Settings"))
        val maps = CommandParser.detectDirectLocalAction("Open Google Maps to Dhaka")
        assertTrue(maps is JarvisAction.OpenMaps)
        assertEquals("Dhaka", (maps as JarvisAction.OpenMaps).query)
    }

    @Test
    fun `detects bengali and hindi commands`() {
        assertEquals(JarvisAction.OpenCamera, CommandParser.detectDirectLocalAction("ক্যামেরা খোলো"))
        assertEquals(JarvisAction.OpenSettings, CommandParser.detectDirectLocalAction("सेटिंग्स खोलो"))
        assertEquals(JarvisAction.BatteryStatus, CommandParser.detectDirectLocalAction("ব্যাটারি অবস্থা দেখাও"))
        assertEquals(JarvisAction.BatteryStatus, CommandParser.detectDirectLocalAction("बैटरी स्थिति"))
    }

    @Test
    fun `detects alarm with am pm and indic digits`() {
        val enAlarm = CommandParser.detectDirectLocalAction("Set alarm for 7:30 PM")
        assertTrue(enAlarm is JarvisAction.SetAlarm)
        assertEquals(19, (enAlarm as JarvisAction.SetAlarm).hour)
        assertEquals(30, enAlarm.minute)

        val bnAlarm = CommandParser.detectDirectLocalAction("সকাল ৭:৩০ এর অ্যালার্ম")
        assertTrue(bnAlarm is JarvisAction.SetAlarm)
        assertEquals(7, (bnAlarm as JarvisAction.SetAlarm).hour)
        assertEquals(30, bnAlarm.minute)
    }

    @Test
    fun `detects note and reminder creation`() {
        val note = CommandParser.detectDirectLocalAction("Note: Review arc reactor schematics")
        assertTrue(note is JarvisAction.CreateNote)
        assertEquals("Review arc reactor schematics", (note as JarvisAction.CreateNote).content)

        val reminder = CommandParser.detectDirectLocalAction("Remind me to call Pepper in 20 min")
        assertTrue(reminder is JarvisAction.CreateReminder)
        assertEquals(20, (reminder as JarvisAction.CreateReminder).delayMinutes)
        assertEquals("call Pepper", reminder.title)
    }

    @Test
    fun `parses gemini action directive cleanly`() {
        val raw = "Certainly, sir. I have scheduled that reminder for you. [ACTION:REMINDER|Buy coffee|25]"
        val parsed = CommandParser.parseGeminiActionDirective(raw)
        assertEquals("Certainly, sir. I have scheduled that reminder for you.", parsed.cleanText)
        assertEquals(JarvisAction.CreateReminder("Buy coffee", 25), parsed.action)
    }

    @Test
    fun `returns null action for general conversation`() {
        assertNull(CommandParser.detectDirectLocalAction("Explain quantum entanglement simply"))
    }
}
