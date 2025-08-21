package com.flux.fluxproject.mappers;

import com.flux.fluxproject.domain.User;
import com.flux.fluxproject.model.UserDTO;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {
    User userDtoToUser(UserDTO userDto);
    UserDTO userToUserDto(User user);
}
