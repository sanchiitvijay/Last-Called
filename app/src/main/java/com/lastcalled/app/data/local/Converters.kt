package com.lastcalled.app.data.local

import androidx.room.TypeConverter
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.RecordOrigin

/** Enums are stored by name (not ordinal) so reordering them can never corrupt stored data. */
class Converters {
    @TypeConverter fun fromDirection(v: CallDirection): String = v.name
    @TypeConverter fun toDirection(v: String): CallDirection = enumValueOrDefault(v, CallDirection.UNKNOWN)

    @TypeConverter fun fromSource(v: CallSource): String = v.name
    @TypeConverter fun toSource(v: String): CallSource = enumValueOrDefault(v, CallSource.UNKNOWN)

    @TypeConverter fun fromType(v: CallType): String = v.name
    @TypeConverter fun toType(v: String): CallType = enumValueOrDefault(v, CallType.UNKNOWN)

    @TypeConverter fun fromOrigin(v: RecordOrigin): String = v.name
    @TypeConverter fun toOrigin(v: String): RecordOrigin = enumValueOrDefault(v, RecordOrigin.SYSTEM_CALL_LOG)

    private inline fun <reified E : Enum<E>> enumValueOrDefault(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
