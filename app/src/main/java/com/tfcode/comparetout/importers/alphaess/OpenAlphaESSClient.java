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

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.tfcode.comparetout.importers.alphaess.responses.ErrorResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenAlphaESSClient {

    private static final String BASE_URL = "https://openapi.alphaess.com/";
    private String mSystemSerialNumber = "";
    private final String mApplicationID;
    private final String mApplicationSecret;

    private final OpenAlphaESSService mApiService;

    public OpenAlphaESSClient(String applicationID, String applicationSecret) {
        mApplicationID = applicationID;
        mApplicationSecret = applicationSecret;
        Retrofit mRetrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
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
            assert response.body() != null;
            responseBody = response.body().string();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!(null == response) && response.isSuccessful()) {
            Gson gson = new Gson();
            try {
                ret = gson.fromJson(responseBody, GetEssListResponse.class);
            }
            catch (IllegalStateException ise) {
                System.out.println("Expecting GetEssListResponse, but not one :-(");
            }
            if ((null == ret) || (null == ret.data)) {
                ErrorResponse err = gson.fromJson(responseBody, ErrorResponse.class);
                System.out.println(err.code);
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
            assert response.body() != null;
            responseBody = response.body().string();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!(null == response) && response.isSuccessful()) {
            Gson gson = new Gson();
            try {
                ret = gson.fromJson(responseBody, GetOneDayPowerResponse.class);
            }
            catch (IllegalStateException ise) {
                System.out.println("Expecting GetOneDayPowerResponse, but not one :-(");
            }
            if ((null == ret) || (null == ret.data)) {
                ErrorResponse err = gson.fromJson(responseBody, ErrorResponse.class);
                System.out.println(err.code);
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
            assert response.body() != null;
            responseBody = response.body().string();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!(null == response) && response.isSuccessful()) {
            Gson gson = new Gson();
            try {
                ret = gson.fromJson(responseBody, GetOneDayEnergyResponse.class);
            }
            catch (IllegalStateException ise) {
                System.out.println("Expecting GetOneDayEnergyResponse, but not one :-(");
            }
            if ((null == ret) || (null == ret.data)) {
                ErrorResponse err = gson.fromJson(responseBody, ErrorResponse.class);
                System.out.println(err.code);
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

    private void throwAppropriateException(ErrorResponse err) throws AlphaESSException {
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
        throw new AlphaESSException("err.code=" + err.code + " err.msg=" + err.msg + " Headers: " +
                mSystemSerialNumber + ", " + mApplicationID + ", " + mApplicationSecret);
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
//        headers.put("Accept", "\"*/*\"");
//        headers.put("Host", "\"openapi.alphaess.com\"");
//        headers.put("Accept-Encoding","\"gzip, deflate, br\"");
//        headers.put("timestamp", timeStamp);
//        headers.put("Content-Type", "application/json");

//        for (Map.Entry<String, String> entry: headers.entrySet()) System.out.println(entry);
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
