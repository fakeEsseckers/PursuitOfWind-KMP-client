package io.github.esseckers.pursuitofwind.core.refresh

import io.github.esseckers.pursuitofwind.data.model.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RefreshNoticeMapperTest {
    @Test
    fun `a connection problem says offline and any other failure says it could not update`() {
        assertEquals(RefreshNotice.OFFLINE, RefreshNoticeMapper.notice(AppError.Network))
        assertEquals(RefreshNotice.REFRESH_FAILED, RefreshNoticeMapper.notice(AppError.Server(503)))
        assertNull(RefreshNoticeMapper.notice(null))
    }

    @Test
    fun `data older than three hours is getting old unless a refresh failed`() {
        assertNull(RefreshNoticeMapper.notice(null, age = 3.hours))
        assertEquals(RefreshNotice.STALE, RefreshNoticeMapper.notice(null, age = 3.hours + 1.minutes))
        assertEquals(RefreshNotice.OFFLINE, RefreshNoticeMapper.notice(AppError.Network, age = 5.hours))
    }
}
