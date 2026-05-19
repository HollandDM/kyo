package kyo

import kyo.*

/** An S3 client bound to a specific [[S3Config]] and an underlying [[HttpClient]].
  *
  * Created via [[S3.init]] (Scope-managed) or [[S3.initUnscoped]] (caller-managed). For most uses, prefer [[S3.withConfig]] which threads a
  * configuration through the default client without allocating a new instance.
  */
opaque type S3Client = S3Client.Backend

object S3Client:

    /** Internal backing record exposed for the `kyo` package only. */
    private[kyo] case class Backend(config: S3Config, httpClient: Maybe[HttpClient])

    /** Wrap a config (and optional dedicated HttpClient) as an [[S3Client]]. */
    private[kyo] def make(config: S3Config, httpClient: Maybe[HttpClient] = Absent): S3Client =
        Backend(config, httpClient)

    /** Read the underlying backend. Visible to the `kyo` package so [[S3]] can dispatch via it. */
    private[kyo] def backend(client: S3Client): Backend = client

end S3Client
