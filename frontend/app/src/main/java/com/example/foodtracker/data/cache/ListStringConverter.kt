package com.example.foodtracker.data.cache

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Persistă List<String> ca JSON într-o coloană TEXT Room. */
class ListStringConverter {
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList()
        else gson.fromJson(value, listType) ?: emptyList()
}
