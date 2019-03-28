package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import kotlinx.coroutines.runBlocking

/**
 * @param conversationId NOT NULL means should generate the group avatar
 */
class RefreshUserJob(private val userIds: List<String>, private val conversationId: String? = null)
    : BaseJob(Params(PRIORITY_UI_HIGH).addTags(RefreshUserJob.GROUP).requireNetwork().persist()) {

    companion object {
        private const val serialVersionUID = 1L
        private const val GROUP = "RefreshUserJob"
    }

    override fun onRun() = runBlocking {
        if (userIds.isEmpty()) {
            return@runBlocking
        }
        val existUsers = userDao.findUserExist(userIds)
        val queryUsers = userIds.filter {
            !existUsers.contains(it)
        }
        if (queryUsers.isEmpty()) {
            return@runBlocking
        }

        val response = userService.getUsers(queryUsers).execute().body()
        if (response != null && response.isSuccess) {
            response.data?.let { data ->
                userRepo.upsertList(data)
                conversationId?.let {
                    jobManager.addJobInBackground(GenerateAvatarJob(conversationId))
                }
            }
        }
    }
}
