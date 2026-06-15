package com.trawhile.adapter.inbound.web;

import com.trawhile.adapter.inbound.web.api.AuthApi;
import com.trawhile.config.oidc.SupportedOidcProviders;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final Environment environment;

    public AuthController(Environment environment) {
        this.environment = environment;
    }

    @Override
    public ResponseEntity<List<String>> authProvidersGet() {
        return ResponseEntity.ok(SupportedOidcProviders.configuredRegistrationIds(environment));
    }
}
