package prog24hour.prog24hourbackend.security.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import prog24hour.prog24hourbackend.security.dto.LoginRequest;
import prog24hour.prog24hourbackend.security.dto.LoginResponse;
import prog24hour.prog24hourbackend.security.entity.Role;
import prog24hour.prog24hourbackend.security.entity.User;
import prog24hour.prog24hourbackend.security.service.UserDetailsServiceImp;

import java.time.Instant;
import java.util.List;

import static java.util.stream.Collectors.joining;

@Slf4j
@RestController
@RequestMapping("/api/auth/")
public class AuthenticationController {

  @Value("${app.token-issuer}")
  private String tokenIssuer;

  @Value("${app.token-expiration}")
  private long tokenExpiration;

  private final AuthenticationManager authenticationManager;

  JwtEncoder encoder;

  public AuthenticationController(AuthenticationConfiguration authenticationConfiguration, JwtEncoder encoder) throws Exception {
    this.authenticationManager = authenticationConfiguration.getAuthenticationManager();
    this.encoder = encoder;
  }

  @PostMapping("login")
//  @Operation(summary = "Login", description = "Use this to login and get a token")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {

    try {
      UsernamePasswordAuthenticationToken uat = new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
      //The authenticate method will use the loadUserByUsername method in UserDetailsServiceImp
      Authentication authentication = authenticationManager.authenticate(uat);

      User user = (User) authentication.getPrincipal();
      Instant now = Instant.now();
      long expiry = tokenExpiration;
      String scope = authentication.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .collect(joining(" "));

      JwtClaimsSet claims = JwtClaimsSet.builder()
              .issuer(tokenIssuer)  //Only this for simplicity
              .issuedAt(now)
              .expiresAt(now.plusSeconds(tokenExpiration))
              .subject(user.getUsername())
              .claim("roles", scope)
              .build();
      JwsHeader jwsHeader = JwsHeader.with(() -> "HS256").build();
      String token = encoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
      List<String> roles = user.getRoles().stream().map(Role::getRoleName).toList();
      return ResponseEntity.ok()
              .body(new LoginResponse(user.getUsername(), token, roles));

    } catch (BadCredentialsException | AuthenticationServiceException e) {
      // AuthenticationServiceException is thrown if the user is not found
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, UserDetailsServiceImp.WRONG_USERNAME_OR_PASSWORD);
    }
  }
}
