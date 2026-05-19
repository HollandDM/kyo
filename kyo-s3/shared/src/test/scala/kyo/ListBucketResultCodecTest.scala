package kyo

import kyo.internal.codec.ListBucketResultCodec

class ListBucketResultCodecTest extends S3Test:

    private val TwoObjects =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
          |  <Name>example-bucket</Name>
          |  <Prefix>photos/</Prefix>
          |  <KeyCount>2</KeyCount>
          |  <MaxKeys>1000</MaxKeys>
          |  <IsTruncated>false</IsTruncated>
          |  <Contents>
          |    <Key>photos/2024-01.jpg</Key>
          |    <LastModified>2024-01-02T03:04:05.000Z</LastModified>
          |    <ETag>"abc123"</ETag>
          |    <Size>4096</Size>
          |    <StorageClass>STANDARD</StorageClass>
          |  </Contents>
          |  <Contents>
          |    <Key>photos/2024-02.jpg</Key>
          |    <LastModified>2024-02-02T03:04:05.000Z</LastModified>
          |    <ETag>"def456"</ETag>
          |    <Size>8192</Size>
          |    <StorageClass>STANDARD</StorageClass>
          |  </Contents>
          |</ListBucketResult>""".stripMargin

    private val TruncatedWithToken =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
          |  <Name>b</Name>
          |  <KeyCount>1</KeyCount>
          |  <IsTruncated>true</IsTruncated>
          |  <NextContinuationToken>token-xyz</NextContinuationToken>
          |  <Contents>
          |    <Key>a.txt</Key>
          |    <LastModified>2024-01-01T00:00:00.000Z</LastModified>
          |    <ETag>"e"</ETag>
          |    <Size>1</Size>
          |  </Contents>
          |</ListBucketResult>""".stripMargin

    private val Empty =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
          |  <Name>b</Name>
          |  <KeyCount>0</KeyCount>
          |  <IsTruncated>false</IsTruncated>
          |</ListBucketResult>""".stripMargin

    private def bytesOf(s: String): Span[Byte] =
        Span.fromUnsafe(s.getBytes("UTF-8"))

    "parses two objects" in run {
        ListBucketResultCodec.parse(bytesOf(TwoObjects)).map { r =>
            assert(r.objects.length == 2)
            assert(r.objects(0).key == "photos/2024-01.jpg")
            assert(r.objects(0).size == 4096)
            assert(r.objects(0).etag == "\"abc123\"")
            assert(r.objects(1).key == "photos/2024-02.jpg")
            assert(!r.isTruncated)
            assert(r.keyCount == 2)
        }
    }

    "parses truncated with continuation token" in run {
        ListBucketResultCodec.parse(bytesOf(TruncatedWithToken)).map { r =>
            assert(r.isTruncated)
            assert(r.nextContinuationToken == Present("token-xyz"))
            assert(r.objects.length == 1)
        }
    }

    "parses empty result" in run {
        ListBucketResultCodec.parse(bytesOf(Empty)).map { r =>
            assert(r.objects.isEmpty)
            assert(!r.isTruncated)
            assert(r.keyCount == 0)
        }
    }

    "rejects wrong root element" in run {
        Abort.run(ListBucketResultCodec.parse(bytesOf("<NotList/>"))).map { res =>
            assert(res.isFailure)
        }
    }

end ListBucketResultCodecTest
