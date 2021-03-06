package com.jetprobe.core.runner

import java.util.Date

import akka.actor.{ActorRef, PoisonPill, Props}
import com.jetprobe.core.Predef.Session
import com.jetprobe.core.action._
import com.jetprobe.core.controller.ControllerCommand.EndScenario
import com.jetprobe.core.runner.ScenarioManager._
import com.jetprobe.core.session.Session
import com.jetprobe.core.structure.{ExecutableScenario, Scenario}
import com.jetprobe.core.validations.{Failed, Passed, Skipped}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration

/**
  * @author Shad.
  */
class ScenarioManager(runnableScn: ExecutableScenario, controller: ActorRef) extends BaseActor {

  var startTime: Long = 0L
  var session: Session = _
  val metricsReport : ArrayBuffer[ActionMetrics] = ArrayBuffer.empty

  override def receive: Receive = {

    case StartScenario =>
      startTime = new Date().getTime
      val scn = runnableScn.build(context.system, new Exit(self), self)
      session = Session(scn.name, scn.className, attributes = scn.configAttr)
      scn.entry.execute(session)

    case ActionCompleted(newSession) =>
      session = newSession
      context stop (sender())

    case ExecuteNext(action, newSession, isScheduled,metrics) =>
      if (!isScheduled) {
        context stop (sender())
      }
      metricsReport.+=(metrics)
      action.execute(newSession)

    case ScenarioCompleted(finalSession) =>
      val status = if (finalSession.validationResults.count(_.status == Passed) == finalSession.validationResults.size) {
        Passed
      } else if (finalSession.validationResults.filter(_.status == Skipped).size > 0) {
        Skipped
      } else Failed

      metricsReport.foreach{report =>
        println(s"Action : ${report.name}, time taken : ${(report.endTime - report.startTime)/1000f} secs")
      }
      val timeTaken = (new Date().getTime - startTime) / 1000f

      controller ! EndScenario(finalSession, timeTaken, status)


    case ExecuteWithDelay(next, delay) =>
      logger.info(s"Pausing the pipeline for the duration : ${delay.length}")
      val startTime = new Date().getTime
      val metrics = new ActionMetrics(s"paused for ${delay.length}ms", startTime, startTime + delay.length, Successful)
      context.system.scheduler.scheduleOnce(delay, self, ExecuteNext(next, session, true, metrics))
  }
}

object ScenarioManager {

  case class StartScenario()

  case class ActionCompleted(session: Session)

  case class ExecuteNext(next: Action, session: Session, scheduledAction: Boolean, metrics: ActionMetrics)

  case class ExecuteWithDelay(next: Action, duration: FiniteDuration)

  case class ScenarioCompleted(session: Session)


  def props(scn: ExecutableScenario, controller: ActorRef): Props = Props(new ScenarioManager(scn, controller))

}
