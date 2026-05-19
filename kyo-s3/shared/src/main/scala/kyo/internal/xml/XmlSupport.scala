package kyo.internal.xml

import java.nio.charset.StandardCharsets.UTF_8
import kyo.*
import scala.xml.{Elem, XML}

/** Wraps scala-xml's parser to produce an Elem and normalize errors into [[S3DecodeException]].
  *
  * Two entry points are provided: one for raw bytes (S3 always returns UTF-8 XML) and one for pre-decoded strings (handy in tests and for
  * fixed-string parsing). Both go through scala-xml's `XML.load` / `XML.loadString`, which is sufficient for S3's flat XML shapes: no DTD,
  * no namespaces beyond the default `xmlns` attribute on `ListBucketResult`, no exotic encoding.
  */
private[kyo] object XmlSupport:

    /** Parse a UTF-8 XML document from raw bytes. */
    def parse(bytes: Span[Byte])(using Frame): Elem < Abort[S3DecodeException] =
        parseInternal(new String(bytes.toArrayUnsafe, UTF_8))

    /** Parse a UTF-8 XML document from a String. */
    def parse(text: String)(using Frame): Elem < Abort[S3DecodeException] =
        parseInternal(text)

    private def parseInternal(text: String)(using Frame): Elem < Abort[S3DecodeException] =
        Result.catching[Throwable](XML.loadString(text)) match
            case Result.Success(elem) => elem
            case Result.Failure(t)    => Abort.fail(S3DecodeException("Failed to parse XML response", t))
            case Result.Panic(t)      => Abort.fail(S3DecodeException("XML parser panic", t))

end XmlSupport
