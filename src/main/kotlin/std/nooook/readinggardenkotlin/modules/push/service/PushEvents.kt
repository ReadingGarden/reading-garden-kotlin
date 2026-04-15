package std.nooook.readinggardenkotlin.modules.push.service

data class GardenMemberJoinedPushEvent(
    val gardenNo: Long,
    val recipientUserIds: List<Long>,
)
