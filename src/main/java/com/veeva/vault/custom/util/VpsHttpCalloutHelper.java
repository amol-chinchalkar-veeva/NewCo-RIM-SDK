/*
 * --------------------------------------------------------------------
 * UDC:			VpsVpsHttpCalloutHelper
 * Author:		paulkwitkin @ Veeva
 * Date:		2019-07-29
 *---------------------------------------------------------------------
 * Description:
 *---------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 *		This code is based on pre-existing content developed and
 * 		owned by Veeva Systems Inc. and may only be used in connection
 *		with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.util;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonData;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.util.List;
import java.util.Map;

@UserDefinedClassInfo()
public class VpsHttpCalloutHelper {

    public VpsHttpCalloutHelper() {
        super();
    }


    public void createBinderLocalHttpCallout(Map<String, String> params) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);


        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest("local_http_callout_connection");

        request.setMethod(HttpMethod.POST);
        request.appendPath("/api/v19.1/objects/binders");
        request.setQuerystringParam("async", "true");

        for (String key : params.keySet()) {
            request.setBodyParam(key, params.get(key));
        }

        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
                .onSuccess(httpResponse -> {
                    int responseCode = httpResponse.getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info("RESPONSE: " + httpResponse.getResponseBody());

                    JsonData response = httpResponse.getResponseBody();

                    //This API call just initiates a workflow. Log success or errors messages depending on the results of the call.
                    if (response.isValidJson()) {
                        String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

                        if (responseStatus.equals("SUCCESS")) {
                            logService.info("Starting HTTP Create Binder");
                        } else {
                            logService.info("Failed to create binder.");
                            if (response.getJsonObject().contains("responseMessage") == true) {
                                String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
                                logService.error("ERROR: {}", responseMessage);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Create Binder: " + responseMessage);
                            }
                            if (response.getJsonObject().contains("errors") == true) {
                                JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
                                String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
                                String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
                                logService.error("ERROR {}: {}", type, message);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Create Binder: " + message);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {
                    int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info(httpOperationError.getMessage());
                    logService.info(httpOperationError.getHttpResponse().getResponseBody());
                })
                .execute();
    }

//    /**
//     * @param docID
//     * @param majorVersion
//     * @param minorVersion
//     */
//    public boolean startWorkflowLocalHttpCallout(String docID, String majorVersion,
//                                                 String minorVersion, Map<String, String> Requestparams) {
//
//        LogService logService = ServiceLocator.locate(LogService.class);
//        HttpService httpService = ServiceLocator.locate(HttpService.class);
//        List<Boolean> successList = VaultCollections.newList();
//        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
//        //The user must have access to the action being performed or the Vault API will return an access error.
//        HttpRequest request = httpService.newHttpRequest("local_http_callout_connection");
//
//        request.setMethod(HttpMethod.PUT);
//        request.appendPath("/api/v19.1/objects/documents/" + docID + "/versions/" + majorVersion + "/" + minorVersion + "/lifecycle_actions/msddmnv5uz4ux6tc");
//        request.setQuerystringParam("async", "true");
//
//
//        for (String key : Requestparams.keySet()) {
//            request.setBodyParam(key, Requestparams.get(key));
//        }
//
//        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
//                .onSuccess(httpResponse -> {
//                    int responseCode = httpResponse.getHttpStatusCode();
//                    logService.info("RESPONSE: " + responseCode);
//                    logService.info("RESPONSE: " + httpResponse.getResponseBody());
//
//                    JsonData response = httpResponse.getResponseBody();
//                    successList.add(true);
//                    //This API call just initiates a workflow. Log success or errors messages depending on the results of the call.
//                    if (response.isValidJson()) {
//                        String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);
//
//                        if (responseStatus.equals("SUCCESS")) {
//                            logService.info("Starting HTTP Start Workflow");
//                        } else {
//                            logService.info("Failed to start workflow.");
//                            if (response.getJsonObject().contains("responseMessage") == true) {
//                                String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
//                                logService.error("ERROR: {}", responseMessage);
//                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call Out: " + responseMessage);
//                            }
//                            if (response.getJsonObject().contains("errors") == true) {
//                                JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
//                                String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
//                                String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
//                                logService.error("ERROR {}: {}", type, message);
//                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call out: " + message);
//                            }
//                        }
//                    }
//                })
//                .onError(httpOperationError -> {
//                    int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
//                    logService.info("RESPONSE: " + responseCode);
//                    logService.info(httpOperationError.getMessage());
//                    logService.info(httpOperationError.getHttpResponse().getResponseBody());
//                })
//                .execute();
//        return successList.size() > 0;
//    }


    /**
     * @param docID
     * @param majorVersion
     * @param minorVersion
     */
    public boolean changeLifecycleState(String docID, String majorVersion,
                                        String minorVersion, String LifecycleUserAction) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        List<Boolean> successList = VaultCollections.newList();
        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest("local_http_callout_connection");

        request.setMethod(HttpMethod.PUT);
        request.appendPath("/api/v19.1/objects/documents/" + docID + "/versions/" + majorVersion + "/" + minorVersion + "/lifecycle_actions/" + LifecycleUserAction);
        //request.setQuerystringParam("async", "true");

