package kyo.chatgpt.util

import kyo.chatgpt.ais.desc
import zio.schema._
import zio.json._
import zio.json.ast._
import zio.json.internal.Write
import scala.annotation.StaticAnnotation
import zio.Chunk

case class JsonSchema(data: List[(String, Json)])

object JsonSchema {

  implicit val jsonSchemaEncoder: JsonEncoder[JsonSchema] = new JsonEncoder[JsonSchema] {
    override def unsafeEncode(js: JsonSchema, indent: Option[Int], out: Write): Unit = {
      implicitly[JsonEncoder[Json.Obj]].unsafeEncode(Json.Obj(js.data.toSeq: _*), indent, out)
    }
  }

  def apply(schema: Schema[_]): JsonSchema =
    new JsonSchema(convert(schema))

  def desc(c: Chunk[Any]): List[(String, Json)] =
    c.collect {
      case desc(v) =>
        "description" -> Json.Str(v.stripMargin)
    }.distinct.toList

  def convert(schema: Schema[_]): List[(String, Json)] = {
    def desc = this.desc(schema.annotations)
    schema match {
      case Schema.Primitive(StandardType.StringType, _) =>
        desc ++ List("type" -> Json.Str("string"))

      case Schema.Primitive(StandardType.IntType, _) =>
        desc ++ List("type" -> Json.Str("integer"), "format" -> Json.Str("int32"))

      case Schema.Primitive(StandardType.LongType, _) =>
        desc ++ List("type" -> Json.Str("integer"), "format" -> Json.Str("int64"))

      case Schema.Primitive(StandardType.DoubleType, _) =>
        desc ++ List("type" -> Json.Str("number"))

      case Schema.Primitive(StandardType.FloatType, _) =>
        desc ++ List("type" -> Json.Str("number"), "format" -> Json.Str("float"))

      case Schema.Primitive(StandardType.BoolType, _) =>
        desc ++ List("type" -> Json.Str("boolean"))

      case Schema.Optional(innerSchema, _) =>
        convert(innerSchema)

      case Schema.Sequence(innerSchema, _, _, _, _) =>
        List("type" -> Json.Str("array"), "items" -> Json.Obj(convert(innerSchema): _*))

      case schema: Schema.Enum[_] =>
        val cases = schema.cases.map(c => Json.Obj(convert(c.schema): _*))
        List("oneOf" -> Json.Arr(cases: _*))

      case schema: Schema.Record[_] =>
        val properties = schema.fields.foldLeft(List.empty[(String, Json)]) { (acc, field) =>
          acc :+ (field.name -> Json.Obj(
              (this.desc(field.annotations) ++ convert(field.schema)): _*
          ))
        }
        val requiredFields = schema.fields.collect {
          case field if !field.schema.isInstanceOf[Schema.Optional[_]] => Json.Str(field.name)
        }
        desc ++ List(
            "type"       -> Json.Str("object"),
            "properties" -> Json.Obj(properties.toSeq: _*),
            "required"   -> Json.Arr(requiredFields: _*)
        )

      case schema: Schema.Lazy[_] =>
        convert(schema.schema)

      case _ =>
        throw new UnsupportedOperationException("This schema type is not supported")
    }
  }
}
