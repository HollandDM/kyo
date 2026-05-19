package kyo

import kyo.*

class S3BucketTest extends S3Test:

    "S3Bucket.apply" - {
        "accepts valid names" in run {
            S3Bucket("my-bucket").map(b => assert(b.value == "my-bucket"))
        }

        "rejects too-short names" in run {
            Abort.run(S3Bucket("a")).map { r =>
                assert(r.isFailure)
            }
        }

        "rejects too-long names" in run {
            val tooLong = "a" * 64
            Abort.run(S3Bucket(tooLong)).map { r =>
                assert(r.isFailure)
            }
        }

        "rejects uppercase letters" in run {
            Abort.run(S3Bucket("MyBucket")).map { r =>
                assert(r.isFailure)
            }
        }

        "rejects names starting with hyphen" in run {
            Abort.run(S3Bucket("-foo")).map { r =>
                assert(r.isFailure)
            }
        }

        "rejects IP-shaped names" in run {
            Abort.run(S3Bucket("192.168.0.1")).map { r =>
                assert(r.isFailure)
            }
        }

        "rejects consecutive dots" in run {
            Abort.run(S3Bucket("foo..bar")).map { r =>
                assert(r.isFailure)
            }
        }

        "accepts names with dots" in run {
            S3Bucket("my.bucket").map(b => assert(b.value == "my.bucket"))
        }
    }

    "S3Bucket.unsafe" - {
        "bypasses validation" in run {
            val b = S3Bucket.unsafe("UPPERCASE")
            assert(b.value == "UPPERCASE")
        }
    }

end S3BucketTest
