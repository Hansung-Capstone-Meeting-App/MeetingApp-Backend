package com.capston.demo.domain.user.dto.workspace;

import lombok.Getter;

@Getter
public class InvitationCountResponse {
    private final long count;

    public InvitationCountResponse(long count) {
        this.count = count;
    }
}

