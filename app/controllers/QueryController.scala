package controllers


import com.daumkakao.s2graph.core._
import com.daumkakao.s2graph.core.mysqls._
import com.daumkakao.s2graph.core.types.{LabelWithDirection, VertexId}
import com.daumkakao.s2graph.logger
import config.Config
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc.{Action, Controller, Result}

import scala.concurrent._
import scala.language.postfixOps
import scala.util.Try

object QueryController extends Controller with RequestParser {

  import ApplicationController._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private def badQueryExceptionResults(ex: Exception) = Future.successful(BadRequest( s"""{"message": "${ex.getMessage}"}""").as(applicationJsonHeader))

  private def errorResults = Future.successful(Ok(s"${PostProcess.timeoutResults}\n").as(applicationJsonHeader))

  def getEdges() = withHeaderAsync(jsonParser) { request =>
    getEdgesInner(request.body)
  }

  def getEdgesExcluded() = withHeaderAsync(jsonParser) { request =>
    getEdgesExcludedInner(request.body)
  }

  private def eachQuery(post: (Seq[QueryResult], Seq[QueryResult]) => JsValue)(q: Query): Future[JsValue] = {
    val filterOutQueryResultsLs = q.filterOutQuery match {
      case Some(filterOutQuery) => Graph.getEdgesAsync(filterOutQuery)
      case None => Future.successful(Seq.empty)
    }

    for {
      queryResultsLs <- Graph.getEdgesAsync(q)
      filterOutResultsLs <- filterOutQueryResultsLs
    } yield {
      val json = post(queryResultsLs, filterOutResultsLs)
      json
    }
  }

  private def getEdgesAsync(jsonQuery: JsValue)
                           (post: (Seq[QueryResult], Seq[QueryResult]) => JsValue): Future[Result] = {
    if (!Config.IS_QUERY_SERVER) Unauthorized.as(applicationJsonHeader)
    val query = eachQuery(post) _

    Try {
      jsonQuery match {
        case JsArray(arr) =>
          val res = arr.map(toQuery(_)).map(query)
          val futureJson = Future.sequence(res).map(JsArray)
          val futureCnt = futureJson.map(jsArr => (jsArr \\ "size").flatMap(js => js.asOpt[Int]).sum)

          futureJson -> futureCnt
        case obj@JsObject(_) =>
          val futureJson = query(toQuery(obj))
          val futureCnt = futureJson.map(jsObj => (jsObj \ "size").asOpt[Int].getOrElse(0))

          futureJson -> futureCnt
      }
    } map { case (futureJson, futureCnt) =>

      for {
        json <- futureJson
        cnt <- futureCnt
      } yield jsonResponse(json, "result_size" -> cnt.toString)

    } recover {
      case e: KGraphExceptions.BadQueryException =>
        logger.error(s"$jsonQuery, $e", e)
        badQueryExceptionResults(e)
      case e: Exception =>
        logger.error(s"$jsonQuery, $e", e)
        errorResults
    } get
  }

  private def getEdgesExcludedAsync(jsonQuery: JsValue)(post: (Seq[QueryResult], Seq[QueryResult]) => JsValue): Future[Result] = {
    try {
      if (!Config.IS_QUERY_SERVER) Unauthorized.as(applicationJsonHeader)

      val q = toQuery(jsonQuery)
      val filterOutQuery = Query(q.vertices, List(q.steps.last))

      for (exclude <- Graph.getEdgesAsync(filterOutQuery); queryResultLs <- Graph.getEdgesAsync(q)) yield {
        val json = post(queryResultLs, exclude)
        val resultSize = (json \ "size").asOpt[Int].getOrElse(0)
        jsonResponse(json, "result_size" -> resultSize.toString)
      }
    } catch {
      case e: KGraphExceptions.BadQueryException =>
        logger.error(s"$jsonQuery, $e", e)
        badQueryExceptionResults(e)
      case e: Throwable =>
        logger.error(s"$jsonQuery, $e", e)
        errorResults
    }
  }

