# rxjava-akka

Attempt at a bridge from *[RxJava]()* to *[Akka]()* and vice versa.

## Dependency

This module is published on *[Bintray](https://bintray.com/)*.

### sbt

This is how you declare the Bintray resolver and add the dependency on `rxjava-akka` for [sbt](http://scala-sbt.org):

```Scala
resolvers += "bintray-jmhofer" at "http://dl.bintray.com/jmhofer/maven"

libraryDependencies += "de.johoop" %% "rxjava-akka" % "1.0.0"
```

### Maven

Add the repository to Maven:

```XML
<repository>
  <id>bintray-jmhofer</id>
  <url>http://dl.bintray.com/jmhofer/maven</url>
</repository>
```

Resolve the library:

```XML
<dependency>
  <groupId>de.johoop</groupId>
  <artifactId>rxjava-akka_2.10</artifactId>
  <version>1.0.0</version>
 </dependency>
```

## Usage

### Akka Scheduler

The Akka Scheduler enables you to run your observables backed by Akka actors. In order to use it, you need a context
that is able to create actors, as the scheduler will create one internally. This can either be the actor system
directly, or the context of another actor (recommended).

The context will act as parent to the created actor. You can supervise it from there in order to handle errors.

### Sample

Here's a simple usage sample application (in Scala, although the scheduler works from Java, too):

```Scala
import akka.actor.{Props, ActorLogging, Actor, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout

import rx.lang.scala._
import rx.schedulers.AkkaScheduler

import scala.concurrent.duration._

import RxActor._

object Main extends App {
  val system = ActorSystem("demo")

  implicit val timeout = Timeout(10 seconds) // for waiting for the Done message
  import system.dispatcher

  val rxContext = system.actorOf(Props[RxActor], "rx-context")
  rxContext ! Start
  rxContext ? Schedule onComplete (_ => system.shutdown())
}

object RxActor {
  sealed trait Message
  case object Start extends Message
  case object Schedule extends Message
  case object Done extends Message
}

class RxActor extends Actor with ActorLogging {
  def receive: Receive = {
    case Start =>
      log info "Starting..."
      val scheduler = AkkaScheduler forParentWithName (context, "rx-scheduler")
      context become scheduling(scheduler)
  }

  def scheduling(scheduler: AkkaScheduler): Receive = {
    case Schedule =>
      log info "Scheduling..."
      val ref = sender
      val observable = Observable interval (1 second, Scheduler(scheduler)) take 5
      observable subscribe (
        onNext = (tick: Long) => log info s"Tick: $tick",
        onError = (e: Throwable) => log error s"Uh-oh: $e",
        onCompleted = () => ref ! Done)
  }
}
```

## Building

This build uses *[sbt](http://scala-sbt.org)*.

## License

See [LICENSE](https://github.com/jmhofer/rxjava-akka/blob/master/LICENSE) (Apache License 2.0).
