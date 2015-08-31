package s2.counter

import org.apache.hadoop.hbase.client.Result
import play.api.libs.json.Json
import s2.counter.core.ExactQualifier

/**
 * Created by alec on 15. 4. 7..
 */
// item1 -> likedCount -> month:2015-10, 1
// edge
  // policyId = Label.findByName(likedCount).id.get
  // item = edge.srcVertexId
  // results =
case class TrxLog(success: Boolean, policyId: Int, item: String, results: Iterable[TrxLogResult])

// interval = m, ts = 2015-10, "age.gender.20.M", 1, 2
case class TrxLogResult(interval: String, ts: Long, dimension: String, value: Long, result: Long = -1)

object TrxLogResult {
  implicit val writes = Json.writes[TrxLogResult]
  implicit val reads = Json.reads[TrxLogResult]
  implicit val formats = Json.format[TrxLogResult]
}

object TrxLog {
  implicit val writes = Json.writes[TrxLog]
  implicit val reads = Json.reads[TrxLog]
  implicit val formats = Json.format[TrxLog]
}
