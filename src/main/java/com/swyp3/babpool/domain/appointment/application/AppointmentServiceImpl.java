package com.swyp3.babpool.domain.appointment.application;

import com.swyp3.babpool.domain.appointment.api.request.AppointmentAcceptRequest;
import com.swyp3.babpool.domain.appointment.api.request.AppointmentCreateRequest;
import com.swyp3.babpool.domain.appointment.api.request.AppointmentRejectRequest;
import com.swyp3.babpool.domain.appointment.application.response.*;
import com.swyp3.babpool.domain.appointment.dao.AppointmentRepository;
import com.swyp3.babpool.domain.appointment.domain.Appointment;
import com.swyp3.babpool.domain.appointment.domain.AppointmentAcceptMessage;
import com.swyp3.babpool.domain.appointment.domain.AppointmentRejectMessage;
import com.swyp3.babpool.domain.appointment.domain.AppointmentRequestMessage;
import com.swyp3.babpool.domain.appointment.exception.AppointmentException;
import com.swyp3.babpool.domain.appointment.exception.errorcode.AppointmentErrorCode;
import com.swyp3.babpool.domain.profile.dao.ProfileRepository;
import com.swyp3.babpool.domain.user.application.UserService;
import com.swyp3.babpool.domain.user.application.response.MyPageResponse;
import com.swyp3.babpool.domain.user.application.response.MyPageUserDaoDto;
import com.swyp3.babpool.domain.user.dao.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AppointmentServiceImpl implements AppointmentService{

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final AppointmentRepository appointmentRepository;
    private final ProfileRepository profileRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public AppointmentCreateResponse makeAppointment(AppointmentCreateRequest appointmentCreateRequest) {
        // 프로필 카드 식별 번호로, 타겟(요청받을) 사용자 내부 식별 값 조회.
        Long targetReceiverUserId = profileRepository.findUserIdByProfileId(appointmentCreateRequest.getTargetProfileId());
        appointmentCreateRequest.setReceiverUserId(targetReceiverUserId);
        throwExceptionIfOtherAppointmentAlreadyAcceptedAtSameTime(targetReceiverUserId, appointmentCreateRequest.getPossibleTimeIdList());

        // t_appointment, t_appointment_request, t_appointment_request_time 테이블에 초기 데이터 저장.
        appointmentRepository.saveAppointment(appointmentCreateRequest);
        appointmentRepository.saveAppointmentRequest(appointmentCreateRequest);
        appointmentRepository.saveAppointmentRequestTime(appointmentCreateRequest);

        // 요청 상대에게 알림 메시지 전송.
        simpMessagingTemplate.convertAndSend("/topic/appointment/" + appointmentCreateRequest.getTargetProfileId(),
                AppointmentRequestMessage.builder()
                        .targetProfileId(appointmentCreateRequest.getTargetProfileId())
                        .message(HttpStatus.OK.name())
                        .build());
        Appointment appointmentEntity = appointmentRepository.findByAppointmentId(appointmentCreateRequest.getAppointmentId());
        return AppointmentCreateResponse.of(appointmentEntity, appointmentCreateRequest.getTargetProfileId());
    }

    /**
     * 이미 다른 사용자에 의해 예약 확정된 시간대가 존재하는지 확인.
     * possible_time_id, appointment_receiver_id 으로 조회.
     * 조회 결과 개수가 0이 아니면 이미 예약된 시간대가 존재.
     * @param targetReceiverUserId,possibleTimeIdList
     */
    private void throwExceptionIfOtherAppointmentAlreadyAcceptedAtSameTime(Long targetReceiverUserId, List<Long> possibleTimeIdList) {
        Integer countDuplicateAppointmentRequest = appointmentRepository.countAcceptedAppointmentByReceiverIdAndPossibleTimeId(targetReceiverUserId, possibleTimeIdList);
        if(countDuplicateAppointmentRequest > 0) {
            throw new AppointmentException(AppointmentErrorCode.DUPLICATE_APPOINTMENT_REQUEST, "이미 예약된 시간대가 존재합니다.");
        }
    }

    @Override
    public List<AppointmentSendResponse> getSendAppointmentList(Long userId) {
        List<AppointmentSendResponse> sendResponseList = appointmentRepository.findAppointmentListByRequesterId(userId);
        if (sendResponseList.isEmpty()) {
            throw new AppointmentException(AppointmentErrorCode.APPOINTMENT_SEND_NOT_FOUND, "발신한 밥약이 존재하지 않습니다.");
        }
        return sendResponseList;
    }

    @Override
    public List<AppointmentReceiveResponse> getReceiveAppointmentList(Long userId) {
        List<AppointmentReceiveResponse> receiveResponseList = appointmentRepository.findAppointmentListByReceiverId(userId);
        if (receiveResponseList.isEmpty()) {
            throw new AppointmentException(AppointmentErrorCode.APPOINTMENT_RECEIVE_NOT_FOUND, "수신한 밥약이 존재하지 않습니다.");
        }
        return receiveResponseList;
    }

    @Override
    public List<AppointmentHistoryDoneResponse> getDoneAppointmentList(Long userId) {
        List<AppointmentHistoryDoneResponse> historyDoneResponseList = appointmentRepository.findDoneAppointmentListByRequesterId(userId);
        if (historyDoneResponseList.isEmpty()) {
            throw new AppointmentException(AppointmentErrorCode.APPOINTMENT_DONE_NOT_FOUND, "완료된 밥약이 존재하지 않습니다.");
        }
        return historyDoneResponseList;
    }

    @Override
    public List<AppointmentHistoryRefuseResponse> getRefuseAppointmentList(Long receiverUserId) {
        List<AppointmentHistoryRefuseResponse> historyRefuseResponseList = appointmentRepository.findRefuseAppointmentListByReceiverId(receiverUserId);
        if (historyRefuseResponseList.isEmpty()) {
            throw new AppointmentException(AppointmentErrorCode.APPOINTMENT_REFUSE_NOT_FOUND, "거절된 밥약이 존재하지 않습니다.");
        }
        return historyRefuseResponseList;
    }

    @Override
    public List<AppointmentPossibleDateTimeResponse> getAppointmentPossibleDateTime(Long profileId) {
        List<AppointmentPossibleDateTimeResponse> possibleDateTimeResponseList = appointmentRepository.findAppointmentPossibleDateTimeByProfileId(profileId);
        if (possibleDateTimeResponseList.isEmpty()) {
            throw new AppointmentException(AppointmentErrorCode.APPOINTMENT_POSSIBLE_DATETIME_NOT_FOUND, "밥약 가능한 날짜 및 시간이 존재하지 않습니다.");
        }
        return possibleDateTimeResponseList;
    }

    @Override
    @Transactional
    public AppointmentRejectResponse rejectAppointment(AppointmentRejectRequest appointmentRejectRequest,Long userId) {
        Appointment appointment = appointmentRepository.findByAppointmentId(appointmentRejectRequest.getAppointmentId());

        validateReceiver(userId, appointment);
        validateAppointmentStatus(appointment);

        appointmentRepository.updateAppointmentReject(appointmentRejectRequest);
        appointmentRepository.saveRejectData(appointmentRejectRequest);

        //상대에게 거절 알림 메시지 전송
        Long requesterUserId = appointment.getAppointmentRequesterUserId();
        Long requesterProfileId = profileRepository.findByUserId(requesterUserId).getProfileId();

        simpMessagingTemplate.convertAndSend("/topic/appointment/" + requesterProfileId,
                AppointmentRejectMessage.builder()
                        .requestProfileId(requesterProfileId)
                        .rejectMessage(HttpStatus.OK.name())
                        .build());
        return new AppointmentRejectResponse("밥약 거절이 처리되었습니다.");
    }

    @Transactional
    @Override
    public AppointmentAcceptResponse acceptAppointment(AppointmentAcceptRequest appointmentAcceptRequest, Long userId) {
        Appointment appointment = appointmentRepository.findByAppointmentId(appointmentAcceptRequest.getAppointmentId());

        validateReceiver(userId, appointment);
        validateAppointmentStatus(appointment);

        appointmentRepository.updateAppointment(appointmentAcceptRequest);
        /*
        possible_time_id 삭제 관련 논의중

        //t_possible_time에서 possible_time id로부터 possible_date_id 조회
        Long possibleTimeId = appointmentAcceptRequest.getPossibleTimeId();
        Long possibleDateId = appointmentRepository.findPossibleDateIdByPossibleTimeId(possibleTimeId);

        //t_appointment_request_time에서 possible_time id에 해당하는 데이터 삭제(외래키 참조 문제)


        //t_possible_time에서 possible_time id에 해당하는 데이터 삭제
        appointmentRepository.deletePossibleTimeById(possibleTimeId);

        //t_possible_time에서 조회한 possible_date_id 외래키를 가지는 데이터가 더 있는지 확인
        //없으면 t_possible_date에서 해당 possible_date_id에 해당하는 데이터 삭제
        appointmentRepository.deletePossibleDateIfNoMorePossibleTime(possibleDateId);

         */

        //상대에게 수락 알림 전송.
        Long requesterUserId = appointment.getAppointmentRequesterUserId();
        log.info(requesterUserId.toString());
        Long requesterProfileId = profileRepository.findByUserId(requesterUserId).getProfileId();
        simpMessagingTemplate.convertAndSend("/topic/appointment/"+ requesterProfileId,
                AppointmentAcceptMessage.builder()
                        .requestProfileId(requesterProfileId)
                        .acceptMessage(HttpStatus.OK.name())
                        .build());
        return new AppointmentAcceptResponse("밥약 수락이 처리되었습니다.");
    }

    @Override
    public AppointmentDetailResponse getAppointmentDetail(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByAppointmentId(appointmentId);
        log.info("test 1");
        MyPageUserDaoDto requesterData = userRepository.findMyProfile(appointment.getAppointmentRequesterUserId());
        log.info("test 2");

        //만료까지 남은 시간 계산
        Map<String, Long> lastingTime = getLastingTime(appointment);
        log.info("test 3");
        //가능한 시간대 정보
        List<AppointmentRequesterPossibleDateTimeResponse> requesterPossibleTime = appointmentRepository.findRequesterPossibleTime(appointment);
        log.info("test 4");
        //질문 정보
        String question = appointmentRepository.findQuestion(appointment);
        log.info("test 5");

        return new AppointmentDetailResponse(requesterData,lastingTime,requesterPossibleTime,question);
    }

    private Map<String, Long> getLastingTime(Appointment appointment) {
        LocalDateTime expireDate = appointment.getAppointmentCreateDate().plusDays(1);
        Duration duration = Duration.between(LocalDateTime.now(), expireDate);

        Map<String,Long> lastingTime = new HashMap<>();
        lastingTime.put("hour", duration.toHours());
        lastingTime.put("minute", duration.toMinutes()-duration.toHours()*60);

        return lastingTime;
    }

    private void validateAppointmentStatus(Appointment appointment) {
        if(!appointmentRepository.findByAppointmentId(appointment.getAppointmentId()).getAppointmentStatus()
                .equals("WAITING")){
            throw new AppointmentException(AppointmentErrorCode.APPOINTMENT_IS_NOT_WAITING,"" +
                    "밥약 요청 상태가 WAITING이 아닙니다.");
        }
    }

    private void validateReceiver(Long userId, Appointment appointment) {
        if(appointment.getAppointmentReceiverUserId()!= userId){
            throw new AppointmentException(AppointmentErrorCode.APPOINTMENT_NOT_RECEIVER,
                    "밥약 수신자가 아니므로 거절을 할 수 없습니다.");
        }
    }


}