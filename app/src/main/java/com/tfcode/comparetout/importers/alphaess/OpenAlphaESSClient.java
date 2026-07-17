/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.importers.alphaess;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.tfcode.comparetout.importers.alphaess.responses.BindSnResponse;
import com.tfcode.comparetout.importers.alphaess.responses.ErrorResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenAlphaESSClient {

    private static final String TAG = "AlphaESSImporter";
    private static final String BASE_URL = "https://openapi.alphaess.com/";
    private String mSystemSerialNumber = "";
    private final String mApplicationID;
    private final String mApplicationSecret;

    private final OpenAlphaESSService mApiService;

    public OpenAlphaESSClient(String applicationID, String applicationSecret) {
        this(applicationID, applicationSecret, BASE_URL);
    }

    /** Package-private: unit tests point the client at a MockWebServer. */
    OpenAlphaESSClient(String applicationID, String applicationSecret, String baseUrl) {
        mApplicationID = applicationID;
        mApplicationSecret = applicationSecret;
        // Bound hangs: the OkHttp defaults are 10s, but under load the
        // AlphaESS OpenAPI sometimes stalls. 30s is generous for the modest
        // JSON bodies these endpoints return.
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        Retrofit mRetrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        mApiService = mRetrofit.create(OpenAlphaESSService.class);
    }

    public void setSerial(String serialNumber) {
        mSystemSerialNumber = serialNumber;
    }

    public GetEssListResponse getEssList() throws AlphaESSException {
        GetEssListResponse ret = null;
        Call<ResponseBody> call = mApiService.getESSList(
                getHeaders());
        Response<ResponseBody> response = null;
        String responseBody = "";
        try {
            response = call.execute();
            try (ResponseBody body = response.body()) {
                if (body != null) responseBody = body.string();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!(null == response) && response.isSuccessful()) {
            Gson gson = new Gson();
            try {
                ret = gson.fromJson(responseBody, GetEssListResponse.class);
            }
            catch (IllegalStateException ise) {
                Log.w(TAG, "Expecting GetEssListResponse, but not one :-(");
            }
            if ((null == ret) || (null == ret.data)) {
                ErrorResponse err = gson.fromJson(responseBody, ErrorResponse.class);
                Log.w(TAG, "getEssList error code=" + err.code);
                throwAppropriateException(err);
            }
        } else if (!(null == response)) {
            ErrorResponse errorResponse= new ErrorResponse();
            errorResponse.code = response.code();
            errorResponse.msg = response.message();
            throwAppropriateException(errorResponse);
        }
        return ret;
    }

    public GetOneDayPowerResponse getOneDayPowerBySn(String queryDate) throws AlphaESSException {
        GetOneDayPowerResponse ret = null;
        Call<ResponseBody> call = mApiService.getOneDayPowerBySn(
                getHeaders(),
                mSystemSerialNumber,
                queryDate);
        Response<ResponseBody> response = null;
        String responseBody = "";
        try {
            response = call.execute();
            try (ResponseBody body = response.body()) {
                if (body != null) responseBody = body.string();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!(null == response) && response.isSuccessful()) {
            Gson gson = new Gson();
            try {
                ret = gson.fromJson(responseBody, GetOneDayPowerResponse.class);
            }
            catch (IllegalStateException ise) {
                Log.w(TAG, "Expecting GetOneDayPowerResponse, but not one :-(");
            }
            if ((null == ret) || (null == ret.data)) {
                ErrorResponse err = gson.fromJson(responseBody, ErrorResponse.class);
                Log.w(TAG, "getOneDayPowerBySn error code=" + err.code);
                throwAppropriateException(err);
            }
        } else if (!(null == response)) {
            ErrorResponse errorResponse= new ErrorResponse();
            errorResponse.code = response.code();
            errorResponse.msg = response.message();
            throwAppropriateException(errorResponse);
        }

        return ret;
    }

    public GetOneDayEnergyResponse getOneDayEnergyBySn(String queryDate) throws AlphaESSException {
        GetOneDayEnergyResponse ret = null;
        Call<ResponseBody> call = mApiService.getOneDateEnergyBySn(
                getHeaders(),
                mSystemSerialNumber,
                queryDate);
        Response<ResponseBody> response = null;
        String responseBody = "";
        try {
            response = call.execute();
            try (ResponseBody body = response.body()) {
                if (body != null) responseBody = body.string();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!(null == response) && response.isSuccessful()) {
            Gson gson = new Gson();
            try {
                ret = gson.fromJson(responseBody, GetOneDayEnergyResponse.class);
            }
            catch (IllegalStateException ise) {
                Log.w(TAG, "Expecting GetOneDayEnergyResponse, but not one :-(");
            }
            if ((null == ret) || (null == ret.data)) {
                ErrorResponse err = gson.fromJson(responseBody, ErrorResponse.class);
                Log.w(TAG, "getOneDayEnergyBySn error code=" + err.code);
                throwAppropriateException(err);
            }
        } else  if (!(null == response)){
            ErrorResponse errorResponse= new ErrorResponse();
            errorResponse.code = response.code();
            errorResponse.msg = response.message();
            throwAppropriateException(errorResponse);
        }

        return ret;
    }

    /**
     * Step 1 of the bind-SN flow (plans/source/alpha.md §1): validates
     * SN + CheckCode and issues a verification code — returned in-band in
     * the envelope's data ({@link BindSnResponse#inBandCode()}) and/or
     * emailed to the developer-account address.
     *
     * <p>Unlike the data GETs, the full envelope is returned INCLUDING
     * non-200 codes: the add-inverter UI branches on 6003 (already bound →
     * success), 6004 (check-code error) and 6053 (too fast), so mapping
     * them to exceptions here would just be unwrapped again.
     * {@link AlphaESSException} is reserved for transport/HTTP failures.
     */
    public BindSnResponse getVerificationCode(String sysSn, String checkCode)
            throws AlphaESSException {
        return executeBindFlowCall(
                mApiService.getVerificationCode(getHeaders(), sysSn, checkCode),
                "getVerificationCode");
    }

    /**
     * Step 2 of the bind-SN flow: binds the SN to this appId with the
     * verification code from {@link #getVerificationCode}. Same envelope
     * contract as that method — codes come back, not exceptions. POST with
     * a JSON body (the Python client's form) — the live server 405s the
     * Postman collection's GET here, the mirror image of getVerificationCode.
     */
    public BindSnResponse bindSn(String sysSn, String code) throws AlphaESSException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("sysSn", sysSn);
        body.put("code", code);
        return executeBindFlowCall(mApiService.bindSn(getHeaders(), body), "bindSn");
    }

    private BindSnResponse executeBindFlowCall(Call<ResponseBody> call, String what)
            throws AlphaESSException {
        Response<ResponseBody> response = null;
        String responseBody = "";
        try {
            response = call.execute();
            try (ResponseBody body = response.body()) {
                if (body != null) responseBody = body.string();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (null == response) {
            throw new AlphaESSException(what + ": no response from AlphaESS");
        }
        if (!response.isSuccessful()) {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.code = response.code();
            errorResponse.msg = response.message();
            throwAppropriateException(errorResponse);
        }
        BindSnResponse ret = null;
        try {
            ret = new Gson().fromJson(responseBody, BindSnResponse.class);
        } catch (RuntimeException rte) {
            Log.w(TAG, "Expecting BindSnResponse, but not one :-(");
        }
        if (null == ret) {
            throw new AlphaESSException(what + ": unparseable response from AlphaESS");
        }
        return ret;
    }

    private void throwAppropriateException(ErrorResponse err) throws AlphaESSException {
        // 200  -- HTTP-style success envelope but data=null. Means the
        //         remote hasn't aggregated yesterday's data yet; the
        //         DailyWorker schedules a single 1-hour delayed retry.
        // 6001 -- Parameter error
        // 6002 -- The SN is not bound to the user
        // 6003 -- You have bound this SN
        // 6004 -- CheckCode error
        // 6005 -- This appId is not bound to the SN
        // 6006 -- Timestamp error
        // 6007 -- Sign verification error ==> bad secret or bad app id
        // 6008 -- Set failed
        // 6009 -- Whitelist verification failed
        // 6010 -- Sign is empty ==>
        // 6012 -- AppId is empty ==> bad app id
        // 6053 -- The request was too fast, please try again later
        // 7001 -- The network was not available
        String message = "err.code=" + err.code + " err.msg=" + err.msg + " Serial: " +
                mSystemSerialNumber;
        if (err.code == 200) {
            throw new AlphaESSNoDataYetException(message);
        }
        throw new AlphaESSException(message);
    }

    private Map<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();

        String appId = mApplicationID;
        String timeStamp = Long.toString(Instant.now().getEpochSecond());
        String sign = getSHA512(appId, mApplicationSecret, timeStamp);
        String appIdHeader = "appId";
        String timeStampHeader = "timeStamp";
        String signHeader = "sign";
        String connectionHeader = "Connection";
        String connectionType = "keep-alive";

        headers.put(appIdHeader, appId);
        headers.put(timeStampHeader, timeStamp);
        headers.put(signHeader, sign);
        headers.put(connectionHeader, connectionType);

        return headers;
    }

    @NonNull
    private String getSHA512(String appId, String appSecret, String timeStamp) {
        //        implementation 'commons-codec:commons-codec:1.16.0'
        //        String sign = DigestUtils.sha512Hex(appId + appSecret + timeStamp);

        String sign = "";
        String dataToHash = appId + appSecret + timeStamp;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hashBytes = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));

            // Convert the byte array to a hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            sign =  hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Handle the exception
            e.printStackTrace();
        }
        return sign;
    }
}