//        for (String key : params.keySet()) {
//            request.setBodyParam(key,params.get(key));
//        }

        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
                .onSuccess(httpResponse -> {
                    int responseCode = httpResponse.getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info("RESPONSE: " + httpResponse.getResponseBody());
                    successList.add(true);
                    JsonData response = httpResponse.getResponseBody();

                    //This API call just initiates a action. Log success or errors messages depending on the results of the call.
                    if (response.isValidJson()) {
                        String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

                        if (responseStatus.equals("SUCCESS")) {
                            logService.info("Starting HTTP Change Lifecycle State");
                        } else {
                            logService.info("Failed to Change Lifecycle State.");
                            if (response.getJsonObject().contains("responseMessage") == true) {
                                String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
                                logService.error("ERROR: {}", responseMessage);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call Out: " + responseMessage);
                            }
                            if (response.getJsonObject().contains("errors") == true) {
                                JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
                                String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
                                String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
                                logService.error("ERROR {}: {}", type, message);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call out: " + message);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {
                    int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info(httpOperationError.getMessage());
                    logService.info(httpOperationError.getHttpResponse().getResponseBody());
                })
                .execute();
        return successList.size() > 0;
    }

    /**
     * @param docID
     * @param majorVersion
     * @param minorVersion
     * @param documentFieldsToUpdate
     */
    public boolean updateDocumentFields(String docID, String majorVersion,
                                        String minorVersion, Map<String, String> documentFieldsToUpdate) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        List<Boolean> successList = VaultCollections.newList();
        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest("local_http_callout_connection");

        request.setMethod(HttpMethod.PUT);
        request.appendPath("/api/v19.1/objects/documents/" + docID + "/versions/" + majorVersion + "/" + minorVersion);
        //request.setQuerystringParam("async", "true");

        for (String key : documentFieldsToUpdate.keySet()) {
            request.setBodyParam(key, documentFieldsToUpdate.get(key));
        }

        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
                .onSuccess(httpResponse -> {
                    int responseCode = httpResponse.getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info("RESPONSE: " + httpResponse.getResponseBody());

                    JsonData response = httpResponse.getResponseBody();
                    successList.add(true);
                    //This API call just initiates a action. Log success or errors messages depending on the results of the call.
                    if (response.isValidJson()) {
                        String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

                        if (responseStatus.equals("SUCCESS")) {
                            logService.info("Starting HTTP update document fields");
                        } else {
                            logService.info("Failed to update document fields.");
                            if (response.getJsonObject().contains("responseMessage") == true) {
                                String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
                                logService.error("ERROR: {}", responseMessage);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call Out: " + responseMessage);
                            }
                            if (response.getJsonObject().contains("errors") == true) {
                                JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
                                String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
                                String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
                                logService.error("ERROR {}: {}", type, message);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call out: " + message);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {
                    int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info(httpOperationError.getMessage());
                    logService.info(httpOperationError.getHttpResponse().getResponseBody());
                })
                .execute();
        return successList.size() > 0;
    }

    /**
     * @param objectType
     * @param fieldsToUpdate
     */
    public String createObject(String objectType, Map<String, String> fieldsToUpdate) {

        LogService logService = ServiceLocator.locate(LogService.class);
        HttpService httpService = ServiceLocator.locate(HttpService.class);
        final String[] usertaskId = {""};

        //A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
        //The user must have access to the action being performed or the Vault API will return an access error.
        HttpRequest request = httpService.newHttpRequest("local_http_callout_connection");

        request.setMethod(HttpMethod.POST);
        //request.appendPath("/api/v19.1/objects/documents/" + docID + "/versions/" + majorVersion + "/" + minorVersion + "/lifecycle_actions/msddmnv5uz4ux6tc");

        request.appendPath("/api/v19.1/vobjects/" + objectType);
        request.setQuerystringParam("async", "true");
        Map<String, String> params = VaultCollections.newMap();
//        params.put("studySites","0SI000000000301,0SI000000000302,0SI000000000303");
//        params.put("applicationRoles", "0AR000000000C01,0TR000000000B0
//        2");
//        params.put("dueDate", "2019-11-22");

        if (fieldsToUpdate != null) {
            for (String key : fieldsToUpdate.keySet()) {
                request.setBodyParam(key, fieldsToUpdate.get(key));
            }
        }

        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
                .onSuccess(httpResponse -> {
                    int responseCode = httpResponse.getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info("RESPONSE: " + httpResponse.getResponseBody());

                    JsonData response = httpResponse.getResponseBody();

                    //This API call just initiates a workflow. Log success or errors messages depending on the results of the call.
                    if (response.isValidJson()) {
                        String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);
                        JsonObject data = response.getJsonObject().getValue("data", JsonValueType.OBJECT);
                        usertaskId[0] = (String) data.getValue("id", JsonValueType.STRING);
                        String str2 = (String) data.getValue("url", JsonValueType.STRING);

                        if (responseStatus.equals("SUCCESS")) {
                            logService.info("Starting HTTP Create Object");
                        } else {
                            logService.info("Failed to create object.");
                            if (response.getJsonObject().contains("responseMessage") == true) {
                                String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
                                logService.error("ERROR: {}", responseMessage);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call Out: " + responseMessage);
                            }
                            if (response.getJsonObject().contains("errors") == true) {
                                JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
                                String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
                                String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
                                logService.error("ERROR {}: {}", type, message);
                                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Call out: " + message);
                            }
                        }
                    }
                })
                .onError(httpOperationError -> {
                    int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                    logService.info("RESPONSE: " + responseCode);
                    logService.info(httpOperationError.getMessage());
                    logService.info(httpOperationError.getHttpResponse().getResponseBody());
                })
                .execute();
        return usertaskId[0];
    }

}
