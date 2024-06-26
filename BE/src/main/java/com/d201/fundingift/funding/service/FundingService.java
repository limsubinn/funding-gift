package com.d201.fundingift.funding.service;

import com.d201.fundingift._common.exception.CustomException;
import com.d201.fundingift._common.response.ErrorType;
import com.d201.fundingift._common.response.SliceList;
import com.d201.fundingift._common.util.FcmNotificationProvider;
import com.d201.fundingift._common.util.SecurityUtil;
import com.d201.fundingift.attendance.repository.AttendanceRepository;
import com.d201.fundingift.consumer.entity.Consumer;
import com.d201.fundingift.consumer.repository.ConsumerRepository;
import com.d201.fundingift.friend.entity.Friend;
import com.d201.fundingift.friend.repository.FriendRepository;
import com.d201.fundingift.funding.dto.request.DeleteFundingRequest;
import com.d201.fundingift.funding.dto.request.PostFundingRequest;
import com.d201.fundingift.funding.dto.response.GetFundingCalendarResponse;
import com.d201.fundingift.funding.dto.response.GetFundingDetailResponse;
import com.d201.fundingift.funding.dto.response.GetFundingResponse;
import com.d201.fundingift.funding.entity.AnniversaryCategory;
import com.d201.fundingift.funding.entity.Funding;
import com.d201.fundingift.funding.entity.status.FundingStatus;
import com.d201.fundingift.funding.repository.AnniversaryCategoryRepository;
import com.d201.fundingift.funding.repository.FundingRepository;
import com.d201.fundingift._common.dto.FcmNotificationDto;
import com.d201.fundingift.product.entity.Product;
import com.d201.fundingift.product.entity.ProductOption;
import com.d201.fundingift.product.repository.ProductOptionRepository;
import com.d201.fundingift.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FundingService {

    private final FundingRepository fundingRepository;
    private final AttendanceRepository attendanceRepository;
    private final ConsumerRepository consumerRepository;
    private final FriendRepository friendRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final AnniversaryCategoryRepository anniversaryCategoryRepository;
    private final SecurityUtil securityUtil;
    private final FcmNotificationProvider fcmNotificationProvider;

    @Transactional
    public void postFunding(PostFundingRequest postFundingRequest) {
        Consumer consumer = getConsumer();

        //상품 없으면 예외
        Product product = getProduct(postFundingRequest);

        //상품 옵션 없으면 예외
        ProductOption productOption = getProductOption(postFundingRequest);

        //제품과 제품 옵션이 맞는지 확인
        checkingProductAndProductOptionIsSame(product, productOption);

        //기념일 카테고리 없으면 예외
        AnniversaryCategory anniversaryCategory = getAnniversaryCategory(postFundingRequest);

        //시작일이 현재 날짜보다 과거면 예외
        isStartDatePast(postFundingRequest.getStartDate());

        // 기념일이 시작일보다 과거면 예외
        isAnniversaryDatePast(postFundingRequest.getAnniversaryDate(), postFundingRequest.getStartDate());

        // 종료일이 기념일 보다 과거이면 예외
        isEndDatePast(postFundingRequest.getEndDate(), postFundingRequest.getAnniversaryDate());

        //시작일 종료일 7일 넘으면 예외
        isOver7Days(postFundingRequest.getStartDate(), postFundingRequest.getEndDate());

        //시작일이 오늘이면 IN_PROGRESS로 상태 변경, 미래면 PRE_PROGRESS
        fundingRepository.save(Funding.from(postFundingRequest, IsStartDateToday(postFundingRequest.getStartDate()), consumer, anniversaryCategory, product, productOption));

        // 알림
        fcmNotificationProvider.sendToMany(
                getConsumersByToConsumerIdAndFavorite(consumer.getId()),
                FcmNotificationDto.of("펀딩 등록 알림", consumer.getName() + "님이 펀딩을 등록했어요!")
        );
    }

    @Transactional
    public void deleteFunding(DeleteFundingRequest deleteFundingRequest) {
        Long myConsumerId = securityUtil.getConsumerId();

        //펀딩 존재 확인
        Funding funding = getFunding(deleteFundingRequest.getFundingId());

        //내 펀딩이 맞는지 확인
        if(!Objects.equals(myConsumerId, funding.getConsumer().getId()))
            throw new CustomException(ErrorType.USER_UNAUTHORIZED);

        //삭제 가능한 상태인지 확인 - 펀딩 시작전일 경우만 삭제 가능(PRE_PROGRESS인 경우)
        if(!"PRE_PROGRESS".equals(String.valueOf(funding.getFundingStatus())))
            throw new CustomException(ErrorType.FUNDING_STATUS_NOT_DELETED);

        fundingRepository.delete(funding);
    }

    //내 펀딩 목록 보기
    public SliceList<GetFundingResponse> getMyFundings(String keyword, Pageable pageable) {
        Long myConsumerId = securityUtil.getConsumerId();

        //제품명으로 검색 안하는 경우
        if (keyword == null)
            return getFundingsSliceList(findAllByConsumerId(myConsumerId, pageable));

        //제품명으로 검색하는 경우
        return getFundingsSliceList(findAllByConsumerIdAndProductName(myConsumerId, keyword, pageable));
    }

    //내가 참여한 펀딩 목록 조회
    public SliceList<GetFundingResponse> getMyAttendanceFundings(Pageable pageable) {
        Long myConsumerId = securityUtil.getConsumerId();

        return getFundingsSliceList(findAllByConsumerRightJoinAttendance(myConsumerId, pageable));
    }

    //친구 펀딩 목록 보기
    public SliceList<GetFundingResponse> getFriendFundings(Long friendConsumerId, String keyword, Pageable pageable) {
        Long myConsumerId = securityUtil.getConsumerId();

        //친구 아이디 존재 여부 확인
        findByConsumerId(friendConsumerId);

        //보려는 펀딩 목록의 대상이 자신의 친구인지 확인
        checkingFriend(myConsumerId, friendConsumerId);

        //보려는 펀딩 목록의 대상에 자신이 친한 친구인지 확인
        if(checkingIsFavoriteFriend(friendConsumerId, myConsumerId)) {
            //제품명으로 검색 안하는 경우
            if(keyword == null)
                return getFundingsSliceList(findAllByConsumerId(friendConsumerId, pageable));

            return getFundingsSliceList(findAllByConsumerIdAndProductName(friendConsumerId, keyword, pageable));
        } else {
            //제품명으로 검색 안하는 경우
            if(keyword == null)
                return getFundingsSliceList(findAllByConsumerIdAndIsPrivate(friendConsumerId, pageable));

            return getFundingsSliceList(findAllByConsumerIdAndIsPrivateAndProductName(friendConsumerId, keyword, pageable));
        }
    }

    public SliceList<GetFundingResponse> getFundingFeeds(Pageable pageable) {
        Long myConsumerId = securityUtil.getConsumerId();

        //친구 리스트 조회
        List<Friend> friends = friendRepository.findByConsumerId(myConsumerId);

        return getFundingsFeedSliceList(findAllByConsumerIdsAndFundingStatus(friends, pageable), friends);
    }

    public List<GetFundingResponse> getFundingsStory(Long consumerId) {
        Long myConsumerId = securityUtil.getConsumerId();

        if(!Objects.equals(myConsumerId, consumerId)) {
            //친구 아이디 존재 여부 확인
            findByConsumerId(consumerId);

            //보려는 펀딩 목록의 대상이 자신의 친구인지 확인
            checkingFriend(myConsumerId, consumerId);
        }

        //보려는 펀딩 목록의 대상에 자신이 친한 친구인지 확인
        if(Objects.equals(myConsumerId, consumerId) || checkingIsFavoriteFriend(consumerId, myConsumerId)) {
            return getFundingsList(findByConsumerIdAndFundingStatusOrderedByStartDate(consumerId));
        } else {
            return getFundingsList(findByConsumerIdAndFundingStatusAndIsPrivateOrderByStartDateAsc(consumerId));
        }
    }

    //펀딩 상세 조회
    public GetFundingDetailResponse getFundingDetailResponse(Long fundingId) {
        Long myConsumerId = securityUtil.getConsumerId();

        Funding funding = getFunding(fundingId);
        Long fundingConsumerId = funding.getConsumer().getId();

        //내 펀딩인지 확인
        if(!Objects.equals(myConsumerId, fundingConsumerId)) {

            //보려는 펀딩 목록의 대상이 자신의 친구인지 확인
            checkingFriend(myConsumerId, fundingConsumerId);

            //글 허용범위가 펀딩 생성자의 친한 친구 인지 확인
            if(funding.getIsPrivate()) {
                checkingIsFavoriteFriendOrElseThrow(fundingConsumerId, myConsumerId);
            }
        }

        return GetFundingDetailResponse.from(funding);
    }

    public List<GetFundingCalendarResponse> getFundingCalendarsResponse(Integer year, Integer month) {
        List<GetFundingCalendarResponse> fundingList = new ArrayList<>();
        Long myConsumerId = securityUtil.getConsumerId();

        //친구 리스트 조회
        List<Friend> friends = friendRepository.findByConsumerId(myConsumerId);

        for(Friend f : friends) {

            //친구가 날 친한 친구로 설정 했는지 확인
            if(checkingIsFavoriteFriend(f.getToConsumerId(), myConsumerId)) {
                //친한 친구로 설정한 경우 isPrivate 상관 없이 모두 조회
                fundingList.addAll(
                        fundingRepository
                                .findAllByConsumerIdAndDeletedAtIsNull(f.getToConsumerId(), year, month)
                                .stream()
                                .map(GetFundingCalendarResponse::from)
                                .toList()
                );
                continue;
            }

            //친한 친구가 아닌경우 IsPrivate == false만 조회
            fundingList.addAll(
                    fundingRepository
                            .findAllByConsumerIdAndIsPrivateAndDeletedAtIsNull(f.getToConsumerId(), year, month)
                            .stream()
                            .map(GetFundingCalendarResponse::from)
                            .toList()
            );
        }

        return fundingList;
    }

    /**
     * 내부 메서드
     */
    private Funding getFunding(Long fundingId) {
        return fundingRepository.findByIdAndDeletedAtIsNull(fundingId)
                .orElseThrow(() -> new CustomException(ErrorType.FUNDING_NOT_FOUND));
    }

    //제품과 제품 옵션이 맞는지 확인
    private void checkingProductAndProductOptionIsSame(Product product, ProductOption productOption) {

        for(ProductOption po : product.getProductOptions()) {
            if(Objects.equals(po.getId(), productOption.getId()))
                return;
        }

        throw new CustomException(ErrorType.PRODUCT_OPTION_MISMATCH);
    }

    private List<GetFundingResponse> getFundingsList(List<Funding> fundings) {
        return fundings.stream().map(GetFundingResponse::from).collect(Collectors.toList());
    }

    private List<Funding> findByConsumerIdAndFundingStatusOrderedByStartDate(Long consumerId) {
        return fundingRepository.findAllByConsumerIdAndFundingStatusOrderByStartDateAsc(consumerId, FundingStatus.IN_PROGRESS);
    }

    private List<Funding> findByConsumerIdAndFundingStatusAndIsPrivateOrderByStartDateAsc(Long consumerId) {
        return fundingRepository.findAllByConsumerIdAndFundingStatusAndIsPrivateOrderByStartDateAsc(consumerId, FundingStatus.IN_PROGRESS, false);
    }

    //slice<Funding> -> SliceList<GetFundingResponse> 변경 매서드
    private SliceList<GetFundingResponse> getFundingsSliceList(Slice<Funding> fundings) {
        return SliceList.from(fundings.stream().map(GetFundingResponse::from).collect(Collectors.toList()), fundings.getPageable(), fundings.hasNext());
    }

    //slice<Funding> -> SliceList<GetFundingResponse> 변경 매서드
    private SliceList<GetFundingResponse> getFundingsFeedSliceList(Slice<Funding> fundings, List<Friend> friends) {
        Map<Long, Friend> toFriends = new HashMap<>();

        for(Friend f : friends) {
            Optional<Friend> toFriend =friendRepository.findById(f.getToConsumerId() + ":" + f.getConsumerId());
            toFriend.ifPresent(friend -> toFriends.put(f.getToConsumerId(), friend));
        }

        List<Funding> changed = new ArrayList<>();
        for(Funding f : fundings) {
            if(!f.getIsPrivate()) {
                changed.add(f);
                continue;
            }

            if(toFriends.containsKey(f.getConsumer().getId()) && toFriends.get(f.getConsumer().getId()).getIsFavorite())
                changed.add(f);
        }

        return SliceList.from(changed.stream().map(GetFundingResponse::from).collect(Collectors.toList()), fundings.getPageable(), fundings.hasNext());
    }

    //consumerId로 펀딩 목록 찾기
    private Slice<Funding> findAllByConsumerId(Long consumerId, Pageable pageable) {
        return fundingRepository.findAllByConsumerIdAndDeletedAtIsNull(consumerId, pageable);
    }

    private Slice<Funding> findAllByConsumerRightJoinAttendance(Long consumerId, Pageable pageable) {
        return attendanceRepository.findAllByConsumerIdAndAndDeletedAtIsNull(consumerId, pageable);
    }

    //consumerId, isPrivate == false로 펀딩 목록 찾기
    private Slice<Funding> findAllByConsumerIdAndIsPrivate(Long consumerId, Pageable pageable) {
        return fundingRepository.findAllByConsumerIdAndIsPrivateAndDeletedAtIsNull(consumerId, pageable);
    }

    //consumerId, 검색어로 펀딩 목록 찾기
    private Slice<Funding> findAllByConsumerIdAndProductName(Long consumerId, String keyword, Pageable pageable) {
        return fundingRepository.findAllByConsumerIdAndProductNameAndDeletedAtIsNull(consumerId, keyword, pageable);
    }

    //consumerId, isPrivate == false, 검색어로 펀딩 목록 찾기
    private Slice<Funding> findAllByConsumerIdAndIsPrivateAndProductName(Long consumerId, String keyword, Pageable pageable) {
        return fundingRepository.findAllByConsumerIdAndIsPrivateAndProductNameAndDeletedAtIsNull(consumerId, keyword, pageable);
    }

    private Slice<Funding> findAllByConsumerIdsAndFundingStatus(List<Friend> friends, Pageable pageable) {
        List<Long> friendIds = friends.stream()
                .map(Friend::getToConsumerId).toList();

        return fundingRepository.findAllByConsumerIdsAndFundingStatusAndDeletedAtIsNull(friendIds, pageable);
    }

    private void findByConsumerId(Long consumerId){
        consumerRepository.findByIdAndDeletedAtIsNull(consumerId)
                .orElseThrow(() -> new CustomException(ErrorType.USER_NOT_FOUND));
    }

    private void checkingFriend(Long consumerId, Long toConsumerId) {
        friendRepository.findById(consumerId + ":" + toConsumerId)
                .orElseThrow(() -> new CustomException(ErrorType.FRIEND_NOT_FOUND));
    }

    private boolean checkingIsFavoriteFriend(Long toConsumerId, Long consumerId) {
        Optional<Friend> friend = friendRepository.findById(toConsumerId + ":" + consumerId);

        //보려는 펀딩 목록의 대상에 본인이 친구가 아니거나 친한 친구가 아닌 경우 -> false
        return friend.isPresent() && friend.get().getIsFavorite();
    }

    private void checkingIsFavoriteFriendOrElseThrow(Long toConsumerId, Long consumerId) {
        friendRepository.findById(toConsumerId + ":" + consumerId)
                .orElseThrow(() -> new CustomException(ErrorType.FRIEND_NOT_IS_FAVORITE));
    }

    private AnniversaryCategory getAnniversaryCategory(PostFundingRequest postFundingRequest) {
        return anniversaryCategoryRepository.findById(postFundingRequest.getAnniversaryCategoryId())
                .orElseThrow(() -> new CustomException(ErrorType.ANNIVERSARY_CATEGORY_NOT_FOUND));
    }

    private ProductOption getProductOption(PostFundingRequest postFundingRequest) {
        return productOptionRepository.findByIdAndStatusIsActive(postFundingRequest.getProductOptionId())
                .orElseThrow(() -> new CustomException(ErrorType.PRODUCT_OPTION_NOT_FOUND));
    }

    private Product getProduct(PostFundingRequest postFundingRequest) {
        return productRepository.findById(postFundingRequest.getProductId())
                .orElseThrow(() -> new CustomException(ErrorType.PRODUCT_NOT_FOUND));
    }

    private Consumer getConsumer() {
        return securityUtil.getConsumer();
    }

    public void isOver7Days(LocalDate start, LocalDate end) {
        if (Math.abs(start.until(end).getDays()) > 7)
            throw new CustomException(ErrorType.FUNDING_DURATION_NOT_VALID);
    }

    public void isStartDatePast(LocalDate startDate) {
        if(startDate.isBefore(LocalDate.now()))
            throw new CustomException(ErrorType.FUNDING_START_DATE_IS_PAST);
    }


    public void isAnniversaryDatePast(LocalDate anniversaryDate, LocalDate startDate) {
        if(anniversaryDate.isBefore(startDate))
            throw new CustomException(ErrorType.FUNDING_ANNIVERSARY_DATE_IS_PAST);
    }

    public void isEndDatePast(LocalDate endDate, LocalDate anniversaryDate) {
        if(endDate.isBefore(anniversaryDate))
            throw new CustomException(ErrorType.FUNDING_END_DATE_IS_PAST);
    }

    public String IsStartDateToday(LocalDate startDate) {
        if(startDate.equals(LocalDate.now()))
            return "IN_PROGRESS";
        return "PRE_PROGRESS";
    }

    private List<Consumer> getConsumersByToConsumerIdAndFavorite(Long toConsumerId) {
        List<Long> consumerIds =  friendRepository.findAllByToConsumerIdAndIsFavorite(toConsumerId, true)
                .stream().map(Friend::getConsumerId).toList();
        return consumerIds.stream()
                .map(id -> consumerRepository.findByIdAndDeletedAtIsNull(id).orElse(null))
                .collect(Collectors.toList());
    }

}
