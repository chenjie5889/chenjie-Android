package com.example.chronic_backend;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private int code;
    private String msg;
    private String nickname;
    private Long userId;
}