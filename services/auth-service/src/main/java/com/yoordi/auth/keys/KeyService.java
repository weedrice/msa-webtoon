package com.yoordi.auth.keys;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KeyService {

    public record RsaKey(String kid, KeyPair keyPair) {}

    private final AtomicReference<RsaKey> current = new AtomicReference<>();
    private final AtomicReference<RsaKey> previous = new AtomicReference<>();

    public KeyService() {
        rotate();
    }

    public synchronized void rotate() {
        RsaKey prev = current.get();
        current.set(new RsaKey(generateKid(), generateRsaKeyPair()));
        previous.set(prev);
    }

    public RsaKey current() { return current.get(); }
    public RsaKey previous() { return previous.get(); }

    public JWKSet jwkSet() {
        JWK cur = toPublicJwk(current.get());
        if (previous.get() != null) {
            JWK prev = toPublicJwk(previous.get());
            return new JWKSet(List.of(cur, prev));
        }
        return new JWKSet(cur);
    }

    private static String generateKid() { return UUID.randomUUID().toString(); }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    private static JWK toPublicJwk(RsaKey k) {
        if (k == null) return null;
        RSAPublicKey pub = (RSAPublicKey) k.keyPair.getPublic();
        return new RSAKey.Builder(pub)
                .keyID(k.kid)
                .build();
    }

    public RSAPrivateKey currentPrivateKey() { return (RSAPrivateKey) current.get().keyPair.getPrivate(); }
    public RSAPublicKey currentPublicKey() { return (RSAPublicKey) current.get().keyPair.getPublic(); }
    public RSAPublicKey previousPublicKey() { return previous.get() == null ? null : (RSAPublicKey) previous.get().keyPair.getPublic(); }
}

