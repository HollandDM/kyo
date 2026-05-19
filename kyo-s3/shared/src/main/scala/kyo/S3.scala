package kyo

import kyo.*
import kyo.internal.Endpoint
import kyo.internal.ErrorParser
import kyo.internal.UriCanonical
import kyo.internal.codec.ListBucketResultCodec
import kyo.internal.codec.MetadataHeaders
import kyo.internal.sigv4.Signer
import kyo.internal.sigv4.Sigv4Constants
import kyo.internal.util.Crypto

/** S3 client surface. The five v1 operations — `getObject`, `putObject`, `deleteObject`, `headObject`, `listObjects` — share a single
  * request pipeline that resolves the endpoint, computes the SigV4 signature (or skips it for `S3Credentials.Anonymous`), and dispatches
  * through [[HttpClient]]. The active [[S3Client]] (and its [[S3Config]]) lives in fiber-local storage mirroring [[HttpClient]]'s `Local`
  * pattern.
  *
  * Top-level convenience methods use the default client; [[S3.let]] swaps in a custom client for a block, and [[S3.withConfig]] threads a
  * configuration transformation through whichever client is active.
  *
  * Non-2xx responses are parsed via [[kyo.internal.ErrorParser]] and surfaced as typed [[S3Exception]] subtypes
  * ([[S3NotFoundException]], [[S3AuthException]], or the general [[S3ApiException]]).
  */
