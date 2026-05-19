package kyo

import kyo.internal.UriCanonical

class UriCanonicalTest extends S3Test:

    "canonicalQuery" - {
        "empty input -> empty string" in {
            assert(UriCanonical.canonicalQuery(Seq.empty) == "")
        }

        "sorts by encoded name then value" in {
            val q = Seq("b" -> "2", "a" -> "1", "a" -> "0")
            assert(UriCanonical.canonicalQuery(q) == "a=0&a=1&b=2")
        }

        "encodes both names and values" in {
            val q = Seq("a b" -> "c/d")
            assert(UriCanonical.canonicalQuery(q) == "a%20b=c%2Fd")
        }
    }

    "normalizeHeaderValue" - {
        "trims whitespace" in {
            assert(UriCanonical.normalizeHeaderValue("  hello  ") == "hello")
        }

        "collapses internal whitespace runs" in {
            assert(UriCanonical.normalizeHeaderValue("a   b   c") == "a b c")
        }

        "preserves empty" in {
            assert(UriCanonical.normalizeHeaderValue("") == "")
            assert(UriCanonical.normalizeHeaderValue("   ") == "")
        }
    }

    "canonicalHeaders" - {
        "lowercases names and sorts" in {
            val (canon, signed) = UriCanonical.canonicalHeaders(Seq("Z-Header" -> "z", "A-Header" -> "a"))
            assert(canon == "a-header:a\nz-header:z\n")
            assert(signed == "a-header;z-header")
        }

        "combines repeated names with comma" in {
            val (canon, signed) = UriCanonical.canonicalHeaders(Seq("X" -> "1", "X" -> "2"))
            assert(canon == "x:1,2\n")
            assert(signed == "x")
        }
    }

end UriCanonicalTest
