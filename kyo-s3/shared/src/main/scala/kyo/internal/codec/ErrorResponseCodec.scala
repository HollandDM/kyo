package kyo.internal.codec

import kyo.*
import kyo.internal.xml.{XmlReaders, XmlSupport}

/** Parses the XML body S3 returns for non-2xx responses.
  *
  * The shape is consistent across AWS S3 and most S3-compatible services:
  * {{{
  *   <Error>
  *     <Code>NoSuchKey</Code>
  *     <Message>The specified key does not exist.</Message>
  *     <Key>foo.txt</Key>            <!-- optional -->
  *     <BucketName>my-bucket</BucketName>  <!-- optional -->
  *     <RequestId>...</RequestId>
  *     <HostId>...</HostId>          <!-- optional -->
  *   </Error>
  * }}}
  *
  * Returns a [[ErrorResponseCodec.ParsedError]] record; the higher-level [[kyo.internal.ErrorParser]] turns this into a typed
  * [[S3Exception]] subtype.
  */
private[kyo] object ErrorResponseCodec:

    case class ParsedError(
        code: String,
        message: String,
        requestId: String,
        hostId: Maybe[String],
        bucketName: Maybe[String],
        resource: Maybe[String]
    )

    def parse(body: Span[Byte])(using Frame): ParsedError < Abort[S3DecodeException] =
        XmlSupport.parse(body).map { root =>
            if root.label != "Error" then
                Abort.fail(S3DecodeException(s"Expected <Error> root, got <${root.label}>"))
            else
                ParsedError(
                    code = XmlReaders.optionalText(root, "Code").getOrElse(""),
                    message = XmlReaders.optionalText(root, "Message").getOrElse(""),
                    requestId = XmlReaders.optionalText(root, "RequestId").getOrElse(""),
                    hostId = XmlReaders.optionalText(root, "HostId"),
                    bucketName = XmlReaders.optionalText(root, "BucketName"),
                    resource = XmlReaders.optionalText(root, "Resource")
                )
        }

end ErrorResponseCodec
