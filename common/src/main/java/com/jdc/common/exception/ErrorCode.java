package com.jdc.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력입니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다"),

    // ChatRoom
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CR001", "채팅방을 찾을 수 없습니다"),
    ALREADY_JOINED(HttpStatus.CONFLICT, "CR002", "이미 참여한 채팅방입니다"),
    NOT_A_MEMBER(HttpStatus.FORBIDDEN, "CR003", "채팅방 멤버가 아닙니다"),

    // Message
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "메시지를 찾을 수 없습니다"),
    MESSAGE_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "M002", "본인이 보낸 메시지만 삭제할 수 있습니다"),
    MESSAGE_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "M003", "이미 삭제된 메시지입니다"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
