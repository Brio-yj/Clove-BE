package com.example.bemerch.auth.config;



import com.example.bemerch.auth.jwt.JwtAuthenticationFilter;
import com.example.bemerch.auth.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenProvider tokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf->csrf.disable())
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeRequests()
                .requestMatchers(HttpMethod.GET,"/merch/all").permitAll()
                .requestMatchers(HttpMethod.GET,"/merch/byEmail").hasAnyAuthority("CLIENT")
                .requestMatchers(HttpMethod.POST,"/merch/buy").hasAnyAuthority("CLIENT")
                .requestMatchers(HttpMethod.GET,"/merch/kakao/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/merch/refund").hasAnyAuthority("CLIENT")
                .requestMatchers(HttpMethod.POST,"/merch").hasAnyAuthority("PROVIDER")
                .requestMatchers("/error/**").permitAll()
                .requestMatchers("/static/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/seats")
                        .allowedOrigins("http://localhost:8081")
                        .allowedOrigins("http://localhost:8083")
                        .allowedMethods("POST", "GET", "PUT", "DELETE")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}