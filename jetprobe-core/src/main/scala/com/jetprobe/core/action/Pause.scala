package com.jetprobe.core.action

import java.util.Date

import akka.actor.{ActorRef, ActorSystem, Props}
import com.jetprobe.core.runner.ScenarioManager.{ExecuteNext, ExecuteWithDelay}
import com.jetprobe.core.session.Session

import scala.concurrent.duration._

/**
  * @author Shad.
  */
class Pause(duration : FiniteDuration, next : Action,actorSystem : ActorSystem,scenarioController : ActorRef) extends Action{

  import actorSystem.dispatcher

  override def name: String = "pause for "

  override def execute(session: Session): Unit = {
    logger.info(s"Pausing for the duration ${duration.length}")
    val startTime = new Date().getTime
    val metrics = new ActionMetrics(s"paused for ${duration.length}ms", startTime, startTime + duration.length, Successful)
    actorSystem.scheduler.scheduleOnce(duration,scenarioController,ExecuteNext(next,session,true,metrics))

  }
}
