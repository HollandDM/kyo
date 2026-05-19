package kyo

import kyo.internal.xml.{XmlReaders, XmlSupport}

class XmlSupportTest extends S3Test:

    "parses valid XML" in run {
        XmlSupport.parse("<root><a>1</a><b>2</b></root>").map { elem =>
            assert(elem.label == "root")
            assert(XmlReaders.optionalText(elem, "a") == Present("1"))
            assert(XmlReaders.optionalText(elem, "b") == Present("2"))
        }
    }

    "aborts with S3DecodeException on invalid XML" in run {
        Abort.run(XmlSupport.parse("<root>")).map { res =>
            assert(res.isFailure)
        }
    }

    "respects default namespace" in run {
        // S3 envelopes everything in xmlns="http://s3.amazonaws.com/doc/2006-03-01/" but our readers match on local name only.
        XmlSupport.parse(
            """<List xmlns="http://example.com/ns"><Key>k1</Key></List>"""
        ).map { elem =>
            assert(elem.label == "List")
            assert(XmlReaders.optionalText(elem, "Key") == Present("k1"))
        }
    }

end XmlSupportTest
