package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.data.local.SyncTombstoneDao
import com.gusanitolabs.robia.data.local.TagDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation

/**
 * Restore defaults on the repository, with the DAOs replaced by recording proxies: every call is logged in order, and
 * the methods the restore reads return empty results.
 */
class LocalTagRepositoryRestoreTest {
    @Test
    fun restoreClearsGarmentTagTombstonesOfTheRestoredDefaultsBeforeWritingThem() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = LocalTagRepository(
            tagDao = recordingDao(TagDao::class.java, calls),
            syncTombstoneDao = recordingDao(SyncTombstoneDao::class.java, calls),
        )

        repository.restoreDefaultTags("category")

        val restoredIds = DefaultTags.tags.filter { tag -> tag.categoryId == "category" }.map { tag -> tag.id }
        val clearedIds = calls
            .filter { call -> call.startsWith("deleteTombstone") }
            .map { call -> call.substringAfter("deleteTombstone(garment_tag,").removeSuffix(")") }
        assertEquals(restoredIds, clearedIds)
        val upsertIndex = calls.indexOfFirst { call -> call.startsWith("upsertTags") }
        assertTrue(upsertIndex > calls.lastIndexOf("deleteTombstone(garment_tag,${restoredIds.last()})"))
    }

    @Test
    fun restoreWithoutTombstoneDaoStillWritesTheDefaults() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = LocalTagRepository(tagDao = recordingDao(TagDao::class.java, calls))

        repository.restoreDefaultTags("category")

        assertTrue(calls.any { call -> call.startsWith("upsertTags") })
    }

    /** A DAO proxy that records `name(args)` for every call (coroutine continuation left out) and answers empty lists, zeroes, or nothing. */
    private fun <T> recordingDao(type: Class<T>, calls: MutableList<String>): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            val values = args.orEmpty().filterNot { arg -> arg is Continuation<*> }
            calls += method.name + values.joinToString(separator = ",", prefix = "(", postfix = ")")
            when (method.name) {
                "tagsForDedupe", "getAllForSync" -> emptyList<Any>()
                "deleteTombstone" -> 0
                else -> null
            }
        } as T
}
