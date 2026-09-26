package com.fitflow.training.assistant

import com.fitflow.training.session.*

data class CommandContext(
    val sessionId: String, val blockId: String?, val phase: Phase, val turnId: Long,
    val revision: Long,
) {
    companion object {
        fun from(snapshot: SessionSnapshot, turnId: Long) =
            CommandContext(snapshot.id, snapshot.currentBlockId, snapshot.phase, turnId, snapshot.revision)
    }
}
data class CommandResult(val applied: Boolean, val message: String)
data class CommandTransition(val snapshot: SessionSnapshot, val result: CommandResult)

object AssistantCommandDispatcher {
    fun apply(snapshot: SessionSnapshot, command: TrainingCommand, context: CommandContext, nowMs: Long): CommandTransition {
        fun reject(message: String) = CommandTransition(snapshot, CommandResult(false, message))
        if (snapshot.id != context.sessionId || snapshot.currentBlockId != context.blockId ||
            snapshot.phase != context.phase || snapshot.revision != context.revision)
            return reject("训练状态已变化，请重新唤醒后说指令")
        if (snapshot.phase == Phase.FINISHED) return reject("训练已结束")
        if (snapshot.isPaused) return reject("训练已暂停，请点击继续训练")
        val next = when (command) {
            TrainingCommand.SKIP_REST -> SessionReducer.skipRest(snapshot)
            TrainingCommand.EXTEND_REST_30 -> SessionReducer.extendRest(snapshot, 30)
            TrainingCommand.PAUSE -> SessionReducer.pause(snapshot, nowMs)
            TrainingCommand.COMPLETE_SET -> SessionReducer.completeSet(snapshot, nowMs)
        }
        if (next == snapshot) return reject(if (snapshot.phase == Phase.RESTING) "正在休息，不能完成本组" else "当前没有进行中的休息")
        val message = when (command) {
            TrainingCommand.SKIP_REST -> "已跳过休息，下一组已就绪"
            TrainingCommand.EXTEND_REST_30 -> "已延长三十秒休息"
            TrainingCommand.PAUSE -> "训练已暂停"
            TrainingCommand.COMPLETE_SET -> when {
                next.phase == Phase.FINISHED -> "训练已完成，辛苦了"
                next.restKind == RestKind.EXERCISE -> "本组已完成，开始动作间休息"
                else -> "本组已完成，开始组间休息"
            }
        }
        return CommandTransition(next.copy(revision = snapshot.revision + 1), CommandResult(true, message))
    }
}
