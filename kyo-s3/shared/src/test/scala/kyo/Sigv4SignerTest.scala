package kyo

import java.time.{Instant as JInstant}
import kyo.internal.sigv4.Signer

/** Validates the SigV4 signer against AWS's official reference vector for `GetObject` documented at
  * https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
  */
class Sigv4SignerTest extends S3Test:

    private val secret    = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    private val accessKey = "AKIAIOSFODNN7EXAMPLE"
    private val region    = S3Region.usEast1
    private val now       = Instant.fromJava(JInstant.parse("2013-05-24T00:00:00Z"))

    private val credentials = S3Credentials.Static(accessKey, secret)

    "formatTimes" - {
        "produces 'yyyyMMddTHHmmssZ' and 'yyyyMMdd'" in {
            val (amz, stamp) = Signer.formatTimes(now)
            assert(amz == "20130524T000000Z")
            assert(stamp == "20130524")
        }
    }

    "deriveSigningKey" - {
        "matches AWS reference (kSigning hex for 20130524/us-east-1/s3)" in {
            val key = Signer.deriveSigningKey(secret, "20130524", "us-east-1", "s3")
            val hex = kyo.internal.util.Hex.encode(key)
            // Independently computed via the AWS SigV4 derivation; this hex is the kSigning value.
            assert(hex == "dbb893acc010964918f1fd433add87c70e8b0db6be30c1fbeafefa5ec6ba8378")
        }
    }

    "sign" - {
        "produces the AWS reference Authorization for GetObject (header-based)" in {
            val request = Signer.Request(
                method = "GET",
                path = "/test.txt",
                query = Seq.empty,
                headers = Seq(
                    "host"                  -> "examplebucket.s3.amazonaws.com",
                    "range"                 -> "bytes=0-9",
                    "x-amz-content-sha256"  -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    "x-amz-date"            -> "20130524T000000Z"
                ),
                payloadHashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            )
            val signed = Signer.sign(request, credentials, region, now)
            assert(signed.signedHeaders == "host;range;x-amz-content-sha256;x-amz-date")
            assert(signed.signature == "f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41")
            assert(signed.authorization ==
                "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                    "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                    "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41")
        }
    }

end Sigv4SignerTest