object S3:

    // --- Local fiber-state ---

    private val defaultClient: S3Client = S3Client.make(S3Config.default)

    private val local: Local[S3Client] = Local.init(defaultClient)

    /** Replace the active client for a block. */
    def let[A, S](client: S3Client)(v: A < S)(using Frame): A < S =
        local.let(client)(v)

    /** Apply a config transformation to the active client for a block. Stacks with the surrounding scope's config. */
    def withConfig[A, S](f: S3Config => S3Config)(v: A < S)(using Frame): A < S =
        local.use { current =>
            val backend = S3Client.backend(current)
            local.let(S3Client.make(f(backend.config), backend.httpClient))(v)
        }

    /** Replace the active config entirely. Does not stack with the surrounding scope. */
    def withConfig[A, S](config: S3Config)(v: A < S)(using Frame): A < S =
        local.use { current =>
            val backend = S3Client.backend(current)
            local.let(S3Client.make(config, backend.httpClient))(v)
        }

    /** Initialize a fresh [[S3Client]]. v1 has no pooled resources of its own — the underlying [[HttpClient]] holds the connection pool —
      * so the `Scope` effect on the return type is reserved for forward compatibility with future resource acquisition.
      */
    def init(config: S3Config = S3Config.default)(using Frame): S3Client < (Async & Scope) =
        S3Client.make(config)

    /** Same as [[init]] but without `Scope`. */
    def initUnscoped(config: S3Config = S3Config.default)(using Frame): S3Client < Async =
        S3Client.make(config)

    // --- Public operations ---

    /** Get an object's bytes. Aborts with [[S3NotFoundException]] when the object/bucket is missing. */
    def getObject(bucket: S3Bucket, key: String)(using Frame): Span[Byte] < (Async & Abort[S3Exception]) =
        prepare(HttpMethod.GET, bucket, key, query = Seq.empty, payloadHash = Sigv4Constants.EmptyBodySha256Hex).map { ctx =>
            val resp =
                HttpClient.getBinaryResponse(ctx.url, ctx.headers, HttpQueryParams.empty, failOnError = false)
            mapHttpErrors(resp).map { response =>
                require2xx(response.status, "GET", bucket, key, response.fields.body)
                    .map(_ => response.fields.body)
            }
        }

    /** Upload bytes as an object. */
    def putObject(
        bucket: S3Bucket,
        key: String,
        body: Span[Byte],
        contentType: Maybe[String] = Absent,
        metadata: Map[String, String] = Map.empty
    )(using Frame): S3PutResult < (Async & Abort[S3Exception]) =
        val payloadHash = Crypto.sha256Hex(body.toArrayUnsafe)
        val extra       = bodyHeaders(contentType, metadata)
        prepare(HttpMethod.PUT, bucket, key, query = Seq.empty, payloadHash = payloadHash, extraHeaders = extra).map { ctx =>
            val resp = HttpClient.putBinaryResponse(ctx.url, body, ctx.headers, HttpQueryParams.empty, failOnError = false)
            mapHttpErrors(resp).map { response =>
                require2xx(response.status, "PUT", bucket, key, response.fields.body)
                    .map(_ => MetadataHeaders.parsePutResult(response.headers))
            }
        }
    end putObject

    /** Delete an object. */
    def deleteObject(bucket: S3Bucket, key: String)(using Frame): Unit < (Async & Abort[S3Exception]) =
        prepare(HttpMethod.DELETE, bucket, key, query = Seq.empty, payloadHash = Sigv4Constants.EmptyBodySha256Hex).map { ctx =>
            val resp = HttpClient.deleteBinaryResponse(ctx.url, ctx.headers, HttpQueryParams.empty, failOnError = false)
            mapHttpErrors(resp).map { response =>
                require2xx(response.status, "DELETE", bucket, key, response.fields.body)
            }
        }

    /** Fetch only the metadata for an object (no body transferred). */
    def headObject(bucket: S3Bucket, key: String)(using Frame): S3ObjectMetadata < (Async & Abort[S3Exception]) =
        prepare(HttpMethod.HEAD, bucket, key, query = Seq.empty, payloadHash = Sigv4Constants.EmptyBodySha256Hex).map { ctx =>
            val resp = HttpClient.head(ctx.url, ctx.headers, HttpQueryParams.empty, failOnError = false)
            mapHttpErrors(resp).map { response =>
                require2xx(response.status, "HEAD", bucket, key, Span.empty[Byte])
                    .map(_ => MetadataHeaders.parse(response.headers))
            }
        }

    /** List objects under a bucket. */
    def listObjects(
        bucket: S3Bucket,
        prefix: Maybe[String] = Absent,
        continuationToken: Maybe[String] = Absent,
        maxKeys: Maybe[Int] = Absent,
        delimiter: Maybe[String] = Absent
    )(using Frame): S3ListResult < (Async & Abort[S3Exception]) =
        val query = buildListQuery(prefix, continuationToken, maxKeys, delimiter)
        prepare(HttpMethod.GET, bucket, "", query = query, payloadHash = Sigv4Constants.EmptyBodySha256Hex).map { ctx =>
            val resp = HttpClient.getBinaryResponse(ctx.url, ctx.headers, HttpQueryParams.empty, failOnError = false)
            mapHttpErrors(resp).map { response =>
                require2xx(response.status, "GET", bucket, "", response.fields.body)
                    .map(_ => ListBucketResultCodec.parse(response.fields.body))
                    .flatMap(identity)
            }
        }

    // --- Internal pipeline ---

    private case class RequestCtx(url: HttpUrl, headers: HttpHeaders, canonicalPath: String)

    /** Resolve the endpoint, build the canonical query string, sign the request (when credentials are present), and return everything the
      * dispatcher needs to put the request on the wire.
      */
    private def prepare(
        method: HttpMethod,
        bucket: S3Bucket,
        key: String,
        query: Seq[(String, String)],
        payloadHash: String,
        extraHeaders: Seq[(String, String)] = Seq.empty
    )(using Frame): RequestCtx < (Async & Abort[S3Exception]) =
        local.use { client =>
            val backend  = S3Client.backend(client)
            val config   = backend.config
            val resolved = Endpoint.resolve(config, bucket.value, key)
            val rawQuery = UriCanonical.canonicalQuery(query)
            val finalUrl =
                if rawQuery.isEmpty then resolved.url
                else resolved.url.copy(rawQuery = Present(rawQuery))

            Clock.now.map { now =>
                val wireHeaders = buildHeaders(
                    method = httpMethodName(method),
                    host = resolved.host,
                    canonicalPath = resolved.canonicalPath,
                    query = query,
                    payloadHash = payloadHash,
                    extraHeaders = extraHeaders,
                    config = config,
                    now = now
                )
                RequestCtx(finalUrl, HttpHeaders.init(wireHeaders), resolved.canonicalPath)
            }
        }
    end prepare

    /** Compute the headers that go on the wire, including SigV4 `Authorization` when credentials are present. */
    private def buildHeaders(
        method: String,
        host: String,
        canonicalPath: String,
        query: Seq[(String, String)],
        payloadHash: String,
        extraHeaders: Seq[(String, String)],
        config: S3Config,
        now: Instant
    ): Seq[(String, String)] =
        config.credentials match
            case S3Credentials.Anonymous =>
                ("Host" -> host) +: extraHeaders
            case s: S3Credentials.Static =>
                val (amzDate, _) = Signer.formatTimes(now)
                val sessionToken = s.sessionToken.fold(Seq.empty[(String, String)])(t => Seq("x-amz-security-token" -> t))
                val toSign: Seq[(String, String)] =
                    Seq("host" -> host, "x-amz-date" -> amzDate, "x-amz-content-sha256" -> payloadHash) ++ sessionToken
                val signed = Signer.sign(
                    Signer.Request(
                        method = method,
                        path = canonicalPath,
                        query = query,
                        headers = toSign,
                        payloadHashHex = payloadHash
                    ),
                    s,
                    config.region,
                    now
                )
                Seq(
                    "Host"                 -> host,
                    "x-amz-date"           -> amzDate,
                    "x-amz-content-sha256" -> payloadHash,
                    "Authorization"        -> signed.authorization
                ) ++ sessionToken ++ extraHeaders
        end match
    end buildHeaders

    private def httpMethodName(method: HttpMethod): String =
        if method == HttpMethod.GET then "GET"
        else if method == HttpMethod.PUT then "PUT"
        else if method == HttpMethod.DELETE then "DELETE"
        else if method == HttpMethod.HEAD then "HEAD"
        else if method == HttpMethod.POST then "POST"
        else "GET"

    private def buildListQuery(
        prefix: Maybe[String],
        continuationToken: Maybe[String],
        maxKeys: Maybe[Int],
        delimiter: Maybe[String]
    ): Seq[(String, String)] =
        val b = Seq.newBuilder[(String, String)]
        b += ("list-type" -> "2")
        prefix.foreach(p => b += ("prefix" -> p))
        continuationToken.foreach(t => b += ("continuation-token" -> t))
        maxKeys.foreach(m => b += ("max-keys" -> m.toString))
        delimiter.foreach(d => b += ("delimiter" -> d))
        b.result()

    private def bodyHeaders(contentType: Maybe[String], metadata: Map[String, String]): Seq[(String, String)] =
        val b = Seq.newBuilder[(String, String)]
        contentType.foreach(ct => b += ("Content-Type" -> ct))
        metadata.foreach { case (k, v) => b += (s"x-amz-meta-$k" -> v) }
        b.result()

    /** Map any [[HttpException]] from the underlying client into a typed [[S3Exception]]. */
    private def mapHttpErrors[A](v: A < (Async & Abort[HttpException]))(using Frame): A < (Async & Abort[S3Exception]) =
        Abort.recover[HttpException] {
            case e: HttpConnectionException => Abort.fail(S3ConnectionException("transport error", e))
            case e: HttpException           => Abort.fail(S3ConnectionException(e.getClass.getSimpleName, e))
        }(v)

    /** If status is 2xx, succeed; otherwise parse the body as an S3 error and abort. */
    private def require2xx(
        status: HttpStatus,
        method: String,
        bucket: S3Bucket,
        key: String,
        body: Span[Byte]
    )(using Frame): Unit < (Async & Abort[S3Exception]) =
        if status.isSuccess then ()
        else
            ErrorParser.toException(
                status = status,
                method = method,
                urlPath = if key.isEmpty then s"/${bucket.value}" else s"/${bucket.value}/$key",
                bucket = bucket.value,
                key = if key.isEmpty then Absent else Present(key),
                body = body
            ).map(Abort.fail(_))

end S3
