package neotypes
package implicits.mappers

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.UUID

import exceptions.{ConversionException, PropertyNotFoundException}
import internal.utils.traverse.{traverseAs, traverseAsList}
import mappers.{ResultMapper, TypeHint, ValueMapper}
import types.Path

import org.neo4j.driver.internal.types.InternalTypeSystem
import org.neo4j.driver.internal.value.{MapValue, NodeValue, RelationshipValue}
import org.neo4j.driver.Value
import org.neo4j.driver.types.{IsoDuration, MapAccessor => NMap, Node, Path => NPath, Point, Relationship}
import shapeless.HNil

import scala.collection.compat._
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

trait ValueMappers {
 implicit final val BooleanValueMapper: ValueMapper[Boolean] =
    ValueMapper.fromCast(v => v.asBoolean)

  implicit final val ByteArrayValueMapper: ValueMapper[Array[Byte]] =
    ValueMapper.fromCast(v => v.asByteArray)

  implicit final val DoubleValueMapper: ValueMapper[Double] =
    ValueMapper.fromCast(v => v.asDouble)

  implicit final val DurationValueMapper: ValueMapper[Duration] =
    ValueMapper.fromCast { v =>
      val isoDuration = v.asIsoDuration

      Duration
        .ZERO
        .plusDays(isoDuration.days)
        .plusSeconds(isoDuration.seconds)
        .plusNanos(isoDuration.nanoseconds.toLong)
    }

  implicit final val FloatValueMapper: ValueMapper[Float] =
    ValueMapper.fromCast(v => v.asFloat)

  implicit final val HNilMapper: ValueMapper[HNil] =
    new ValueMapper[HNil] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, HNil] =
        Right(HNil)
    }

  implicit final val IntValueMapper: ValueMapper[Int] =
    ValueMapper.fromCast(v => v.asInt)

  implicit final val IsoDurationValueMapper: ValueMapper[IsoDuration] =
    ValueMapper.fromCast(v => v.asIsoDuration)

  implicit final val LocalDateValueMapper: ValueMapper[LocalDate] =
    ValueMapper.fromCast(v => v.asLocalDate)

  implicit final val LocalDateTimeValueMapper: ValueMapper[LocalDateTime] =
    ValueMapper.fromCast(v => v.asLocalDateTime)

  implicit final val LocalTimeValueMapper: ValueMapper[LocalTime] =
    ValueMapper.fromCast(v => v.asLocalTime)

  implicit final val LongValueMapper: ValueMapper[Long] =
    ValueMapper.fromCast(v => v.asLong)

  implicit final val NodeValueMapper: ValueMapper[Node] =
    ValueMapper.fromCast(v => v.asNode)

  implicit final val OffsetDateTimeValueMapper: ValueMapper[OffsetDateTime] =
    ValueMapper.fromCast(v => v.asOffsetDateTime)

  implicit final val OffsetTimeValueMapper: ValueMapper[OffsetTime] =
    ValueMapper.fromCast(v => v.asOffsetTime)

  implicit final val PathValueMapper: ValueMapper[NPath] =
    ValueMapper.fromCast(v => v.asPath)

  implicit final val PeriodValueMapper: ValueMapper[Period] =
    ValueMapper.fromCast { v =>
      val isoDuration = v.asIsoDuration

      Period
        .ZERO
        .plusMonths(isoDuration.months)
        .plusDays(isoDuration.days)
    }

  implicit final val PointValueMapper: ValueMapper[Point] =
    ValueMapper.fromCast(v => v.asPoint)

  implicit final val RelationshipValueMapper: ValueMapper[Relationship] =
    ValueMapper.fromCast(v => v.asRelationship)

  implicit final val StringValueMapper: ValueMapper[String] =
    ValueMapper.fromCast(v => v.asString)

  implicit final val UUIDValueMapper: ValueMapper[UUID] =
    ValueMapper.fromCast(s => UUID.fromString(s.asString))

  implicit final val ValueValueMapper: ValueMapper[Value] =
    ValueMapper.fromCast(identity)

  implicit final val ZonedDateTimeValueMapper: ValueMapper[ZonedDateTime] =
    ValueMapper.fromCast(v => v.asZonedDateTime)

  implicit final def iterableValueMapper[T, I[_]](implicit factory: Factory[T, I[T]], mapper: ValueMapper[T]): ValueMapper[I[T]] =
    new ValueMapper[I[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, I[T]] =
        value match {
          case None =>
            Right(factory.newBuilder.result())

          case Some(value) =>
            traverseAs(factory)(value.values.asScala.iterator) { value =>
              mapper.to("", Option(value))
            }
        }
    }

  protected def getKeyValuesFrom(nmap: NMap): Iterator[(String, Value)] =
    nmap.keys.asScala.iterator.map(key => key -> nmap.get(key))

  implicit final def mapValueMapper[V, M[_, _]](implicit factory: Factory[(String, V), M[String, V]], mapper: ValueMapper[V]): ValueMapper[M[String, V]] =
    new ValueMapper[M[String, V]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, M[String, V]] =
        value match {
          case None =>
            Right(factory.newBuilder.result())

          case Some(value) =>
            traverseAs(factory)(getKeyValuesFrom(value)) {
              case (key, value) =>
                mapper.to("", Option(value)).map(v => key -> v)
            }
        }
    }

  implicit final def ccValueMarshallable[T](implicit resultMapper: ResultMapper[T], ct: ClassTag[T]): ValueMapper[T] =
    new ValueMapper[T] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, T] =
        value match {
          case Some(value: MapValue) =>
            resultMapper.to(getKeyValuesFrom(value).toList, Some(TypeHint(ct)))

          case Some(value) =>
            resultMapper.to((fieldName -> value) :: Nil, Some(TypeHint(ct)))

          case None =>
            Left(ConversionException(s"Cannot convert $fieldName [$value]"))
        }
    }

  implicit final def optionValueMapper[T](implicit mapper: ValueMapper[T]): ValueMapper[Option[T]] =
    new ValueMapper[Option[T]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Option[T]] =
        value match {
          case None =>
            Right(None)

          case Some(value) =>
            mapper.to(fieldName, Some(value)).map(r => Option(r))
        }
    }

  implicit final def pathMarshallable[N, R](implicit nm: ResultMapper[N], rm: ResultMapper[R]): ValueMapper[Path[N, R]] =
    new ValueMapper[Path[N, R]] {
      override def to(fieldName: String, value: Option[Value]): Either[Throwable, Path[N, R]] =
        value match {
          case None =>
            Left(PropertyNotFoundException(s"Property $fieldName not found"))

          case Some(value) =>
            if (value.`type` == InternalTypeSystem.TYPE_SYSTEM.PATH) {
              val path = value.asPath

              val nodes = traverseAsList(path.nodes.asScala.iterator.zipWithIndex) {
                case (node, index) => nm.to((s"node $index" -> new NodeValue(node)) :: Nil, None)
              }

              val relationships = traverseAsList(path.relationships.asScala.iterator.zipWithIndex) {
                case (relationship, index) => rm.to((s"relationship $index" -> new RelationshipValue(relationship)) :: Nil, None)
              }

              for {
                nodes <- nodes
                relationships <- relationships
              } yield Path(nodes, relationships, path)
            } else {
              Left(ConversionException(s"$fieldName of type ${value.`type`} cannot be converted into a Path"))
            }
        }
    }
}
