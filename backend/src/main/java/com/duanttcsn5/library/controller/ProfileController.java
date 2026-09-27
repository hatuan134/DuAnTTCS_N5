package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.profile.ChangePasswordRequest;
import com.duanttcsn5.library.dto.profile.ProfileMessageResponse;
import com.duanttcsn5.library.dto.profile.UpdateProfileRequest;
import com.duanttcsn5.library.dto.profile.UserProfileResponse;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.getProfile(principal.id()));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(profileService.updateProfile(principal.id(), request, ipAddress));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ProfileMessageResponse> changePasswordWithPut(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(profileService.changePassword(principal.id(), request, ipAddress));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ProfileMessageResponse> changePasswordWithPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(profileService.changePassword(principal.id(), request, ipAddress));
    }
}
