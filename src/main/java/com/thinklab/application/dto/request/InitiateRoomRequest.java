package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for adding a new Room to an existing Building (BIAN Behavior Qualifier: {@code room/initiate}).
 */
@Serdeable
public record InitiateRoomRequest(
        @NotBlank(message = "Room Name is required")
        String roomName,

        String roomType
) {}
