package com.flux.fluxproject.mappers;

import com.flux.fluxproject.domain.Post;
import com.flux.fluxproject.model.PostDTO;
import com.flux.fluxproject.model.PostViewResponse;
import org.mapstruct.Mapper;

@Mapper
public interface PostViewMapper {
    Post postViewToPost(PostDTO postDto);
    PostViewResponse postToPostView(Post post);
}
