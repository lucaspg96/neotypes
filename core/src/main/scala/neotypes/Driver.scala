package neotypes

import internal.syntax.async._
import internal.syntax.stage._
import org.neo4j.driver.{AccessMode, Bookmark, SessionConfig, Driver => NDriver}

import scala.jdk.CollectionConverters._

/** A neotypes driver for accessing the neo4j graph database
  * A driver wrapped in the resource type can be created using the neotypes GraphDatabase
  * {{{
  *    val driver = GraphDatabase[F]("bolt://localhost:7687").driver
  * }}}
  *
  * @param driver neo4j driver
  * @tparam F effect type for driver
  * @define futinfo When your effect type is scala.Future there is no concept of Resource. For more information see <a href = https://neotypes.github.io/neotypes/docs/alternative_effects.html>alternative effects</a>
  */
final class Driver[F[_]](private val driver: NDriver) extends AnyVal {

  /** Acquire a session to the database in read mode
    *
    * @note $futinfo
    * @param F asynchronous effect type with resource type defined
    * @tparam R resource type dependant on effect type F
    * @return Session[F] in effect type R
    */
  def session[R[_]](implicit F: Async.Aux[F, R]): R[Session[F]] =
    session[R](accessMode = AccessMode.READ)

  /** Acquire a session to the database in read or write mode
    *
    * @note $futinfo
    * @param accessMode read or write mode
    * @param bookmarks  bookmarks passed between transactions for neo4j casual chaining
    * @param F          asynchronous effect type with resource type defined
    * @tparam R resource type dependant on effect type F
    * @return Session[F] in effect type R
    */
  def session[R[_]](accessMode: AccessMode, bookmarks: String*)
                   (implicit F: Async.Aux[F, R]): R[Session[F]] =
    F.resource(createSession(accessMode, bookmarks))(session => session.close)

  private[this] def createSession(accessMode: AccessMode, bookmarks: Seq[String] = Seq.empty): Session[F] =
    new Session(
      driver.asyncSession(SessionConfig.builder()
        .withDefaultAccessMode(accessMode)
        .withBookmarks(Bookmark.from(bookmarks.toSet.asJava))
        .build())
    )

  /** Apply a unit of work to a read session
    *
    * @param sessionWork function that takes a Session[F] and returns an F[T]
    * @param F           the effect type
    * @tparam T the type of the value that will be returned when the query is executed.
    * @return an effect F of type T
    */
  def readSession[T](sessionWork: Session[F] => F[T])
                    (implicit F: Async[F]): F[T] =
    withSession(AccessMode.READ)(sessionWork)

  /** Apply a unit to a write session
    *
    * @param sessionWork function that takes a Session[F] and returns an F[T]
    * @param F           the effect type
    * @tparam T the type of the value that will be returned when the query is executed.
    * @return an effect F of type T
    */
  def writeSession[T](sessionWork: Session[F] => F[T])
                     (implicit F: Async[F]): F[T] =
    withSession(AccessMode.WRITE)(sessionWork)

  private[this] def withSession[T](accessMode: AccessMode)
                                  (sessionWork: Session[F] => F[T])
                                  (implicit F: Async[F]): F[T] =
    F.delay(createSession(accessMode)).guarantee(sessionWork) {
      case (session, _) => session.close
    }

  /** Close the resources assigned to the neo4j driver
    *
    * @param F the effect type
    * @return an effect F of Unit
    */
  def close(implicit F: Async[F]): F[Unit] =
    F.async { cb =>
      driver.closeAsync().acceptVoid(cb)
    }
}
