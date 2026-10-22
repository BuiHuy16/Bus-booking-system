package com.example.busbooking.api.mapper;

import com.example.busbooking.api.dto.user.*;
import com.example.busbooking.business.command.UpdateProfileCommand;
import com.example.busbooking.business.result.AuthenticatedUser;
import org.springframework.stereotype.Component;

@Component
public class UserDtoMapper {
    public UpdateProfileCommand command(UpdateProfileRequest request) {
        return new UpdateProfileCommand(request.fullName(), request.phone());
    }
    public UserResponse user(AuthenticatedUser user) {
        return new UserResponse(user.id(), user.fullName(), user.email(), user.phone(), user.role(), user.emailVerified());
    }
}
