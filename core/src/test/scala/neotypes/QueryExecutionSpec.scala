package neotypes

import neotypes.implicits.mappers.results._
import neotypes.implicits.syntax.string._

import scala.collection.immutable.{ListMap, ListSet, SortedMap}
import scala.concurrent.Future

class QueryExecutionSpec extends BaseIntegrationSpec[Future] {
  it should "retrieve multiple results as a List" in execute { s =>
    "match (p:Person) return p.name"
      .query[Int]
      .list(s)
      .map {
        names => assert(names == (0 to 10).toList)
      }
  }

  it should "retrieve multiple results as a Set" in execute { s =>
    "match (p:Person) return p.name"
      .query[Int]
      .set(s)
      .map {
        names => assert(names == (0 to 10).toSet)
      }
  }

  it should "retrieve multiple results as a Vector" in execute { s =>
    "match (p:Person) return p.name"
      .query[Int]
      .vector(s)
      .map {
        names => assert(names == (0 to 10).toVector)
      }
  }

  it should "retrieve multiple results as a Map" in execute { s =>
    "match (p:Person) return p.name, 1"
      .query[(Int, Int)]
      .map(s)
      .map {
        names => assert(names == (0 to 10).map(k => k -> 1).toMap)
      }
  }

  it should "retrieve multiple results as a custom collection (ListSet)" in execute { s =>
    "match (p:Person) return p.name"
      .query[Int]
      .collectAs(ListSet)(s)
      .map {
        names => assert(names == ListSet((0 to 10) : _*))
      }
  }

  it should "retrieve multiple results as a custom collection (ListMap)" in execute { s =>
    "match (p:Person) return p.name, 1"
      .query[(Int, Int)]
      .collectAs(ListMap)(s)
      .map {
        names => assert(names == ListMap((0 to 10).map(k => k -> 1) : _*))
      }
  }

  it should "retrieve multiple results as a custom collection (SortedMap)" in execute { s =>
    "match (p:Person) return p.name, 1"
      .query[(Int, Int)]
      .collectAs(SortedMap)(s)
      .map {
        names => assert(names == SortedMap((0 to 10).map(k => k -> 1) : _*))
      }
  }

  it should "retrieve a Neo4j LIST into a Scala collection (List)" in execute { s =>
    "unwind [1, 2] as x return x"
      .query[Int]
      .list(s)
      .map {
        names => assert(names == List(1, 2))
      }
  }

  it should "retrieve a Neo4j LIST into a Scala collection (ListSet)" in execute { s =>
    "unwind [1, 2] as x return x"
      .query[Int]
      .collectAs(ListSet)(s)
      .map {
        names => assert(names == ListSet(1, 2))
      }
  }

  override def initQuery: String = BaseIntegrationSpec.MULTIPLE_VALUES_INIT_QUERY
}
