/*
 * Copyright 2013 Joachim Hofer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.schedulers

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import rx.Scheduler
import rx.Subscription
import rx.schedulers.actor.SchedulerActor
import rx.schedulers.actor.SchedulerActor._
import rx.subscriptions.Subscriptions
import rx.util.functions.Action0
import scala.concurrent.duration._
import scala.concurrent.Future

/** Provides RxJava schedulers which schedule actions in an akka actor.
  * This actor is created in the given context (which may be an actor system or a parent actor context).
  * The scheduler has to be explicitly shut down after usage in order for the internally used actor to stop.
  */
object AkkaScheduler {
  def forParent(parent: ActorRefFactory): AkkaScheduler = new AkkaScheduler(parent)

  def forParentWithName(parent: ActorRefFactory, name: String): AkkaScheduler = new AkkaScheduler(parent, Some(name))

  def forParentWithTimeout(parent: ActorRefFactory, timeout: FiniteDuration): AkkaScheduler =
    new AkkaScheduler(parent, timeout = timeout)

  def forParentWithTimeout(parent: ActorRefFactory, timeout: Long, unit: TimeUnit): AkkaScheduler =
    new AkkaScheduler(parent, timeout = Duration(timeout, unit))

  def forParentWithNameAndTimeout(parent: ActorRefFactory, name: String, timeout: FiniteDuration): AkkaScheduler =
    new AkkaScheduler(parent, Some(name), timeout)

  def forParentWithNameAndTimeout(parent: ActorRefFactory, name: String, timeout: Long, unit: TimeUnit): AkkaScheduler =
    new AkkaScheduler(parent, Some(name), Duration(timeout, unit))
}

class AkkaScheduler(context: ActorRefFactory,
                    actorName: Option[String] = None,
                    timeout: FiniteDuration = 1.second) extends Scheduler {

  val actor = actorName
    .map (context.actorOf(Props(classOf[SchedulerActor], this), _))
    .getOrElse (context.actorOf(Props(classOf[SchedulerActor], this)))

  def shutdown(): Unit = context stop actor

  def schedule[T](state: T, action: Action[T]): Subscription = {
    actor ! StatefulAction(state, action)
    Subscriptions.empty
  }

  def schedule[T](state: T, action: Action[T], delayTime: Long, unit: TimeUnit): Subscription = {
    implicit val timeout0 = Timeout(timeout)
    val cancellable = actor ? Delayed(StatefulAction(state, action), Duration(delayTime, unit))
    subscriptionFor(cancellable.mapTo[Cancellable])
  }

  override def schedulePeriodically[T](state: T, action: Action[T], initialDelay: Long, period: Long, unit: TimeUnit): Subscription = {
    implicit val timeout0 = Timeout(timeout)
    val cancellable = actor ? Periodic[T](StatefulAction[T](state, action), Duration(initialDelay, unit), Duration(period, unit))
    subscriptionFor(cancellable.mapTo[Cancellable])
  }

  private def subscriptionFor(cancellable: Future[Cancellable]) = Subscriptions create new Action0 {
    def call(): Unit = {
      import context.dispatcher
      cancellable foreach (_.cancel())
    }
  }
}
