package com.flux.fluxproject.mappers;

import com.flux.fluxproject.domain.SocialAccount;
import com.flux.fluxproject.model.SocialAccountDTO;
import org.mapstruct.Mapper;

@Mapper
public interface SocialAccountMapper {
    SocialAccount socialAccountDtoToSocialAccount(SocialAccountDTO socialAccountDto);
    SocialAccountDTO socialAccountToSocialAccountDto(SocialAccount socialAccount);
}
