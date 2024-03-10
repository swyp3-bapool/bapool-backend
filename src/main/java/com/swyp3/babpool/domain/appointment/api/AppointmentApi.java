package com.swyp3.babpool.domain.appointment.api;

import com.swyp3.babpool.domain.appointment.api.request.AppointmentRejectRequest;
import com.swyp3.babpool.domain.appointment.application.AppointmentService;
import com.swyp3.babpool.domain.appointment.api.request.AppointmentCreateRequest;
import com.swyp3.babpool.domain.appointment.application.response.*;
import com.swyp3.babpool.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
public class AppointmentApi {

    private final AppointmentService appointmentService;

    /**
     * 밥약 요청 API
     */
    @PostMapping("/api/appointment")
    public ApiResponse<AppointmentCreateResponse> makeAppointment(@RequestAttribute(value = "userId", required = false) Long userId,
                                                                  @RequestBody AppointmentCreateRequest appointmentCreateRequest) {
        appointmentCreateRequest.setRequesterUserId(userId);
        return ApiResponse.ok(appointmentService.makeAppointment(appointmentCreateRequest));
    }

    /**
     * 요청한 밥약 리스트 조회 API
     */
    @GetMapping("/api/appointment/list/send")
    public ApiResponse<List<AppointmentSendResponse>> getSendAppointmentList(@RequestAttribute(value = "userId", required = false) Long userId) {
        return ApiResponse.ok(appointmentService.getSendAppointmentList(userId));
    }

    /**
     * 요청받은 밥약 리스트 조회 API
     */
    @GetMapping("/api/appointment/list/receive")
    public ApiResponse<List<AppointmentReceiveResponse>> getReceiveAppointmentList(@RequestAttribute(value = "userId", required = false) Long userId) {
        return ApiResponse.ok(appointmentService.getReceiveAppointmentList(userId));
    }

    /**
     * 밥약 히스토리 - 완료(DONE) 리스트 조회 API
     */
    @GetMapping("/api/appointment/list/done")
    public ApiResponse<List<AppointmentHistoryDoneResponse>> getDoneAppointmentList(@RequestAttribute(value = "userId", required = false) Long userId) {
        return ApiResponse.ok(appointmentService.getDoneAppointmentList(userId));
    }

    /**
     * 밥약 히스토리 - 거절(EXPIRE, REJECT) 리스트 조회 API
     */
    @GetMapping("/api/appointment/list/refuse")
    public ApiResponse<List<AppointmentHistoryRefuseResponse>> getRefuseAppointmentList(@RequestAttribute(value = "userId", required = false) Long userId) {
        return ApiResponse.ok(appointmentService.getRefuseAppointmentList(userId));
    }

    /**
     * 특정 프로필 카드가 활성화 해둔, 밥약 가능한 possibleTimeId와 식별값에 따른 날짜 및 시간 조회 API
     * @param profileId
     */
    @GetMapping("/api/appointment/{profileId}/datetime")
    public ApiResponse<List<AppointmentPossibleDateTimeResponse>> getAppointmentPossibleDateTime(@PathVariable Long profileId) {
        return ApiResponse.ok(appointmentService.getAppointmentPossibleDateTime(profileId));
    }

    /**
     * 밥약 요청 거절 API
     */
    @PostMapping("/api/appointment/refuse")
    public ApiResponse<AppointmentRefuseResponse> refuseAppointment(@RequestAttribute(value = "userId", required = false) Long userId,
                                                                    @RequestBody @Valid AppointmentRejectRequest appointmentRejectRequest) {
        return ApiResponse.ok(appointmentService.refuseAppointment(appointmentRejectRequest));
    }
}
