package com.flux.fluxproject.mappers;

import com.flux.fluxproject.domain.Post;
import com.flux.fluxproject.model.PostDTO;
import org.mapstruct.Mapper;

@Mapper
public interface PostMapper {
    Post postDtoToPost(PostDTO postDto);
    PostDTO postToPostDto(Post post);
}
