package kyo.internal.util

/** Scala Native implementation of [[Crypto]].
  *
  * Placeholder: the production implementation will bind to OpenSSL `EVP_Digest` and `HMAC` via a `kyo_s3_crypto.c` shim, reusing the
  * libcrypto linkage already configured by `kyo-http`'s `openssl-native-settings`. Implemented in a follow-up pass; this stub allows the
  * cross-build to succeed while the FFI work lands separately.
  */
private[kyo] object CryptoImpl extends Crypto:

    def sha256(data: Array[Byte]): Array[Byte] =
        throw new NotImplementedError(
            "kyo-s3 Native crypto is not yet implemented. " +
                "OpenSSL FFI bindings are pending — track at kyo-s3/native/src/main/resources/scala-native/kyo_s3_crypto.c."
        )

    def hmacSha256(key: Array[Byte], data: Array[Byte]): Array[Byte] =
        throw new NotImplementedError(
            "kyo-s3 Native crypto is not yet implemented. " +
                "OpenSSL FFI bindings are pending."
        )

end CryptoImpl
