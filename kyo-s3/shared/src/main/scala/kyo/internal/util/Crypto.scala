package kyo.internal.util

/** Platform-portable wrapper for the cryptographic primitives SigV4 needs.
  *
  * AWS SigV4 requires:
  *   - SHA-256 hashing of request bodies (or the constant `UNSIGNED-PAYLOAD` for streaming uploads)
  *   - HMAC-SHA256 for deriving signing keys (`kSecret -> kDate -> kRegion -> kService -> kSigning`) and signing the string-to-sign
  *
  * Each platform provides a concrete implementation under `kyo-s3/{jvm,js,native}/src/main/scala/kyo/internal/util/CryptoImpl.scala`:
  *   - JVM uses `javax.crypto.Mac` and `java.security.MessageDigest`
  *   - JS uses Node's `crypto` module (`createHmac`, `createHash`)
  *   - Native uses OpenSSL via FFI bindings, reusing the libcrypto linkage already configured by `kyo-http`
  *
  * Inputs are `Array[Byte]` rather than `Span[Byte]` because every backend reaches the JDK/JS/C API for raw arrays. Callers should convert
  * `Span[Byte]` at the boundary with `span.toArrayUnsafe` when ownership permits, or `span.toArray` for a defensive copy.
  */
private[kyo] trait Crypto:
    def sha256(data: Array[Byte]): Array[Byte]
    def hmacSha256(key: Array[Byte], data: Array[Byte]): Array[Byte]

private[kyo] object Crypto:

    /** Platform-supplied implementation. Resolved at compile time via the per-platform `CryptoImpl` object. */
    def instance: Crypto = CryptoImpl

    /** Hex (lowercase) digest of `data`. Convenience for the canonical request's `x-amz-content-sha256` header and the SigV4 payload hash. */
    def sha256Hex(data: Array[Byte]): String =
        Hex.encode(instance.sha256(data))
end Crypto
