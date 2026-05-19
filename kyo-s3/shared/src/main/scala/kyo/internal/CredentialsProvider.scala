package kyo.internal

import kyo.*

/** Resolves an [[S3Credentials]] reference into concrete `Static` (or `Anonymous`) credentials at the point of signing.
  *
  * v1 only needs to distinguish the two cases that are stored in [[S3Config]]: `Static` is returned as-is; `Anonymous` is returned as-is.
  * Future expansion points (file chain, IMDS, STS, web-identity) plug in here without changing the rest of the module.
  */
private[kyo] object CredentialsProvider:

    /** Materialize credentials for a single signing attempt. */
    def resolve(credentials: S3Credentials)(using Frame): S3Credentials < Any =
        credentials

end CredentialsProvider
