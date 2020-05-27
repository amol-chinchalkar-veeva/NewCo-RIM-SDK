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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@DocumentActionInfo(label = "Set Document ID")

public class VpsDocIDGenerator implements DocumentAction {

    private static final String DOCFIELD_MAJOR_VERSION_NUMBER = "major_version_number__v";
    private static final String DOCFIELD_MINOR_VERSION_NUMBER = "minor_version_number__v";
    private static final String DOCFIELD_ID = "id";
    private static final String DOCFIELD_STATUS = "status__v";
    //doc number config
    final static int DOCID_FORMAT_LEGTH = 6;
    final static int DOCID_MAX_VALUE = 728999999;
    final static String DOCID_FORMAT_PADDING = "0";

    private static final String OBJFIELD_NAME = "name__v";
    private static final String AUTONUMBER_OBJ_NAME = "newco_doc_id__c";
    private static final String DOCFIELD_NEWCO_DOCUMENT_ID = "newco_document_id__c"; //newco_document_id__c
    private static final String DOCFIELD_EXPORT_FILENAME = "export_filename__v";
    public static final String VERSION_ID_FIELD = "version_id";
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

        for (DocumentVersion documentVersion : documentActionContext.getDocumentVersions()) {
            DocumentService docService = ServiceLocator.locate(DocumentService.class);
            List<DocumentVersion> docVersionList = VaultCollections.newList();

            String docId = documentVersion.getValue(DOCFIELD_ID, ValueType.STRING);
            // String docStatus = documentVersion.getValue(DOCFIELD_STATUS, ValueType.REFERENCES);
            String docNewCoId = getNotNullValue(documentVersion.getValue(DOCFIELD_NEWCO_DOCUMENT_ID, ValueType.STRING));

            logger.info("Document versions to be updated for id {}", docId);

            if (docNewCoId.equals("")) {
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
                            //update all versions for id
                            updateAllVersions(docId, base30DocID, API_CONNECTION);
                            logger.info("Document versions updated for id {}", docId);
                        }
                    } else {
                        String errMsg = "The Document ID is exceeding max limit " + DOCID_MAX_VALUE;
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to update Document ID: {}" + errMsg);
                    }
                }
            } else {
                //handle steady state versions
                logger.info("Document versions already has Document id {}", docNewCoId);
            }
        }

    }

    /**
     * updatePreviousVersions : Only updates the latest version
     *
     * @param id
     * @param base30Id
     */
    private void updateLatestVersions(String id, String base30Id) {
        DocumentService ds = ServiceLocator.locate(DocumentService.class);
        List<DocumentVersion> docUpdates = VaultCollections.newList();

        VpsVQLHelper vqlHelper = new VpsVQLHelper();
        vqlHelper.appendVQL("SELECT " + "version_id," + DOCFIELD_NEWCO_DOCUMENT_ID);
        vqlHelper.appendVQL(" FROM " + "allversions documents");
        vqlHelper.appendVQL(" WHERE " + DOCFIELD_ID + "=" + id);
        QueryResponse versionResponse = vqlHelper.runVQL();
        versionResponse.streamResults().forEach(versionResult -> {
            String docId = getNotNullValue(versionResult.getValue(DOCFIELD_NEWCO_DOCUMENT_ID, ValueType.STRING));
            DocumentVersion docUpdate = ds.newDocumentWithId(id);
            if (docId != null || docId.equals("")) {
                docUpdate.setValue(DOCFIELD_NEWCO_DOCUMENT_ID, base30Id);
                docUpdate.setValue(DOCFIELD_EXPORT_FILENAME,
                        base30Id);
                docUpdates.add(docUpdate);
            }
        });
        if (docUpdates.size() > 0) {
            ds.saveDocumentVersions(docUpdates);
        }
    }

    /**
     * updatePreviousVersions : Queue each version
     *
     * @param docId
     * @param base30DocumentId
     * @param apiConnection
     */
    public void updateAllVersions(String docId, String base30DocumentId, String apiConnection) {
        LogService logger = ServiceLocator.locate(LogService.class);
        VpsVQLHelper vqlHelper = new VpsVQLHelper();
        QueueService queueService = ServiceLocator.locate(QueueService.class);

        vqlHelper.appendVQL("SELECT " + DOCFIELD_MAJOR_VERSION_NUMBER + "," +
                DOCFIELD_MINOR_VERSION_NUMBER + "," + DOCFIELD_NEWCO_DOCUMENT_ID);
        vqlHelper.appendVQL(" FROM " + "allversions documents");
        vqlHelper.appendVQL(" WHERE " + DOCFIELD_ID + "=" + docId);
        QueryResponse versionResponse = vqlHelper.runVQL();
        //update previous versions one by one
        versionResponse.streamResults().forEach(versionResult -> {
            BigDecimal majorVersionNumber = versionResult.getValue(DOCFIELD_MAJOR_VERSION_NUMBER, ValueType.NUMBER);
            BigDecimal minorVersionNumber = versionResult.getValue(DOCFIELD_MINOR_VERSION_NUMBER, ValueType.NUMBER);
            String existingDocId = getNotNullValue(versionResult.getValue(DOCFIELD_NEWCO_DOCUMENT_ID, ValueType.STRING));
            boolean updateSuccess = false;

            if (existingDocId.equals("")) {
                Message message = queueService.newMessage(QUEUE_NAME)
                        .setAttribute("docId", docId)
                        .setAttribute("base30DocumentId", base30DocumentId)
                        .setAttribute("apiConnection", apiConnection)
                        .setAttribute("majorVersionNumber", majorVersionNumber.toString())
                        .setAttribute("minorVersionNumber", minorVersionNumber.toString());

                //Put the new message into the Spark outbound queue.
                //The PutMessageResponse can be used to review if queuing was successful or not
                PutMessageResponse response = queueService.putMessage(message);
                logger.info("Put 'document' Message in Queue - state = 'docid");

                //Check that the message queue successfully processed the message.
                if (response.getError() != null) {
                    logger.info("ERROR Queuing Failed: " + response.getError().getMessage());
                } else {
                    response.getPutMessageResults().stream().forEach(result -> {
                        logger.info("SUCCESS: " + result.getMessageId() + " " + result.getConnectionName());
                    });
                }
            }
        });
    }


    /**
     * Save Records
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
        String recordID = saveRecords(recordList);
        return recordID;
    }

    public String getNotNullValue(String value) {
        if (value == null) {
            value = "";
        }
        return value;
    }

}
