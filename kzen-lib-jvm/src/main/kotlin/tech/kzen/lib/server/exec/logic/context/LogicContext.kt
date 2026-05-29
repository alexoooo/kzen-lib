package tech.kzen.lib.server.exec.logic.context

import tech.kzen.lib.common.exec.logic.Logic
import tech.kzen.lib.common.service.store.normal.ObjectStableId


data class LogicContext(
    val logicInstances: Map<ObjectStableId, Logic>,
    val root: LogicFrame
)
