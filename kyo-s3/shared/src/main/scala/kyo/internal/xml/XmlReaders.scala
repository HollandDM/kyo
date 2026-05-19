package kyo.internal.xml

import kyo.*
import scala.xml.{Elem, Node}

/** Small set of combinators over scala-xml that S3 codecs use to extract typed values without depending on scala-xml's API surface beyond
  * this file. If we ever swap XML libraries, only this file and [[XmlSupport]] need to change.
  *
  * The element-matching helpers all match on local element labels (`Node.label`), ignoring namespace prefixes. That is correct for S3:
  * `<ListBucketResult>` lives in the default `http://s3.amazonaws.com/doc/2006-03-01/` namespace, and `<Error>` lives in none — both have
  * unique local names within their document.
  */
private[kyo] object XmlReaders:

    /** First direct child with the given local name. */
    def child(elem: Elem, name: String): Maybe[Elem] =
        var i = 0
        val cs = elem.child
        while i < cs.length do
            cs(i) match
                case e: Elem if e.label == name => return Present(e)
                case _                          => ()
            i += 1
        Absent

    /** All direct children with the given local name, in document order. */
    def children(elem: Elem, name: String): Chunk[Elem] =
        val b = Chunk.newBuilder[Elem]
        val cs = elem.child
        var i  = 0
        while i < cs.length do
            cs(i) match
                case e: Elem if e.label == name => b += e
                case _                          => ()
            i += 1
        b.result()

    /** Concatenated text of direct text children (ignores child elements). */
    def text(elem: Elem): String =
        val sb = new StringBuilder
        val cs = elem.child
        var i  = 0
        while i < cs.length do
            val n = cs(i)
            if !n.isInstanceOf[Elem] then sb.append(n.text)
            i += 1
        sb.toString

    /** Required child element. Aborts with [[S3DecodeException]] when missing. */
    def requireChild(elem: Elem, name: String)(using Frame): Elem < Abort[S3DecodeException] =
        child(elem, name) match
            case Present(e) => e
            case Absent     => Abort.fail(S3DecodeException(s"Missing required <$name> in <${elem.label}>"))

    /** Required text of a child element. */
    def requireText(elem: Elem, name: String)(using Frame): String < Abort[S3DecodeException] =
        requireChild(elem, name).map(text)

    /** Optional text of a child element. */
    def optionalText(elem: Elem, name: String): Maybe[String] =
        child(elem, name).map(text)

end XmlReaders
