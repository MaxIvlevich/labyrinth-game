package max.iv.labyrinth_game.model.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "game_users") // Имя таблицы в БД
@Getter
@Setter
@NoArgsConstructor
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "profile_avatar_url")
    private String profileAvatarUrl;

    private boolean enabled = true;

    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    private Set<String> roles = new HashSet<>();

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password; // Пароль должен быть зашифрован перед сохранением
        this.email = email;
        this.roles.add("ROLE_USER"); // По умолчанию добавляем роль USER
    }

    // --- Методы интерфейса UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Преобразуем наши роли (строки) в объекты, которые понимает Spring Security
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }


    @Override
    public boolean isAccountNonExpired() {
        return true; // В нашей логике аккаунты не истекают
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Логику блокировки можно будет добавить позже
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Пароли не истекают
    }

    @Override
    public boolean isEnabled() {
        return this.enabled; // Используем наше поле enabled
    }
}
