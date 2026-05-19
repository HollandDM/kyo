package kyo.internal.util

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** JVM implementation of [[Crypto]] using the standard `java.security` and `javax.crypto` providers.
  *
  * `MessageDigest` and `Mac` instances are not thread-safe and not reusable across threads safely, so each call allocates a fresh instance.
  * SigV4 signing happens once per request, so the allocation cost is negligible compared to the network round-trip.
  */
private[kyo] object CryptoImpl extends Crypto:

    def sha256(data: Array[Byte]): Array[Byte] =
        MessageDigest.getInstance("SHA-256").digest(data)

    def hmacSha256(key: Array[Byte], data: Array[Byte]): Array[Byte] =
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(new SecretKeySpec(key, "HmacSHA256"))
        mac.doFinal(data)

end CryptoImpl
