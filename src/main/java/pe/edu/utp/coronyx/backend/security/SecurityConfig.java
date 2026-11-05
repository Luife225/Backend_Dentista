package pe.edu.utp.coronyx.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Preflight OPTIONS para navegadores y clientes Web
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Endpoints públicos de login y salud de infraestructura
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/v1/helpdesk/**", "/helpdesk/**").permitAll()

                        // Revalidación de sesión: requiere token de cualquier rol autorizado
                        .requestMatchers("/api/v1/auth/me", "/auth/me").authenticated()

                        // Directorio de usuarios de la clínica: ADMIN_CLINICA y SUPER_ADMIN (Soporte Técnico)
                        .requestMatchers("/api/v1/auth/users", "/auth/users").hasAnyRole("ADMIN_CLINICA", "SUPER_ADMIN")

                        // CRUD de cuentas y membresías de clínica: ADMIN_CLINICA y SUPER_ADMIN
                        .requestMatchers("/api/v1/clinic/users/**", "/clinic/users/**").hasAnyRole("ADMIN_CLINICA", "SUPER_ADMIN")

                        // Operación clínica de Pacientes y Citas:
                        .requestMatchers("/api/v1/patients/**", "/patients/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE", "SUPER_ADMIN")

                        .requestMatchers("/api/v1/citas/**", "/citas/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE", "SUPER_ADMIN")

                        .requestMatchers("/api/v1/me/**", "/me/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE", "SUPER_ADMIN")

                        // Historia clínica y atenciones: estrictamente ODONTOLOGO (Admin/recepción/paciente bloqueados con 403)
                        .requestMatchers("/api/v1/clinical-encounters/**", "/clinical-encounters/**",
                                         "/api/v1/atenciones-clinicas/**", "/atenciones-clinicas/**")
                        .hasRole("ODONTOLOGO")

                        // Archivos adjuntos y radiografías (F06-A, P05): roles operativos y paciente
                        .requestMatchers("/api/v1/attachments/**", "/attachments/**",
                                         "/api/v1/archivos/**", "/archivos/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE", "SUPER_ADMIN")

                        // Odontograma (D07/V06): roles operativos y paciente para consultar/gestionar
                        .requestMatchers("/api/v1/odontograma/**", "/odontograma/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE", "SUPER_ADMIN")

                        // Disponibilidad y configuración de horarios (A09/R05)
                        .requestMatchers("/api/v1/disponibilidad/**", "/disponibilidad/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE", "SUPER_ADMIN")

                        // Panel y métricas reales (A11/V10): equipo clínico y soporte
                        .requestMatchers("/api/v1/dashboard/**", "/dashboard/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "SUPER_ADMIN")

                        // Notificaciones transaccionales (V15): cualquier rol autenticado
                        .requestMatchers("/api/v1/notifications/**", "/notifications/**",
                                         "/api/v1/notificaciones/**", "/notificaciones/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE", "SUPER_ADMIN")

                        // Perfil de clínica (V09): lectura para equipo clínico, mutación validada por servicio
                        .requestMatchers("/api/v1/clinic/profile/**", "/api/v1/clinic/**", "/clinic/**")
                        .hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "SUPER_ADMIN")

                        .requestMatchers("/api/v1/caja/**").hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA", "ODONTOLOGO", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/inventario/**").hasAnyRole("ADMIN_CLINICA", "RECEPCIONISTA")
                        .requestMatchers("/api/v1/teleconsulta/**").hasAnyRole("ADMIN_CLINICA", "ODONTOLOGO", "PACIENTE")

                        // Cualquier otra ruta exige autenticación
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
