/*
 * --------------------------------------------------------------------
 * MessageProcessor:	VpsDocIDGenerator
 * Author:				achinchalkar @ Veeva
 * Date:				2020-05-24
 *---------------------------------------------------------------------
 * Description:
 *---------------------------------------------------------------------
 * Copyright (c) 2020 Veeva Systems Inc.  All Rights Reserved.
 *		This code is based on pre-existing content developed and
 *		owned by Veeva Systems Inc. and may only be used in connection
 *		with the deliverable with which it was provided to Customer.
 *---------------------------------------------------------------------
 */
package com.veeva.vault.custom.action;


import com.veeva.vault.custom.util.VpsSequenceGenerator;
import com.veeva.vault.custom.util.VpsVQLHelper;
import com.veeva.vault.custom.util.api.VpsAPIClient;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.queue.Message;
import com.veeva.vault.sdk.api.queue.PutMessageResponse;
import com.veeva.vault.sdk.api.queue.QueueService;

import java.util.List;


@DocumentActionInfo(label = "Set Document ID")

public class VpsDocIDGenerator implements DocumentAction {


    private static final String DOCFIELD_ID = "id";

    //doc number config
    final static int DOCID_FORMAT_LEGTH = 6;
    final static int DOCID_MAX_VALUE = 728999999;
    final static String DOCID_FORMAT_PADDING = "0";

    private static final String OBJFIELD_NAME = "name__v";
    private static final String AUTONUMBER_OBJ_NAME = "docid_autonumber__c";
    private static final String DOCFIELD_BASE30_DOCUMENT_ID = "document_id__c"; //newco_document_id__c
    private static final String API_CONNECTION = "local_http_callout_connection";
    private static final String QUEUE_NAME = "doc_id_queue__c";


    public VpsDocIDGenerator() {

    }

    @Override
    public boolean isExecutable(DocumentActionContext documentActionContext) {
        return true;
    }


    @Override
    public void execute(DocumentActionContext documentActionContext) {

        VpsSequenceGenerator seqGenerator = new VpsSequenceGenerator();
        LogService logger = ServiceLocator.locate(LogService.class);
        VpsAPIClient apiClient = new VpsAPIClient(API_CONNECTION);

        for (DocumentVersion documentVersion : documentActionContext.getDocumentVersions()) {

            String docId = documentVersion.getValue(DOCFIELD_ID, ValueType.STRING);
            String existingdocId = getNotNullValue(documentVersion.getValue(DOCFIELD_BASE30_DOCUMENT_ID, ValueType.STRING));
            logger.info("Document versions to be updated for id {}", docId);

            if (existingdocId.equals("")) {
                // create & get ID of a auto number object
                String autoNumberRecordID = createNewObject(AUTONUMBER_OBJ_NAME);
                logger.info("Created auto number object with id {}", autoNumberRecordID);

                if (!autoNumberRecordID.equals("")) {
                    // get name__v of a auto number object
                    String strUniqueDocID = getName(autoNumberRecordID, AUTONUMBER_OBJ_NAME);
                    // convert ######### (9 digit String) to int value
                    int intUniqueDocID = Integer.parseInt(strUniqueDocID);

                    if (intUniqueDocID <= DOCID_MAX_VALUE) {
                        String base30DocID = seqGenerator.getBase30Number(intUniqueDocID,
                                DOCID_FORMAT_LEGTH,
                                DOCID_FORMAT_PADDING);
                        logger.info("Generated base30 {} id for the decimal {}", base30DocID, intUniqueDocID);

                        if (!base30DocID.equals("")) {
                            //create a local queue
                            queueLocalMessage(docId, base30DocID);
                            logger.info("Document message queued for id {}", docId);
                        }
                    } else {
                        String errMsg = "The Document ID is exceeding max limit " + DOCID_MAX_VALUE;
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to update Document ID: {}" + errMsg);
                    }
                }
            } else {
                logger.info("Document versions already has Document id {}", existingdocId);
            }
        }

    }

    /**
     * Save Records
     *
     * @param listRecord
     */
    private String saveRecords(List<Record> listRecord) {
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        LogService logger = ServiceLocator.locate(LogService.class);

        final String[] recordId = {""};
        recordService.batchSaveRecords(listRecord)
                .onSuccesses(batchOperationSuccess -> {
                    batchOperationSuccess.stream().forEach(success -> {
                        recordId[0] = success.getRecordId();
                        logger.debug("Successfully created/updated record with id: " + recordId[0] + " for object");
                    });
                })
                .onErrors(batchOperationErrors -> {
                    batchOperationErrors.stream().findFirst().ifPresent(error -> {
                        String errMsg = error.getError().getMessage();
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to save record: " + errMsg);
                    });
                })
                .execute();
        logger.debug("Completed");
        return recordId[0];
    }

    /**
     * Get name from object
     *
     * @param id
     * @return
     */
    public static String getName(String id, String objectName) {
        final String[] name = {""};
        VpsVQLHelper vqlHelper = new VpsVQLHelper();
        vqlHelper.appendVQL("SELECT " + OBJFIELD_NAME);
        vqlHelper.appendVQL(" FROM " + objectName);
        vqlHelper.appendVQL(" WHERE " + DOCFIELD_ID + "= '" + id + "'");
        QueryResponse queryResponse = vqlHelper.runVQL();
        queryResponse.streamResults().forEach(queryResult -> {
            name[0] = queryResult.getValue(OBJFIELD_NAME, ValueType.STRING);
        });
        return name[0];
    }

    /**
     * Get ID of auto number record
     *
     * @param autoNumberObjectName
     * @return
     */
    private String createNewObject(String autoNumberObjectName) {
        // create an instance of the Record
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        List<Record> recordList = VaultCollections.newList();
        //new record is created with the name__v set as "system managed field value"
        Record r = recordService.newRecord(autoNumberObjectName);
        recordList.add(r);
        return saveRecords(recordList);

    }

    public String getNotNullValue(String value) {
        if (value == null) {
            value = "";
        }
        return value;
    }

    /**
     * updatePreviousVersions : Queue each version
     *
     * @param docId
     * @param base30DocumentId
     */
    public void queueLocalMessage(String docId, String base30DocumentId) {
        LogService logger = ServiceLocator.locate(LogService.class);

        QueueService queueService = ServiceLocator.locate(QueueService.class);
        Message message = queueService.newMessage(QUEUE_NAME)
                .setAttribute("docId", docId)
                .setAttribute("base30DocumentId", base30DocumentId)
                .setAttribute("apiConnection", API_CONNECTION);
        //Put the new message into the Spark queue.
        //The Response can be used to review if queuing was successful or not
        PutMessageResponse response = queueService.putMessage(message);
        logger.info("Put 'document' Message in Queue - for:{}", docId);

        //Check that the message queue successfully processed the message.
        if (response.getError() != null) {
            logger.info("ERROR Queuing Failed: " + response.getError().getMessage());
        } else {
            response.getPutMessageResults().stream().forEach(result -> {
                logger.info("SUCCESS: " + result.getMessageId() + " " + result.getConnectionName());
            });
        }
    }
}//EOF
