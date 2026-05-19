package kyo.internal.codec

import kyo.*
import kyo.internal.xml.{XmlReaders, XmlSupport}
import scala.xml.Elem

/** Parses the XML body returned by `GET /?list-type=2` (ListObjectsV2).
  *
  * S3 envelopes the result in `<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">`. Our reader matches on local names only,
  * so the default namespace is irrelevant.
  *
  * Each `<Contents>` element describes an object; `<CommonPrefixes><Prefix>...</Prefix></CommonPrefixes>` carries grouped prefixes when a
  * delimiter is used.
  */
private[kyo] object ListBucketResultCodec:

    def parse(body: Span[Byte])(using Frame): S3ListResult < Abort[S3DecodeException] =
        XmlSupport.parse(body).map { root =>
            if root.label != "ListBucketResult" then
                Abort.fail(S3DecodeException(s"Expected <ListBucketResult> root, got <${root.label}>"))
            else parseRoot(root)
        }

    private def parseRoot(root: Elem)(using Frame): S3ListResult < Abort[S3DecodeException] =
        val isTruncated  = XmlReaders.optionalText(root, "IsTruncated").exists(_.equalsIgnoreCase("true"))
        val keyCount     = XmlReaders.optionalText(root, "KeyCount").flatMap(_.toIntOption.toMaybe).getOrElse(0)
        val contToken    = XmlReaders.optionalText(root, "ContinuationToken")
        val nextContToken = XmlReaders.optionalText(root, "NextContinuationToken")
        val commonPrefixes =
            XmlReaders.children(root, "CommonPrefixes").flatMap { cp =>
                XmlReaders.optionalText(cp, "Prefix").map(Chunk(_)).getOrElse(Chunk.empty)
            }

        Kyo
            .foreach(XmlReaders.children(root, "Contents"))(parseObject)
            .map { objects =>
                S3ListResult(
                    objects = Chunk.from(objects),
                    commonPrefixes = commonPrefixes,
                    isTruncated = isTruncated,
                    continuationToken = contToken,
                    nextContinuationToken = nextContToken,
                    keyCount = keyCount
                )
            }
    end parseRoot

    private def parseObject(elem: Elem)(using Frame): S3Object < Abort[S3DecodeException] =
        XmlReaders.requireText(elem, "Key").map { key =>
            val sizeStr        = XmlReaders.optionalText(elem, "Size").getOrElse("0")
            val size           = sizeStr.toLongOption.getOrElse(0L)
            val etag           = XmlReaders.optionalText(elem, "ETag").getOrElse("")
            val storageClass   = XmlReaders.optionalText(elem, "StorageClass")
            val lastModifiedRaw = XmlReaders.optionalText(elem, "LastModified").getOrElse("")
            Instant.parse(lastModifiedRaw) match
                case Result.Success(i) =>
                    S3Object(key, size, etag, i, storageClass)
                case _ =>
                    S3Object(key, size, etag, Instant.Epoch, storageClass)
        }

    extension [A](opt: Option[A])
        private def toMaybe: Maybe[A] = Maybe.fromOption(opt)

end ListBucketResultCodec
