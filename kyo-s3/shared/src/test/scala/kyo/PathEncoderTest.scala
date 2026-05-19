package kyo

import kyo.internal.PathEncoder

class PathEncoderTest extends S3Test:

    "encodeKey" - {
        "leaves simple keys alone" in {
            assert(PathEncoder.encodeKey("foo.txt") == "/foo.txt")
        }

        "prefixes a leading slash" in {
            assert(PathEncoder.encodeKey("a/b") == "/a/b")
            assert(PathEncoder.encodeKey("/a/b") == "/a/b")
        }

        "preserves '/' separators between segments" in {
            assert(PathEncoder.encodeKey("a/b/c.txt") == "/a/b/c.txt")
        }

        "encodes spaces as %20" in {
            assert(PathEncoder.encodeKey("hello world.txt") == "/hello%20world.txt")
        }

        "encodes special characters" in {
            assert(PathEncoder.encodeKey("foo&bar=baz") == "/foo%26bar%3Dbaz")
        }

        "encodes unicode as UTF-8 percent encoding" in {
            // 'é' is C3 A9 in UTF-8
            assert(PathEncoder.encodeKey("café.txt") == "/caf%C3%A9.txt")
        }

        "treats empty key as root" in {
            assert(PathEncoder.encodeKey("") == "/")
        }

        "leaves unreserved characters unencoded" in {
            assert(PathEncoder.encodeKey("a-_.~") == "/a-_.~")
        }
    }

    "encodeQuery" - {
        "encodes '/' inside query values" in {
            assert(PathEncoder.encodeQuery("a/b") == "a%2Fb")
        }

        "encodes spaces as %20 (never +)" in {
            assert(PathEncoder.encodeQuery("hello world") == "hello%20world")
        }

        "leaves unreserved set alone" in {
            assert(PathEncoder.encodeQuery("foo-bar_baz.qux~") == "foo-bar_baz.qux~")
        }
    }

end PathEncoderTest
