package benchmark

import com.daumkakao.s2graph.logger
import play.api.libs.json.JsNumber
import play.api.test.{FakeApplication, PlaySpecification, WithApplication}
import play.libs.Json

class JsonBenchmarkSpec extends PlaySpecification {
  val wrapStr = s"\n=================================================="
  def duration[T](prefix: String = "")(block: => T) = {
    val startTs = System.currentTimeMillis()
    val ret = block
    val endTs = System.currentTimeMillis()
    logger.info(s"$wrapStr\n$prefix: took ${endTs - startTs} ms$wrapStr")
    ret
  }

  "to json" should {
    implicit val app = FakeApplication()

    "json benchmark" in new WithApplication(app) {

      duration("map to json") {
        (0 to 100000) foreach { n =>
          val numberMaps = (0 to 100).map { n => (n.toString -> JsNumber(n * n)) }.toMap
          Json.toJson(numberMaps)
        }
      }

      duration("directMakeJson") {
        (0 to 100000) foreach { n =>
          var jsObj = play.api.libs.json.Json.obj()
          (0 to 100).foreach { n =>
            jsObj += (n.toString -> JsNumber(n * n))
          }
        }
      }

      duration("map to json 2") {
        (0 to 500000) foreach { n =>
          val numberMaps = (0 to 100).map { n => (n.toString -> JsNumber(n * n)) }.toMap
          Json.toJson(numberMaps)
        }
      }

      duration("directMakeJson 2") {
        (0 to 500000) foreach { n =>
          var jsObj = play.api.libs.json.Json.obj()
          (0 to 100).foreach { n =>
            jsObj += (n.toString -> JsNumber(n * n))
          }
        }
      }

      true
    }
  }
}
