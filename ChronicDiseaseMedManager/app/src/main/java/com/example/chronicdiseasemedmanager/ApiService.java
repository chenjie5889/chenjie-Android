package com.example.chronicdiseasemedmanager;

import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public interface ApiService {

    @FormUrlEncoded
    @POST("api/login")
    Call<LoginResponse> login(@Field("phone") String phone, @Field("password") String password);

    @FormUrlEncoded
    @POST("api/register")
    Call<SmsResponse> register(@Field("phone") String phone, @Field("code") String code, @Field("password") String password);

    @FormUrlEncoded
    @POST("api/bindIdentity")
    Call<SmsResponse> bindIdentity(@Field("phone") String phone, @Field("realName") String realName, @Field("idCard") String idCard);

    @FormUrlEncoded
    @POST("api/sendSms")
    Call<SmsResponse> sendSms(@Field("phone") String phone);

    @GET("api/getMedLogs")
    Call<List<MedicationLog>> getMedLogs(@Query("userId") Long userId);

    // 获取档案
    @GET("api/getArchive")
    Call<ArchivePlusResponse> getArchive(@Query("userId") Long userId);

    @POST("api/updateArchive")
    Call<SmsResponse> updateArchive(@Body Archive archive);

    // --- 疾病管理接口 ---
    @GET("api/med/diseases")
    Call<List<Disease>> getDiseases(@Query("userId") Long userId);

    @POST("api/med/disease/add")
    Call<SmsResponse> addDisease(@Body Disease disease);

    @POST("api/med/disease/update")
    Call<SmsResponse> updateDisease(@Body Disease disease);

    @DELETE("api/med/disease/delete")
    Call<SmsResponse> deleteDisease(@Query("id") Long id);

    // --- 用药管理接口 ---
    @GET("api/med/medications")
    Call<List<Medication>> getMedications(@Query("userId") Long userId);

    @GET("api/med/medications/byDisease")
    Call<List<Medication>> getMedicationsByDisease(@Query("userId") Long userId, @Query("diseaseId") Long diseaseId);

    @POST("api/med/medication/add")
    Call<SmsResponse> addMedication(@Body Medication medication);

    @POST("api/med/medication/update")
    Call<SmsResponse> updateMedication(@Body Medication medication);

    @DELETE("api/med/medication/delete")
    Call<SmsResponse> deleteMedication(@Query("id") Long id);

    // --- 统计接口 ---
    @GET("api/med/stats")
    Call<MedStats> getMedStats(@Query("userId") Long userId);

    // 添加家属管理接口
    @FormUrlEncoded
    @POST("api/family/searchUser")
    Call<SmsResponse> searchUser(@Field("phone") String phone);

    @POST("api/family/request")
    Call<SmsResponse> sendFamilyRequest(@Body FamilyRequest request);

    @GET("api/family/pendingRequests")
    Call<List<FamilyRequestResponse>> getPendingRequests(@Query("userId") Long userId);

    @POST("api/family/handleRequest")
    Call<SmsResponse> handleFamilyRequest(@Body HandleRequest request);

    @GET("api/family/approvedFamily")
    Call<List<FamilyMemberResponse>> getApprovedFamily(@Query("userId") Long userId);

    @GET("api/family/familyData")
    Call<FamilyDataResponse> getFamilyData(@Query("userId") Long userId, @Query("familyId") Long familyId);

    @DELETE("api/family/removeFamily")
    Call<SmsResponse> removeFamily(@Query("userId") Long userId, @Query("familyId") Long familyId);

    @GET("api/getUserInfo")
    Call<UserInfoResponse> getUserInfo(@Query("userId") Long userId);

    @POST("api/updateUserInfo")
    Call<SmsResponse> updateUserInfo(@Body UpdateUserInfoRequest request);

    @FormUrlEncoded
    @POST("api/changePassword")
    Call<SmsResponse> changePassword(
            @Field("userId") Long userId,
            @Field("oldPassword") String oldPassword,
            @Field("newPassword") String newPassword
    );

    @FormUrlEncoded
    @POST("api/changePhone")
    Call<SmsResponse> changePhone(
            @Field("userId") Long userId,
            @Field("oldPhone") String oldPhone,
            @Field("newPhone") String newPhone,
            @Field("code") String code
    );

    @FormUrlEncoded
    @POST("api/sendChangePhoneCode")
    Call<SmsResponse> sendChangePhoneCode(@Field("phone") String phone);
}


// --- 数据模型类 ---
class ArchivePlusResponse {
    public Archive archive;
    public String realName;
    public boolean hasData;  // 是否有数据

