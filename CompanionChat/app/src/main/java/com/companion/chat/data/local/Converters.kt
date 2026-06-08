package com.companion.chat.data.local

import androidx.room.TypeConverter
import com.companion.chat.data.model.MessageRole
import org.json.JSONArray

class Converters {

    @TypeConverter
    fun fromMessageRole(value: MessageRole): String = value.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter
    fun fromStringList(values: List<String>): String {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val array = JSONArray(value)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }
    }
}
