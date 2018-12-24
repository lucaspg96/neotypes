package neotypes

import java.time.Duration

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.neo4j.driver.v1
import org.neo4j.driver.v1.{GraphDatabase, Transaction, TransactionWork}
import org.scalatest.AsyncFlatSpec
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

abstract class BaseIntegrationSpec(initQuery: String = null) extends AsyncFlatSpec with ForAllTestContainer {
  override val container = GenericContainer("neo4j:3.4.5",
    env = Map("NEO4J_AUTH" -> "none"),
    exposedPorts = Seq(7687),
    waitStrategy = new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(15))
  )

  lazy val driver = GraphDatabase.driver(s"bolt://localhost:${container.mappedPort(7687)}")

  override def afterStart(): Unit = {
    if (initQuery != null) {
      val session = driver.session()
      try {
        session.writeTransaction(new TransactionWork[Unit] {
          override def execute(tx: v1.Transaction): Unit = tx.run(initQuery)
        })
      } finally {
        session.close()
      }
    }
  }
}