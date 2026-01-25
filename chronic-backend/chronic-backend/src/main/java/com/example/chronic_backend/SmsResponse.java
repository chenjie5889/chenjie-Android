package com.example.chronic_backend;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmsResponse {
    private int code;      // 200 表示成功
    private String msg;    // 提示信息
    private String data;   // 模拟返回验证码
}