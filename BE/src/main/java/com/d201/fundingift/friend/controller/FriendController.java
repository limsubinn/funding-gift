package com.d201.fundingift.friend.controller;

import com.d201.fundingift._common.response.ErrorResponse;
import com.d201.fundingift._common.response.ResponseUtils;
import com.d201.fundingift._common.response.SuccessResponse;
import com.d201.fundingift._common.response.SuccessType;
import com.d201.fundingift.friend.dto.FriendDto;
import com.d201.fundingift.friend.dto.response.GetFriendStoryResponse;
import com.d201.fundingift.friend.dto.response.GetKakaoFriendsResponse;
import com.d201.fundingift.friend.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Tag(name = "friends", description = "친구 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class FriendController {
    private final FriendService friendService;

    @Operation(summary = "내 카카오 친구목록 불러오기",
            description = "내 카카오 계정의 친구정보를 조회합니다. `Token`"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "성공",
                    useReturnTypeSchema = true)
    })
    @GetMapping("/kakao")
    public SuccessResponse<GetKakaoFriendsResponse> getKakaoFriendByAuthentication() {
        GetKakaoFriendsResponse friendsResponse = friendService.getKakaoFriendByController();
        return ResponseUtils.ok(friendsResponse, SuccessType.GET_KAKAO_FRIEND_INFO_SUCCESS);
    }

    @Operation(summary = "내 친구 목록 조회 (소비자)",
            description = """
    내 친구들의 친구정보를 조회합니다. `Token`"
    친한친구 -> 가나다 순 정렬으로 반환합니다.
    """
    )
    @GetMapping
    public SuccessResponse<List<FriendDto>> getFriends() {
        List<FriendDto> friendDtos = friendService.getFriends();
        return ResponseUtils.ok(friendDtos, SuccessType.GET_FRIEND_INFO_SUCCESS);
    }
    @Operation(summary = "내 친구 모두 삭제",
            description = """
    나와 연결된 모든 친구를 친구목록에서 삭제합니다. `Token`"
    상대방의 친구목록에서도 삭제됩니다.
    """
    )
    @DeleteMapping("/{consumer-id}")
    public SuccessResponse<Void> deleteAllFriendsByConsumerId(@PathVariable("consumer-id") Long consumerId) {
            friendService.deleteAllFriendsByConsumerId(consumerId);
            return ResponseUtils.ok(SuccessType.DELETE_FRIEND_RELATIONSHIP_SUCCESS);
    }

    @Operation(summary = "친한친구 설정 변경",
            description = "해당 친구와의 친한친구 관계를 토글합니다. `Token`"
    )
    @PutMapping("/{to-consumer-id}/toggle-favorite")
    public SuccessResponse<?> toggleFavorite(@PathVariable("to-consumer-id") Long toConsumerId) {
        friendService.toggleFavorite(toConsumerId);
        return ResponseUtils.ok(SuccessType.PUT_FAVORITE_TOGGLE_SUCCESS);
    }

    @Operation(summary = "친구 펀딩 스토리 리스트",
            description = """
                           `token` \n
                           자신의 친구 펀딩 스토리 리스트를 줍니다. \n
                           진행중인 펀딩만 보여줍니다. \n
                           시작일 기준 오름차순으로 정렬 해서 줍니다. \n
                           """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",
                    description = "성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400",
                    description = "로그인 여부",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    ))
    })
    @GetMapping("/fundings-story")
    public SuccessResponse<List<GetFriendStoryResponse>> getFundingsStory() {
        return ResponseUtils.ok(friendService.getFriendsStory(), SuccessType.GET_FRIENDS_STORY_SUCCESS);
    }
}
