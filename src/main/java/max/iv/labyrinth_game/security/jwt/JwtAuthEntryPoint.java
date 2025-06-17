package max.iv.labyrinth_game.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        String acceptHeader = request.getHeader("Accept");
        boolean wantsHtml = false;
        if (acceptHeader != null) {
            List<String> acceptValues = Collections.list(request.getHeaders("Accept"));
            wantsHtml = acceptValues.stream().anyMatch(h -> h.contains(MediaType.TEXT_HTML_VALUE));
        }

        if (wantsHtml && !request.getRequestURI().startsWith("/api/")) { // Если хотят HTML и это не API запрос
            log.debug("Client expects HTML, redirecting to /login.html for unauthorized request to {}", request.getRequestURI());
            response.sendRedirect(request.getContextPath() + "/login.html"); // Редирект на страницу логина
        } else {
            // Для API запросов или если клиент не указал явное предпочтение HTML, возвращаем JSON 401
            log.debug("Client expects JSON or no specific type, sending 401 JSON error for unauthorized request to {}", request.getRequestURI());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            final Map<String, Object> body = new HashMap<>();
            body.put("timestamp", System.currentTimeMillis());
            body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
            body.put("error", "Unauthorized");
            body.put("message", authException.getMessage());
            body.put("path", request.getServletPath());

            final ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(response.getOutputStream(), body);
        }
    }
}

