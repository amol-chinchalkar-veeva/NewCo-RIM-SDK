package com.veeva.vault.custom.action;


import com.veeva.vault.custom.util.VpsSequenceGenerator;
import com.veeva.vault.custom.util.VpsVQLHelper;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryResponse;

import java.math.BigDecimal;
import java.util.List;

@DocumentActionInfo(label = "Set DocID")

public class VpsRCAMDocIDGenerator implements DocumentAction {

    private static final String DOCFIELD_MAJOR_VERSION_NUMBER = "major_version_number__v";
    private static final String DOCFIELD_MINOR_VERSION_NUMBER = "minor_version_number__v";
    private static final String OBJFIELD_ID = "id";
    final static int DOCID_FORMAT_LEGTH = 6;
    final static int DOCID_MAX_VALUE = 728999999;
    final static String DOCID_FORMAT_PADDING = "0";
    private static final String OBJFIELD_NAME = "name__v";
    private static final String AUTONUMBER_OBJ_NAME = "newco_doc_id__c";
    private static final String DOCFIELD_DOCUMENT_ID = "newco_document_id__c"; //newco_document_id__c
    private static final String DOCFIELD_EXPORT_FILENAME = "export_filename__v";

    public VpsRCAMDocIDGenerator() {

    }

    @Override
    public boolean isExecutable(DocumentActionContext documentActionContext) {
        return true;
    }

    @Override
    public void execute(DocumentActionContext documentActionContext) {
        LogService logger = ServiceLocator.locate(LogService.class);
        VpsSequenceGenerator seqGenerator = new VpsSequenceGenerator();

        for (DocumentVersion documentVersion : documentActionContext.getDocumentVersions()) {
            DocumentService docService = ServiceLocator.locate(DocumentService.class);

            List<DocumentVersion> docVersionList = VaultCollections.newList();
            String docId = documentVersion.getValue(OBJFIELD_ID, ValueType.STRING);
            BigDecimal majorVersionNumber = documentVersion.getValue(DOCFIELD_MAJOR_VERSION_NUMBER, ValueType.NUMBER);
            BigDecimal minorVersionNumber = documentVersion.getValue(DOCFIELD_MINOR_VERSION_NUMBER, ValueType.NUMBER);
            logger.info("Doc Id {}, update with base30 Document ID", docId);

            RecordService recordService = ServiceLocator.locate(RecordService.class);
            // create & get ID of a auto number object
            String autoNumberRecordID = createNewAutoNumber(AUTONUMBER_OBJ_NAME);
            logger.info("Created autonmber object with id {}", autoNumberRecordID);
            if (!autoNumberRecordID.equals("")) {
                // get name__v of a auto number object
                String strUniqueDocID = getName(autoNumberRecordID);
                // convert ######### (9 digit String) to int value
                int intUniqueDocID = Integer.parseInt(strUniqueDocID);

                if (intUniqueDocID <= DOCID_MAX_VALUE) {
                    String base30DocID = seqGenerator.getBase30Number(intUniqueDocID,
                            DOCID_FORMAT_LEGTH,
                            DOCID_FORMAT_PADDING);
                    logger.info("Generated base30 {} id for {}", base30DocID, intUniqueDocID);
                    documentVersion.setValue(DOCFIELD_DOCUMENT_ID,
                            base30DocID);
                    documentVersion.setValue(DOCFIELD_EXPORT_FILENAME,
                            base30DocID);
                    docVersionList.add(documentVersion);
                    docService.saveDocumentVersions(docVersionList);
                } else {
                    String errMsg = "The Document ID is exceeding max limit " + DOCID_MAX_VALUE;
                    throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to create Document ID: {}" + errMsg);
                }
            }
        }//version

    }

    /**
     * Save Records
     *
     * @param listRecord
     */
    private String saveRecords(List<Record> listRecord) {
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        LogService logger = ServiceLocator.locate(LogService.class);

        final String[] userId = {""};
        recordService.batchSaveRecords(listRecord)
                .onSuccesses(batchOperationSuccess -> {
                    batchOperationSuccess.stream().forEach(success -> {
                        userId[0] = success.getRecordId();
                        logger.debug("Successfully created/updated record with id: " + userId[0] + " for object");
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
        return userId[0];
    }

    /**
     * Get name from object
     * @param id
     * @return
     */
    public static String getName(String id) {
        final String[] name = {""};
        VpsVQLHelper vqlHelper = new VpsVQLHelper();
        vqlHelper.appendVQL("SELECT " + OBJFIELD_NAME);
        vqlHelper.appendVQL(" FROM " + AUTONUMBER_OBJ_NAME);
        vqlHelper.appendVQL(" WHERE " + OBJFIELD_ID + "= '" + id + "'");
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
    private String createNewAutoNumber(String autoNumberObjectName) {
        // create an instance of the Record
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        List<Record> recordList = VaultCollections.newList();
        //new record is created with the name__v set as "system manages field value"
        Record r = recordService.newRecord(autoNumberObjectName);
        recordList.add(r);
        String recordID = saveRecords(recordList);
        return recordID;
    }

}
