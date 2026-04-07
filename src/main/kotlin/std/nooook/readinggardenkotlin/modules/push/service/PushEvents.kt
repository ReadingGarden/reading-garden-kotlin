package std.nooook.readinggardenkotlin.modules.push.service

data class GardenMemberJoinedPushEvent(
    val gardenNo: Int,
    val recipientUserNos: List<Int>,
)
