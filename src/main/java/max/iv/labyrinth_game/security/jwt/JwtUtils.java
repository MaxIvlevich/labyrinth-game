package max.iv.labyrinth_game.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.dto.auth.JwtAuthenticationDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
@Slf4j
@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecretString;

    public JwtAuthenticationDTO generateAuthToken(String email) {
        JwtAuthenticationDTO jwtDTO =new JwtAuthenticationDTO();
        jwtDTO.setAccessToken(generateJwtToken(email));
        jwtDTO.setRefreshToken(generateJwtRefreshToken(email));
        return jwtDTO;

    }

    public String generateJwtToken(String email) {
        Date date = Date.from(LocalDateTime.now()
                .plusHours(1)
                .atZone(ZoneId.systemDefault())
                .toInstant());

        return Jwts.builder()
                .subject(email)
                .expiration(date)
                .signWith(getSignInKey())
                .compact();
    }

    private String generateJwtRefreshToken(String email) {
        Date date = Date.from(LocalDateTime.now()
                .plusDays(1)
                .atZone(ZoneId.systemDefault())
                .toInstant());

        return Jwts.builder()
                .subject(email)
                .expiration(date)
                .signWith(getSignInKey())
                .compact();
    }
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecretString);
        return Keys.hmacShaKeyFor(keyBytes);

    }

    public JwtAuthenticationDTO refreshBaseToken(String email,String refreshToken){
        JwtAuthenticationDTO jwtDTO =new JwtAuthenticationDTO();
        jwtDTO.setTokenType(generateJwtToken(email));
        jwtDTO.setRefreshToken(refreshToken);
        return jwtDTO;

    }

    public String getEmailFromToken(String token){
        Claims claims = Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();

    }

    public boolean validateJwtToken(String token){
        try {
            Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return true;
        } catch (ExpiredJwtException expiredJwtException) {
            log.error("ExpiredJwtException",expiredJwtException);
        } catch (UnsupportedJwtException exception){
            log.error("UnsupportedJwtException",exception);
        } catch (MalformedJwtException exception){
            log.error("MalformedJwtException",exception);
        } catch (SecurityException exception){
            log.error("SecurityException",exception);
        } catch (Exception exception){
            log.error("Invalid token",exception);
        }
        return false;
    }
}
