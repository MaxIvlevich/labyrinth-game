package max.iv.labyrinth_game.security.service;


import lombok.extern.slf4j.Slf4j;
import max.iv.labyrinth_game.model.user.User;
import max.iv.labyrinth_game.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    @Autowired
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        log.debug("Attempting to load user by identifier: {}", identifier);
        // Сначала пытаемся найти по username (если это может быть username)
        Optional<User> user = userRepository.findByUsername(identifier);
        if (user.isPresent()) {
            return user.get();
        }
        // Если не нашли по username, пытаемся по email
        user = userRepository.findByEmail(identifier);
        if (user.isPresent()) {
            return user.get();
        }
        throw new UsernameNotFoundException("User Not Found with identifier: " + identifier);
    }
}
