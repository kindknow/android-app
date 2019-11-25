package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import kotlinx.coroutines.runBlocking
import one.mixin.android.vo.ParticipantSession

class RefreshSessionJob(
    private val conversationId: String,
    private val userIds: List<String>
) : BaseJob(Params(PRIORITY_UI_HIGH).addTags(GROUP).requireNetwork().persist()) {

    companion object {
        private const val serialVersionUID = 1L
        private const val GROUP = "RefreshSessionJob"
    }

    override fun onRun() = runBlocking {
        val response = userService.fetchSessions(userIds)
        if (response.isSuccess) {
            val ps = response.data?.map { item ->
                ParticipantSession(conversationId, item.user_id, item.session_id)
            }
            if (!ps.isNullOrEmpty()) {
                participantSessionDao.insertList(ps)
            }
        }
    }
}
