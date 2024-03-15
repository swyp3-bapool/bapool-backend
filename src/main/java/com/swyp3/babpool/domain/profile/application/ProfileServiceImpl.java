package com.swyp3.babpool.domain.profile.application;

import com.swyp3.babpool.domain.possibledatetime.dao.PossibleDateTimeRepository;
import com.swyp3.babpool.domain.profile.api.request.ProfilePagingConditions;
import com.swyp3.babpool.domain.profile.api.request.ProfileUpdateRequest;
import com.swyp3.babpool.domain.profile.application.response.*;
import com.swyp3.babpool.domain.profile.domain.*;
import com.swyp3.babpool.domain.profile.application.response.ProfilePagingDto;
import com.swyp3.babpool.domain.profile.application.response.ProfilePagingResponse;
import com.swyp3.babpool.domain.profile.dao.ProfileRepository;
import com.swyp3.babpool.domain.profile.exception.ProfileException;
import com.swyp3.babpool.domain.profile.exception.errorcode.ProfileErrorCode;
import com.swyp3.babpool.domain.review.application.ReviewService;
import com.swyp3.babpool.domain.review.application.response.ReviewCountByTypeResponse;
import com.swyp3.babpool.domain.review.application.response.ReviewPagingResponse;
import com.swyp3.babpool.global.common.request.PagingRequestList;
import com.swyp3.babpool.infra.s3.application.AwsS3Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService{

    private final AwsS3Provider awsS3Provider;
    private final ProfileRepository profileRepository;
    private final PossibleDateTimeRepository possibleDateTimeRepository;
    private final ReviewService reviewService;
    @Override
    public Page<ProfilePagingResponse> getProfileListWithPageable(ProfilePagingConditions profilePagingConditions, Pageable pageable) {
        PagingRequestList<?> pagingRequest = PagingRequestList.builder()
                .condition(profilePagingConditions)
                .pageable(pageable)
                .build();
        List<ProfilePagingDto> profilePagingDtoList = null;
        int counts = 0;
        try {
            profilePagingDtoList = profileRepository.findAllByPageable(pagingRequest);
            counts = profileRepository.countByPageable(profilePagingConditions);
        } catch (Exception e) {
            log.error("프로필 리스트 조회 중 오류 발생. {}", e.getMessage());
            log.error("{}", e.getStackTrace());
            throw new ProfileException(ProfileErrorCode.PROFILE_LIST_ERROR, "프로필 리스트 조회 중 오류가 발생했습니다.");
        }
        List<ProfilePagingResponse> profilePagingResponse = profilePagingDtoList.stream()
                .map(ProfilePagingResponse::of)
                .toList();

        return new PageImpl<>(profilePagingResponse, pagingRequest.getPageable(), counts);
    }

    @Override
    public ProfileDetailResponse getProfileDetail(Long targetProfileId) {
        if(!isExistProfile(targetProfileId)){
            throw new ProfileException(ProfileErrorCode.PROFILE_TARGET_PROFILE_ERROR,"존재하지 않는 프로필을 조회하였습니다.");
        }
        ProfileDetail profileDetail = profileRepository.findProfileDetail(targetProfileId);
        ReviewCountByTypeResponse reviewCountByType = reviewService.getReviewCountByType(targetProfileId);
        List<ReviewPagingResponse> reviewListForProfileDetail = reviewService.getReviewListForProfileDetail(targetProfileId, 3);
        ProfileDetailResponse profileDetailResponse = new ProfileDetailResponse(profileDetail, reviewCountByType,reviewListForProfileDetail);
        return profileDetailResponse;
    }

    @Override
    public ProfileDefaultResponse getProfileDefault(Long userId) {
        Profile profile = profileRepository.findByUserId(userId);
        ProfileDefault daoResponse= profileRepository.findProfileDefault(profile.getProfileId());
        ProfileKeywordsResponse keywords = profileRepository.findKeywords(profile.getProfileId());

        return new ProfileDefaultResponse(daoResponse,keywords);
    }

    @Override
    public ProfileRegistrationResponse getProfileisRegistered(Long userId) {
        Profile profile = profileRepository.findByUserId(userId);
        Boolean isRegistered = profileRepository.findProfileIsRegistered(profile.getProfileId());

        return new ProfileRegistrationResponse(isRegistered);
    }

    private boolean isExistProfile(Long profileId) {
        if(profileRepository.findById(profileId)==null)
            return false;
        return true;
    }

    @Override
    public String updateProfileImage(Long userId, MultipartFile multipartFile) {
        if(verifyNoImageFile(multipartFile)){
            return null;
        }

        deleteExistImageIfUserSelfUploaded(userId);
        String uploadedImageUrl = awsS3Provider.uploadImage(multipartFile);

        profileRepository.updateProfileImageUrl(Profile.builder()
                .userId(userId)
                .profileImageUrl(uploadedImageUrl)
                .build());
        return uploadedImageUrl;
    }

    private void deleteExistImageIfUserSelfUploaded(Long userId) {
        Profile targetProfile = profileRepository.findByUserId(userId);
        // 만약 프로필 이미지가 없다면 삭제할 필요가 없다.
        if(StringUtils.hasText(targetProfile.getProfileImageUrl())){
            return;
        }
        // 만약 프로필 이미지 URL 이 S3에 저장된 이미지 URL이 아니라면(카카오,구글 CDN) 삭제할 필요가 없다.
        if(!targetProfile.getProfileImageUrl().startsWith(awsS3Provider.getAmazonS3ClientUrlPrefix())){
            log.info("ProfileService.deleteExistImageIfUserSelfUploaded, S3에 저장된 이미지가 아닙니다. URL: {}",targetProfile.getProfileImageUrl());
            return;
        }
        awsS3Provider.deleteImage(targetProfile.getProfileImageUrl());
    }

    private boolean verifyNoImageFile(MultipartFile multipartFile) {
        if(multipartFile == null || multipartFile.isEmpty()){
            log.info("ProfileService.checkNoImageFile, 첨부된 이미지 파일이 없습니다.");
            return true;
        }
        return false;
    }

    @Override
    public void saveProfile(Profile profile) {
        profileRepository.saveProfile(profile);
    }

    @Override
    public Profile getByUserId(Long userId) {
        return profileRepository.findByUserId(userId);
    }

    @Override
    public ProfileUpdateResponse updateProfileInfo(Long userId, ProfileUpdateRequest profileUpdateRequest) {
        validateRequestPossibleDateTime(profileUpdateRequest.getPossibleDate());
        Long profileId = profileRepository.findByUserId(userId).getProfileId();
        profileRepository.updateUserAccount(userId,profileUpdateRequest);
        profileRepository.updateProfile(profileId,profileUpdateRequest);
        profileRepository.deleteUserKeywords(userId);
        profileRepository.saveUserKeywords(userId,profileUpdateRequest.getKeywords());
        updatePossibleDateTime(userId, profileId,profileUpdateRequest);
        return new ProfileUpdateResponse(profileId);
    }

    private void validateRequestPossibleDateTime(Map<String, List<Integer>> possibleDateMap) {
        if(possibleDateMap.isEmpty()){
            throw new ProfileException(ProfileErrorCode.PROFILE_POSSIBLE_DATE_ERROR,"가능한 날짜와 시간을 최소 1개 이상 선택해주세요.");
        }
        // time : 8 ~ 22 only
        possibleDateMap.values().stream()
                .flatMap(List::stream)
                .forEach(time -> {
                    if(time < 8 || time > 22){
                        throw new ProfileException(ProfileErrorCode.PROFILE_POSSIBLE_DATE_ERROR,"가능한 시간은 8시부터 22시까지만 선택 가능합니다.");
                    }
                });
    }

//    public void updatePossibleDateTime(Long profileId, ProfileUpdateRequest profileUpdateRequest) {
//        profileRepository.deletePossibleTimes(profileId);
//        profileRepository.deletePossibleDates(profileId);
//
//        for (Map.Entry<String, List<Integer>> entry : profileUpdateRequest.getPossibleDate().entrySet()) {
//            String date = entry.getKey();
//
//            PossibleDate possibleDate = PossibleDate.builder()
//                    .possible_date(date)
//                    .profile_id(profileId)
//                    .build();
//            profileRepository.savePossibleDates(possibleDate);
//
//            List<Integer> times = entry.getValue();
//
//            for (Integer time : times) {
//                profileRepository.savePossibleTimes(time, possibleDate.getId());
//            }
//        }
//    }

    @Transactional
    @Override
    public void updatePossibleDateTime(Long userId, Long profileId, ProfileUpdateRequest profileUpdateRequest) {
        // TODO : possibleDateTimeRepository 의 deletePossibleDate, deletePossibleTime 쿼리 추가
        // TODO : possibleDateTimeRepository 의 insertPossibleDate, insertPossibleTime 쿼리 추가

        // 특정 프로필이 활성화한 가능한 날짜와 시간 리스트를 조회 (오늘 날짜를 포함한 미래의 가능한 날짜와 시간 리스트만 조회)
        List<PossibleDateAndTime> existPossibleDateTimeLists = possibleDateTimeRepository.findAllPossibleDateAndTimeByProfileIdAndNowDateWithoutAcceptOrDone(profileId);
        Map<String, List<Integer>> requestPossibleDateTime = profileUpdateRequest.getPossibleDate();

        // Map<"2024-03-15", [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18]>
        List<PossibleDateAndTime> deleteTargets = new ArrayList<>(); // existPossibleDateTimeLists 에는 있지만 requestPossibleDateTime 에는 없는 것
        Map<String, List<Integer>> insertTargets = new HashMap<>(); // requestPossibleDateTime 에는 있지만 existPossibleDateTimeLists 에는 없는 것

        for (PossibleDateAndTime exist : existPossibleDateTimeLists) {
            String existDate = exist.getPossibleDate();
            List<Integer> existTimes = exist.getPossibleTimeList();

            if (requestPossibleDateTime.containsKey(existDate)) {
                List<Integer> requestTimes = requestPossibleDateTime.get(existDate);


                // requestPossibleTime 에는 있지만 existPossibleTimeList 에는 없는 것 : 추가 대상
                List<Integer> insertTimeList = new ArrayList<>();
                for (Integer time : requestTimes) {
                    if (!existTimes.contains(time)) {
                        insertTimeList.add(time);
                    }
                }
                insertTargets.put(existDate, insertTimeList);

                // Determine times to delete: exist in existTimes but not in requestTimes
                List<Integer> timesToDelete = existTimes.stream()
                        .filter(time -> !requestTimes.contains(time))
                        .collect(Collectors.toList());

                if (!timesToDelete.isEmpty()) {
                    // Filter possibleTimeIdList based on timesToDelete for accurate ID mapping
                    List<Long> timeIdsToDelete = filterTimeIds(exist.getPossibleTimeIdList(), existTimes, timesToDelete);
                    deleteTargets.add(new PossibleDateAndTime(exist.getPossibleDateId(), existDate, timeIdsToDelete, timesToDelete));
                }
            } else {
                // The entire date is missing in the request, mark all times for deletion
                deleteTargets.add(exist);
            }
        }

        // requestPossibleDateTime 을 기준으로 순회하며 추가 대상을 구분
        for (Map.Entry<String, List<Integer>> entry : requestPossibleDateTime.entrySet()) {
            String requestPossibleDate = entry.getKey();
            List<Integer> requestPossibleTime = entry.getValue();

            // existPossibleDateTimeLists 에는 없는 날짜(requestPossibleDate)는 추가 대상
            if(existPossibleDateTimeLists.stream().noneMatch(
                    existPossibleDateTimeList -> existPossibleDateTimeList.getPossibleDate().equals(requestPossibleDate))
            ){
                insertTargets.put(requestPossibleDate, requestPossibleTime);
            }
        }

        // 삭제 대상이 비어있지 않다면 삭제
        if(!deleteTargets.isEmpty()){
            // Map 순회하며 삭제 : 삭제할 때는 시간 먼저 삭제
            deleteTargets.forEach((possibleDateAndTime) -> {
                try {
                    for (Long timeId : possibleDateAndTime.getPossibleTimeIdList()) {
                        possibleDateTimeRepository.deletePossibleTime(profileId, timeId);
                    }
                    possibleDateTimeRepository.deletePossibleDate(profileId, possibleDateAndTime.getPossibleDateId());
                } catch (Exception e) {
                    log.info("ProfileServiceImpl.updatePossibleDateTime, 가능한 날짜와 시간 삭제 중 오류 발생. {}", e.getMessage());
                    log.info("참조키 오류 발생 무시 : {}", possibleDateAndTime);
                }
            });

        }

        // 추가 대상이 비어있지 않다면 추가
        if(!insertTargets.isEmpty()){
            // Map 순회하며 추가 : 추가할 때는 날짜 먼저 추가
            insertTargets.forEach((date, timeList) -> {
                possibleDateTimeRepository.insertPossibleDate(profileId, date);
                possibleDateTimeRepository.insertPossibleTime(profileId, timeList);
            });
        }

    }

    private List<Long> filterTimeIds(List<Long> timeIds, List<Integer> existTimes, List<Integer> timesToDelete) {
        List<Long> filteredTimeIds = new ArrayList<>();
        for (int i = 0; i < existTimes.size(); i++) {
            if (timesToDelete.contains(existTimes.get(i))) {
                filteredTimeIds.add(timeIds.get(i));
            }
        }
        return filteredTimeIds;
    }

}