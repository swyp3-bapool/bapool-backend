package com.swyp3.babpool.domain.profile.api;

import com.swyp3.babpool.domain.profile.api.request.ProfilePagingConditions;
import com.swyp3.babpool.domain.profile.application.ProfileService;
import com.swyp3.babpool.domain.profile.application.response.ProfilePagingResponse;
import com.swyp3.babpool.domain.profile.application.response.ProfileResponse;
import com.swyp3.babpool.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProfilePermitApi {

    private final ProfileService profileService;

    @GetMapping("/api/profile/list")
    public ApiResponse<Page<ProfilePagingResponse>> getProfileListWithPageable(@RequestParam String searchTerm,
                                                                               @RequestParam List<String> userGrades,
                                                                               @RequestParam List<String> keywords,
                                                                               @PageableDefault(size = 10) Pageable pageable){
        return ApiResponse.ok(profileService.getProfileListWithPageable(ProfilePagingConditions.builder()
                .search(searchTerm)
                .userGrades(userGrades)
                .keywords(keywords)
                .build(), pageable));
    }

}
