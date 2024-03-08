package com.swyp3.babpool.domain.user.application.response;

import com.swyp3.babpool.domain.appointment.application.response.AppointmentHistoryDoneResponse;
import com.swyp3.babpool.domain.profile.application.response.ProfileDetailDaoDto;
import com.swyp3.babpool.domain.review.application.response.ReviewCountByTypeResponse;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class MyPageResponse {
    //마이페이지 조회 최종 응답 DTO
    private Long profileId;
    private String name;
    private String profileImg;
    private String grade;
    private String intro;
    private String[] keywords;
    private Map<String,Long> reviewCount;
    private List<AppointmentHistoryDoneResponse> histories;

    public MyPageResponse(MyPageUserDaoDto myPageUserDaoDto, ReviewCountByTypeResponse reviewCount, List<AppointmentHistoryDoneResponse> histories) {
        this.profileId = myPageUserDaoDto.getProfileId();
        this.name=myPageUserDaoDto.getName();
        this.profileImg= myPageUserDaoDto.getProfileImg();
        this.grade= myPageUserDaoDto.getGrade();
        this.intro= myPageUserDaoDto.getIntro();
        this.keywords= myPageUserDaoDto.getKeywords();

        Map<String, Long> reviewCountMap = new HashMap<>();
        reviewCountMap.put("best",reviewCount.getBestCount());
        reviewCountMap.put("good",reviewCount.getGreatCount());
        reviewCountMap.put("bad",reviewCount.getBadCount());
        this.reviewCount=reviewCountMap;

        this.histories=histories;
    }
}