  def getEdgesInner(jsonQuery: JsValue) = {
    getEdgesAsync(jsonQuery)(PostProcess.toSimpleVertexArrJson)
  }

  def getEdgesExcludedInner(jsValue: JsValue) = {
    getEdgesExcludedAsync(jsValue)(PostProcess.toSimpleVertexArrJson)
  }

  def getEdgesWithGrouping() = withHeaderAsync(jsonParser) { request =>
    getEdgesWithGroupingInner(request.body)
  }

  def getEdgesWithGroupingInner(jsonQuery: JsValue) = {
    getEdgesAsync(jsonQuery)(PostProcess.summarizeWithListFormatted)
  }

  def getEdgesExcludedWithGrouping() = withHeaderAsync(jsonParser) { request =>
    getEdgesExcludedWithGroupingInner(request.body)
  }

  def getEdgesExcludedWithGroupingInner(jsonQuery: JsValue) = {
    getEdgesExcludedAsync(jsonQuery)(PostProcess.summarizeWithListExcludeFormatted)
  }

  def getEdgesGroupedInner(jsonQuery: JsValue) = {
    getEdgesAsync(jsonQuery)(PostProcess.summarizeWithList)
  }

  @deprecated(message = "deprecated", since = "0.2")
  def getEdgesGrouped() = withHeaderAsync(jsonParser) { request =>
    getEdgesGroupedInner(request.body)
  }

  @deprecated(message = "deprecated", since = "0.2")
  def getEdgesGroupedExcluded() = withHeaderAsync(jsonParser) { request =>
    getEdgesGroupedExcludedInner(request.body)
  }

  def getEdgesGroupedExcludedInner(jsonQuery: JsValue): Future[Result] = {
    try {
      if (!Config.IS_QUERY_SERVER) Unauthorized.as(applicationJsonHeader)

      val q = toQuery(jsonQuery)
      val filterOutQuery = Query(q.vertices, List(q.steps.last))

      for (exclude <- Graph.getEdgesAsync(filterOutQuery); queryResultLs <- Graph.getEdgesAsync(q)) yield {
        val json = PostProcess.summarizeWithListExclude(queryResultLs, exclude)
        val resultSize = (json \ "size").asOpt[Int].getOrElse(0)
        jsonResponse(json, "result_size" -> resultSize.toString)
      }
    } catch {
      case e: KGraphExceptions.BadQueryException =>
        logger.error(s"$jsonQuery, $e", e)
        badQueryExceptionResults(e)
      case e: Throwable =>
        logger.error(s"$jsonQuery, $e", e)
        errorResults
    }
  }

  @deprecated(message = "deprecated", since = "0.2")
  def getEdgesGroupedExcludedFormatted = withHeaderAsync(jsonParser) { request =>
    getEdgesGroupedExcludedFormattedInner(request.body)
  }

  def getEdgesGroupedExcludedFormattedInner(jsonQuery: JsValue): Future[Result] = {
    try {
      if (!Config.IS_QUERY_SERVER) Unauthorized.as(applicationJsonHeader)

      val q = toQuery(jsonQuery)
      val filterOutQuery = Query(q.vertices, List(q.steps.last))
      //      KafkaAggregatorActor.enqueue(queryInTopic, q.templateId().toString)

      for (exclude <- Graph.getEdgesAsync(filterOutQuery); queryResultLs <- Graph.getEdgesAsync(q)) yield {
        val json = PostProcess.summarizeWithListExcludeFormatted(queryResultLs, exclude)
        val resultSize = (json \ "size").asOpt[Int].getOrElse(0)
        jsonResponse(json, "result_size" -> resultSize.toString)
      }
    } catch {
      case e: KGraphExceptions.BadQueryException =>
        logger.error(s"$jsonQuery, $e", e)
        badQueryExceptionResults(e)
      case e: Throwable =>
        logger.error(s"$jsonQuery, $e", e)
        errorResults
    }
  }

