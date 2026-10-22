package io.github.esseckers.pursuitofwind.data.repository

import androidx.room3.Room
import androidx.room3.RoomDatabase
import io.github.esseckers.pursuitofwind.data.internal.local.PursuitOfWindDatabase
import java.nio.file.Files

internal actual fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<PursuitOfWindDatabase> =
    Room.inMemoryDatabaseBuilder<PursuitOfWindDatabase>()

internal actual fun temporaryFilePath(name: String): String = Files.createTempDirectory("pursuitofwind").resolve(name).toString()
