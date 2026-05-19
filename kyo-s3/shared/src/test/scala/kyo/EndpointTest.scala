package kyo

import kyo.internal.Endpoint

class EndpointTest extends S3Test:

    "virtual-host style (default)" - {
        "builds AWS host for an S3 bucket and key" in {
            val r = Endpoint.resolve(S3Config.default.region(S3Region.usWest2), "my-bucket", "foo.txt")
            assert(r.host == "my-bucket.s3.us-west-2.amazonaws.com")
            assert(r.url.host == "my-bucket.s3.us-west-2.amazonaws.com")
            assert(r.canonicalPath == "/foo.txt")
        }

        "uses '/' canonical path when key is empty" in {
            val r = Endpoint.resolve(S3Config.default, "my-bucket", "")
            assert(r.canonicalPath == "/")
            assert(r.url.path == "/")
        }
    }

    "path style" - {
        "puts bucket in the path under a custom endpoint" in {
            val cfg = S3Config.default
                .endpoint(HttpUrl(Present("http"), "minio.local", 9000, "/", Absent))
                .forcePathStyle(true)
            val r = Endpoint.resolve(cfg, "my-bucket", "foo.txt")
            assert(r.host == "minio.local:9000")
            assert(r.url.host == "minio.local")
            assert(r.url.port == 9000)
            assert(r.canonicalPath == "/my-bucket/foo.txt")
        }

        "encodes keys with special characters per-segment" in {
            val cfg = S3Config.default
                .endpoint(HttpUrl(Present("http"), "minio.local", 9000, "/", Absent))
                .forcePathStyle(true)
            val r = Endpoint.resolve(cfg, "my-bucket", "a/b c.txt")
            assert(r.canonicalPath == "/my-bucket/a/b%20c.txt")
        }
    }

end EndpointTest