  def getEdge(srcId: String, tgtId: String, labelName: String, direction: String) = Action.async { request =>
    if (!Config.IS_QUERY_SERVER) Future.successful(Unauthorized)
    val params = Json.arr(Json.obj("label" -> labelName, "direction" -> direction, "from" -> srcId, "to" -> tgtId))
    checkEdgesInner(params)
  }

  /**
   * Vertex
   */

  def checkEdgesInner(jsValue: JsValue) = {
    try {
      val params = jsValue.as[List[JsValue]]
      var isReverted = false
      val labelWithDirs = scala.collection.mutable.HashSet[LabelWithDirection]()
      val quads = for {
        param <- params
        labelName <- (param \ "label").asOpt[String]
        direction <- GraphUtil.toDir((param \ "direction").asOpt[String].getOrElse("out"))
        label <- Label.findByName(labelName)
        srcId <- jsValueToInnerVal((param \ "from").as[JsValue], label.srcColumnWithDir(direction.toInt).columnType, label.schemaVersion)
        tgtId <- jsValueToInnerVal((param \ "to").as[JsValue], label.tgtColumnWithDir(direction.toInt).columnType, label.schemaVersion)
      } yield {
          val labelWithDir = LabelWithDirection(label.id.get, direction)
          labelWithDirs += labelWithDir
          val (src, tgt, dir) = if (direction == 1) {
            isReverted = true
            (Vertex(VertexId(label.tgtColumnWithDir(direction.toInt).id.get, tgtId)),
              Vertex(VertexId(label.srcColumnWithDir(direction.toInt).id.get, srcId)), 0)
          } else {
            (Vertex(VertexId(label.srcColumnWithDir(direction.toInt).id.get, srcId)),
              Vertex(VertexId(label.tgtColumnWithDir(direction.toInt).id.get, tgtId)), 0)
          }

          //          logger.debug(s"SrcVertex: $src")
          //          logger.debug(s"TgtVertex: $tgt")
          //          logger.debug(s"direction: $dir")
          (src, tgt, label, dir.toInt)
        }

      Graph.checkEdges(quads, isInnerCall = false).map { case queryResultLs =>
        val edgeJsons = for {
          queryResult <- queryResultLs
          (edge, score) <- queryResult.edgeWithScoreLs
          edgeJson <- PostProcess.edgeToJson(if (isReverted) edge.duplicateEdge else edge, score, queryResult)
        } yield edgeJson

        val json = Json.toJson(edgeJsons)
        jsonResponse(json, "result_size" -> edgeJsons.size.toString)
      }
    } catch {
      case e: Throwable =>
        logger.error(s"$jsValue, $e", e)
        errorResults
    }
  }

  def checkEdges() = withHeaderAsync(jsonParser) { request =>
    if (!Config.IS_QUERY_SERVER) Future.successful(Unauthorized)

    checkEdgesInner(request.body)
  }

  def getVertices() = withHeaderAsync(jsonParser) { request =>
    if (!Config.IS_QUERY_SERVER) Unauthorized.as(applicationJsonHeader)

    val jsonQuery = request.body
    val ts = System.currentTimeMillis()
    val props = "{}"

    try {
      val vertices = request.body.as[List[JsValue]].flatMap { js =>
        val serviceName = (js \ "serviceName").as[String]
        val columnName = (js \ "columnName").as[String]
        for (id <- (js \ "ids").asOpt[List[JsValue]].getOrElse(List.empty[JsValue])) yield {
          Management.toVertex(ts, "insert", id.toString, serviceName, columnName, props)
        }
      }

      Graph.getVerticesAsync(vertices) map { vertices =>
        val json = PostProcess.verticesToJson(vertices)
        val resultSize = (json \ "size").asOpt[Int].getOrElse(0)
        jsonResponse(json, "result_size" -> resultSize.toString)
      }
    } catch {
      case e: play.api.libs.json.JsResultException =>
        logger.error(s"$jsonQuery, $e", e)
        badQueryExceptionResults(e)
      case e: Exception =>
        logger.error(s"$jsonQuery, $e", e)
        errorResults
    }
  }
}
