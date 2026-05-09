package com.capston.demo.domain.user.dto.response;

import com.capston.demo.domain.user.entity.User;
import lombok.Getter;

@Getter
public class UserSearchResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final String profileImg;

    public UserSearchResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.profileImg = user.getProfileImg();
    }
}
