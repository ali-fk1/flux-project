package com.flux.fluxproject.services;

import com.flux.fluxproject.domain.User;
import com.flux.fluxproject.mappers.UserMapper;
import com.flux.fluxproject.model.UserDTO;
import com.flux.fluxproject.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public Mono<UserDTO> saveUser(UserDTO userDTO) {
        return userRepository.save(userMapper.userDtoToUser(userDTO)).map(userMapper::userToUserDto);
    }

    public Mono<User> registerUser(String email, String password) {
        return userRepository.findByEmail(email)
                .hasElement()
                .flatMap(exists->{
                    if(exists){
                        return Mono.error(new RuntimeException("Email already in use"));
                    }
                    String hashed = passwordEncoder.encode(password);

                  return userRepository.save(
                          User.builder()
                                  .email(email)
                                  .passwordHash(hashed)
                                  .enabled(false)
                                  .build()
                  );
                });
    }

    public Mono<Void> markVerified(UUID userId) {
        // Additional business checks can go here (e.g. emit audit event)
        return userRepository.setEnabledTrue(userId);
    }

    public Mono<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
