package com.lumalife.security;

import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.User;
import com.lumalife.service.DemoStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, DemoStore store) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**", "/api/v1/merchants/**", "/api/v1/categories", "/api/v1/reviews/merchant/**",
          "/api/v1/assistant/ask", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole(UserRole.PLATFORM_ADMIN.name())
        .requestMatchers("/api/v1/merchant-admin/**").hasRole(UserRole.MERCHANT_ADMIN.name())
        .requestMatchers("/api/v1/**").authenticated()
        .anyRequest().permitAll())
      .exceptionHandling(exceptions -> exceptions
        .authenticationEntryPoint((request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
        .accessDeniedHandler((request, response, accessDeniedException) -> response.setStatus(HttpServletResponse.SC_FORBIDDEN)))
      .addFilterBefore(new TokenFilter(store), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  static class TokenFilter extends OncePerRequestFilter {
    private final DemoStore store;

    TokenFilter(DemoStore store) {
      this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
      String auth = request.getHeader("Authorization");
      if (auth != null && auth.startsWith("Bearer ")) {
        String rawToken = auth.substring(7).trim();
        User user = rawToken.isBlank() ? null : store.userByToken(rawToken).orElse(null);
        if (user != null) {
          var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
          var token = new UsernamePasswordAuthenticationToken(user.phone(), null, List.of(authority));
          token.setDetails(user);
          SecurityContextHolder.getContext().setAuthentication(token);
        }
      }
      chain.doFilter(request, response);
    }
  }
}
