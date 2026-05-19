package kyo.internal

/** Percent-encoding helpers tuned for AWS SigV4 canonical URIs and query strings.
  *
  * AWS uses RFC 3986 with one important specialization for S3: when building the canonical URI, each path *segment* is percent-encoded, but
  * the `/` separator between segments is preserved. Standard URI encoders that treat the whole path as a single string would encode `/`
  * inside keys (correct) but would not preserve the separators — so we split, encode each segment, and rejoin.
  *
  * Per AWS docs, the unreserved set is `A-Z a-z 0-9 - _ . ~`. Everything else gets `%XX` encoding. Spaces become `%20` (never `+`).
  */
private[kyo] object PathEncoder:

    /** Encode an S3 object key for use in the request path.
      *
      * The key may contain `/` separators; each segment between separators is percent-encoded individually so the path structure is
      * preserved. A leading `/` is added if absent, matching the canonical URI form.
      */
    def encodeKey(key: String): String =
        if key.isEmpty then "/"
        else
            val sb        = new StringBuilder(key.length + 16)
            val withSlash = if key.charAt(0) == '/' then key else "/" + key
            var i         = 0
            while i < withSlash.length do
                val c = withSlash.charAt(i)
                if c == '/' then sb.append('/')
                else appendEncodedByte(sb, c)
                i += 1
            sb.toString

    /** Encode a query parameter name or value per RFC 3986 with the SigV4 rules. */
    def encodeQuery(s: String): String =
        val sb = new StringBuilder(s.length + 8)
        var i  = 0
        while i < s.length do
            appendEncodedByte(sb, s.charAt(i))
            i += 1
        sb.toString

    private def appendEncodedByte(sb: StringBuilder, c: Char): Unit =
        if isUnreserved(c) then sb.append(c)
        else
            // Encode the UTF-8 bytes of the character. Anything inside the BMP non-surrogate range fits in 1..3 bytes.
            val bytes = c.toString.getBytes("UTF-8")
            var b     = 0
            while b < bytes.length do
                sb.append('%')
                val v = bytes(b) & 0xff
                sb.append(hexChar(v >>> 4))
                sb.append(hexChar(v & 0x0f))
                b += 1
            end while

    private def isUnreserved(c: Char): Boolean =
        (c >= 'A' && c <= 'Z') ||
            (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') ||
            c == '-' || c == '_' || c == '.' || c == '~'

    private def hexChar(v: Int): Char =
        if v < 10 then ('0' + v).toChar else ('A' + v - 10).toChar

end PathEncoder