    public ArchivePlusResponse() {}

    public ArchivePlusResponse(Archive archive, String realName, boolean hasData) {
        this.archive = archive;
        this.realName = realName;
        this.hasData = hasData;
    }

    public boolean hasArchiveData() {
        return hasData && archive != null;
    }
}

class Archive {
    public Long id;
    public Long userId;
    public String gender;
    public String birthday;
    public Double height;
    public Double weight;
    public String medicalHistory;

    public Archive() {}
}

class MedicationLog {
    public String logDate;
    public Integer status;

    public MedicationLog() {}
}

class SmsResponse {
    public int code;
    public String msg;
    public String data;

    public SmsResponse() {}
}

class LoginResponse {
    public int code;
    public String msg;
    public String nickname;
    public Long userId;

    public LoginResponse() {}
}

class Disease {
    public Long id;
    public Long userId;
    public String diseaseName;
    public String diseaseType;
    public String diagnosisInfo;
    public String symptoms;
    public String diagnosisDate;  // String类型
    public String hospital;
    public String doctor;
    public String createTime;
    public String updateTime;

    public Disease() {}
}

class Medication {
    public Long id;
    public Long userId;
    public Long diseaseId;
    public String medicineName;
    public String genericName;
    public String dosage;
    public String frequency;
    public String takeTimeMorning;    // String类型
    public String takeTimeNoon;       // String类型
    public String takeTimeEvening;    // String类型
    public String takeTimeNight;      // String类型
    public String instructions;
    public String precautions;
    public String sideEffects;
    public String contraindications;
    public String mechanism;
    public String storage;
    public String startDate;          // String类型
    public String endDate;            // String类型
    public Boolean isActive = true;
    public String createTime;
    public String updateTime;

    // 获取关联的疾病名称（在UI层通过查询获取）
    public transient String diseaseName;

    public Medication() {}

    // 获取服药时间描述
    public String getTakeTimeDescription() {
        StringBuilder sb = new StringBuilder();
        if (takeTimeMorning != null && !takeTimeMorning.isEmpty()) {
            sb.append("早上 ").append(takeTimeMorning);
        }
        if (takeTimeNoon != null && !takeTimeNoon.isEmpty()) {
            if (sb.length() > 0) sb.append("，");
            sb.append("中午 ").append(takeTimeNoon);
        }
        if (takeTimeEvening != null && !takeTimeEvening.isEmpty()) {
            if (sb.length() > 0) sb.append("，");
            sb.append("晚上 ").append(takeTimeEvening);
        }
        if (takeTimeNight != null && !takeTimeNight.isEmpty()) {
            if (sb.length() > 0) sb.append("，");
            sb.append("睡前 ").append(takeTimeNight);
        }
        return sb.length() > 0 ? sb.toString() : "未设置服药时间";
    }
}

class MedStats {
    public long totalDiseases;
    public long totalMedications;
    public long activeMedications;

    public MedStats() {}
}

// 新增数据模型类（添加到ApiService.java中）
class FamilyRequest {
    public Long userId;
    public String familyPhone;
    public String relationship;
    public List<String> permissions;

    public FamilyRequest() {}
}

class HandleRequest {
    public Long userId;
    public Long relationshipId;
    public boolean agree;

    public HandleRequest() {}
}

class FamilyRequestResponse {
    public Long id;
    public Long requesterId;
    public String requesterName;
    public String requesterPhone;
    public String relationship;
    public String requestTime;
    public List<String> requestedPermissions;

    public FamilyRequestResponse() {}
}

class FamilyMemberResponse {
    public Long relationshipId;
    public Long familyId;
    public String familyName;
    public String familyPhone;
    public String relationship;
    public Boolean isRequester;
    public Map<String, Boolean> permissions;

    public FamilyMemberResponse() {}
}

class FamilyDataResponse {
    public int code;
    public String msg;
    public Archive archive;
    public List<Disease> diseases;
    public List<Medication> medications;

    public FamilyDataResponse() {}
}

class UpdateUserInfoRequest {
    public Long userId;
    public String nickname;
    public String realName;
    public String idCard;

    public UpdateUserInfoRequest() {}

    // 构造函数
    public UpdateUserInfoRequest(Long userId, String nickname, String realName, String idCard) {
        this.userId = userId;
        this.nickname = nickname;
        this.realName = realName;
        this.idCard = idCard;
    }
}

class UserInfoResponse {
    public Long id;
    public String phone;
    public String nickname;
    public String realName;
    public String idCard;

    public UserInfoResponse() {}
}