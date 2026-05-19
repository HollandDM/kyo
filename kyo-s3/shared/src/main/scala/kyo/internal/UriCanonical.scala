package kyo.internal

/** Canonicalization helpers for the parts of an HTTP request that participate in the SigV4 signature.
  *
  *   - Canonical URI: pre-encoded path (handled by [[PathEncoder.encodeKey]]).
  *   - Canonical query string: parameters URL-encoded, sorted by encoded name then encoded value, joined with `&`.
  *   - Canonical headers: lowercased names, trimmed values with internal whitespace runs collapsed, sorted, formatted as
  *     `name:value\n`.
  *   - Signed headers list: lowercased names, sorted, joined with `;`.
  */
private[kyo] object UriCanonical:

    /** Build the canonical query string from a sequence of (name, value) pairs. Names and values are already raw (not pre-encoded); this
      * helper encodes them per AWS rules and sorts them.
      */
    def canonicalQuery(params: Seq[(String, String)]): String =
        if params.isEmpty then ""
        else
            params
                .map { case (k, v) => (PathEncoder.encodeQuery(k), PathEncoder.encodeQuery(v)) }
                .sortWith { (a, b) =>
                    val c = a._1.compareTo(b._1)
                    if c != 0 then c < 0 else a._2.compareTo(b._2) < 0
                }
                .map { case (k, v) => s"$k=$v" }
                .mkString("&")

    /** Trim leading/trailing whitespace and collapse internal whitespace runs to a single space per RFC 7230. */
    def normalizeHeaderValue(value: String): String =
        val trimmed = value.trim
        if trimmed.isEmpty then ""
        else
            val sb = new StringBuilder(trimmed.length)
            var i  = 0
            var inWs = false
            while i < trimmed.length do
                val c = trimmed.charAt(i)
                if c == ' ' || c == '\t' then
                    if !inWs then sb.append(' ')
                    inWs = true
                else
                    sb.append(c)
                    inWs = false
                i += 1
            sb.toString
        end if
    end normalizeHeaderValue

    /** Build the canonical headers block and the matching signed-headers list from the given header pairs. Each header name is lowercased;
      * headers with the same name combine their values with `,`. The block ends with a trailing newline as required by the algorithm.
      */
    def canonicalHeaders(headers: Seq[(String, String)]): (String, String) =
        val grouped =
            headers
                .map { case (n, v) => (n.toLowerCase, normalizeHeaderValue(v)) }
                .groupBy(_._1)
                .toSeq
                .sortBy(_._1)
                .map { case (name, vs) => (name, vs.map(_._2).mkString(",")) }
        val canonical = grouped.map { case (n, v) => s"$n:$v\n" }.mkString
        val signed    = grouped.map(_._1).mkString(";")
        (canonical, signed)
    end canonicalHeaders

end UriCanonical
