package com.ozyern.exhale.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchHistorySettingsTest {
    @Test
    fun recordsNonemptyQueriesWhenHistoryIsEnabled() {
        assertTrue(shouldRecordSearchQuery("exhale", SearchHistorySettings()))
    }

    @Test
    fun doesNotRecordQueriesWhenHistoryIsPaused() {
        assertFalse(shouldRecordSearchQuery("exhale", SearchHistorySettings(paused = true)))
    }

    @Test
    fun doesNotRecordQueriesBeforeSettingsAreLoaded() {
        assertFalse(shouldRecordSearchQuery("exhale", null))
    }

    @Test
    fun doesNotRecordEmptyQueries() {
        assertFalse(shouldRecordSearchQuery("", SearchHistorySettings()))
    }
}
